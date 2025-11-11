package com.example.testmessagesimple.data

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Service de messagerie en temps réel avec livraison directe.
 *
 * STRATÉGIE :
 * 1. Si le destinataire est en ligne → envoi direct via Socket.IO (pas de BDD serveur)
 * 2. Si le destinataire est offline → stockage temporaire dans BDD serveur (24h max)
 * 3. Tous les messages sont stockés localement sur le téléphone
 * 4. À la connexion, récupération des messages offline depuis le serveur
 */
class DirectMessagingService(
    private val baseUrl: String = "http://10.0.2.2:3000"
) {
    private var socket: Socket? = null
    private var authToken: String? = null
    private var currentUserId: Int? = null

    // État de connexion
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Messages reçus en temps réel
    private val _receivedMessages = MutableStateFlow<MessageReceived?>(null)
    val receivedMessages: StateFlow<MessageReceived?> = _receivedMessages

    // Statut de livraison
    private val _deliveryStatus = MutableStateFlow<DeliveryStatus?>(null)
    val deliveryStatus: StateFlow<DeliveryStatus?> = _deliveryStatus

    // Demandes d'amis reçues
    private val _friendRequestReceived = MutableStateFlow<FriendRequestNotification?>(null)
    val friendRequestReceived: StateFlow<FriendRequestNotification?> = _friendRequestReceived

    // Amitié supprimée
    private val _friendshipDeleted = MutableStateFlow<Int?>(null)
    val friendshipDeleted: StateFlow<Int?> = _friendshipDeleted

    companion object {
        private const val TAG = "DirectMessaging"
    }

    /**
     * Connexion au serveur Socket.IO et authentification
     */
    fun connect(token: String, userId: Int) {
        try {
            authToken = token
            currentUserId = userId

            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000
            }

            socket = IO.socket(baseUrl, options)

            socket?.apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "✅ Socket connected")
                    authenticate(token)
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "❌ Socket disconnected")
                    _isConnected.value = false
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "❌ Connection error: ${args.joinToString()}")
                    _isConnected.value = false
                }

                on("authenticated") { args ->
                    Log.d(TAG, "✅ Authenticated")
                    _isConnected.value = true
                }

                on("error") { args ->
                    Log.e(TAG, "❌ Server error: ${args.joinToString()}")
                }

                // MESSAGE REÇU (livraison directe ou depuis serveur)
                on("message") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val message = MessageReceived(
                            id = data.optInt("id", 0),
                            senderId = data.getInt("senderId"),
                            senderEmail = data.getString("senderEmail"),
                            content = data.getString("content"),
                            timestamp = data.getLong("timestamp"),
                            fromServer = data.optBoolean("fromServer", false)
                        )
                        Log.d(TAG, "📨 Message reçu de ${message.senderEmail}: ${message.content}")
                        _receivedMessages.value = message
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing message", e)
                    }
                }

                // CONFIRMATION DE LIVRAISON DIRECTE
                on("message_delivered") { args ->
                    try {
                        val data = args[0] as JSONObject
                        _deliveryStatus.value = DeliveryStatus(
                            tempId = data.optString("tempId"),
                            receiverId = data.getInt("receiverId"),
                            timestamp = data.getLong("timestamp"),
                            direct = true,
                            stored = false
                        )
                        Log.d(TAG, "✅ Message livré directement")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing delivery status", e)
                    }
                }

                // MESSAGE STOCKÉ (destinataire offline)
                on("message_stored") { args ->
                    try {
                        val data = args[0] as JSONObject
                        _deliveryStatus.value = DeliveryStatus(
                            tempId = data.optString("tempId"),
                            receiverId = data.getInt("receiverId"),
                            timestamp = data.getLong("timestamp"),
                            direct = false,
                            stored = true,
                            serverMessageId = data.optInt("messageId")
                        )
                        Log.d(TAG, "💾 Message stocké sur serveur (destinataire offline)")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing storage status", e)
                    }
                }

                // DEMANDE D'AMI REÇUE
                on("friend_request_received") { args ->
                    try {
                        val data = args[0] as JSONObject
                        _friendRequestReceived.value = FriendRequestNotification(
                            id = data.getInt("id"),
                            senderId = data.getInt("senderId"),
                            senderEmail = data.getString("senderEmail")
                        )
                        Log.d(TAG, "👥 Demande d'ami reçue")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing friend request", e)
                    }
                }

                // AMITIÉ SUPPRIMÉE
                on("friendship_deleted") { args ->
                    try {
                        val data = args[0] as JSONObject
                        val friendshipId = data.getInt("friendshipId")
                        _friendshipDeleted.value = friendshipId
                        Log.d(TAG, "💔 Amitié supprimée: $friendshipId")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing friendship deletion", e)
                    }
                }

                connect()
            }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ Invalid server URL", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Connection error", e)
        }
    }

    /**
     * Authentification auprès du serveur
     */
    private fun authenticate(token: String) {
        val authData = JSONObject().apply {
            put("token", token)
        }
        socket?.emit("authenticate", authData)
    }

    /**
     * Envoyer un message (livraison directe si destinataire en ligne)
     */
    fun sendMessage(receiverId: Int, content: String, tempId: String? = null) {
        if (!_isConnected.value) {
            Log.e(TAG, "❌ Cannot send message: not connected")
            return
        }

        val messageData = JSONObject().apply {
            put("receiverId", receiverId)
            put("content", content)
            if (tempId != null) {
                put("tempId", tempId)
            }
        }

        socket?.emit("send_message", messageData)
        Log.d(TAG, "📤 Message envoyé à $receiverId")
    }

    /**
     * Déconnexion
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _isConnected.value = false
        Log.d(TAG, "👋 Déconnecté")
    }

    /**
     * Réinitialiser les états
     */
    fun clearMessageState() {
        _receivedMessages.value = null
    }

    fun clearDeliveryStatus() {
        _deliveryStatus.value = null
    }

    fun clearFriendRequestNotification() {
        _friendRequestReceived.value = null
    }

    fun clearFriendshipDeleted() {
        _friendshipDeleted.value = null
    }
}

/**
 * Message reçu en temps réel
 */
data class MessageReceived(
    val id: Int,
    val senderId: Int,
    val senderEmail: String,
    val content: String,
    val timestamp: Long,
    val fromServer: Boolean = false // true si le message était stocké offline
)

/**
 * Statut de livraison d'un message
 */
data class DeliveryStatus(
    val tempId: String?,
    val receiverId: Int,
    val timestamp: Long,
    val direct: Boolean, // true = livraison directe
    val stored: Boolean, // true = stocké sur serveur
    val serverMessageId: Int? = null
)

/**
 * Notification de demande d'ami
 */
data class FriendRequestNotification(
    val id: Int,
    val senderId: Int,
    val senderEmail: String
)

