package com.example.testmessagesimple.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.testmessagesimple.AppDatabase
import com.example.testmessagesimple.Message
import com.example.testmessagesimple.data.AuthRepository
import com.example.testmessagesimple.data.DirectMessagingService
import com.example.testmessagesimple.utils.CryptoManager
import com.example.testmessagesimple.utils.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel pour la gestion des messages avec stockage local et livraison directe.
 *
 * Workflow :
 * 1. L'utilisateur envoie un message → stocké localement ET envoyé via Socket.IO
 * 2. Si destinataire en ligne → livraison directe (pas de BDD serveur)
 * 3. Si destinataire offline → stocké sur serveur temporairement (24h)
 * 4. À la réception d'un message → stocké localement dans Room
 * 5. Les messages sont TOUJOURS affichés depuis la base locale (Room)
 */
class DirectMessagingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "app_database"
    )
        .fallbackToDestructiveMigration() // Pour dev : recrée la DB si changement de schéma
        .build()

    private val dao = database.appDao()

    // Service de messagerie en temps réel
    private val messagingService = DirectMessagingService()

    // E2EE : Gestionnaire de chiffrement
    private val cryptoManager = CryptoManager(application)

    // TokenManager pour récupérer le token JWT
    private val tokenManager = TokenManager(application)

    // AuthRepository pour récupérer les clés publiques
    private val authRepository = AuthRepository()

    // État de connexion
    val isConnected: StateFlow<Boolean> = messagingService.isConnected

    // Utilisateur actuel
    private val _currentUserId = MutableStateFlow<Int?>(null)
    val currentUserId: StateFlow<Int?> = _currentUserId.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    // Statut d'envoi
    private val _sendStatus = MutableStateFlow<SendStatus?>(null)
    val sendStatus: StateFlow<SendStatus?> = _sendStatus.asStateFlow()

    companion object {
        private const val TAG = "DirectMessagingVM"
    }

    init {
        // Observer les messages reçus en temps réel
        viewModelScope.launch {
            messagingService.receivedMessages.collect { received ->
                received?.let {
                    handleReceivedMessage(it)
                    messagingService.clearMessageState()
                }
            }
        }

        // Observer le statut de livraison
        viewModelScope.launch {
            messagingService.deliveryStatus.collect { status ->
                status?.let {
                    _sendStatus.value = if (it.direct) {
                        SendStatus.DeliveredDirect
                    } else {
                        SendStatus.StoredOffline
                    }
                    messagingService.clearDeliveryStatus()
                }
            }
        }

        // Observer les demandes d'ami
        viewModelScope.launch {
            messagingService.friendRequestReceived.collect { notification ->
                notification?.let {
                    Log.d(TAG, "👥 Nouvelle demande d'ami de ${it.senderEmail}")
                    // Ici, vous pouvez déclencher une notification ou mettre à jour l'UI
                    messagingService.clearFriendRequestNotification()
                }
            }
        }

        // Observer les amitiés supprimées
        viewModelScope.launch {
            messagingService.friendshipDeleted.collect { friendshipId ->
                friendshipId?.let {
                    Log.d(TAG, "💔 Amitié supprimée: $it")
                    // Supprimer l'amitié localement
                    // TODO: implementer selon votre logique
                    messagingService.clearFriendshipDeleted()
                }
            }
        }
    }

    /**
     * Connexion au service de messagerie en temps réel
     */
    fun connectMessaging(token: String, userId: Int, userEmail: String) {
        _currentUserId.value = userId
        _currentUserEmail.value = userEmail
        messagingService.connect(token, userId)
        Log.d(TAG, "🔌 Connexion au service de messagerie pour user $userId")
    }

    /**
     * Déconnexion du service
     */
    fun disconnectMessaging() {
        messagingService.disconnect()
        Log.d(TAG, "👋 Déconnexion du service de messagerie")
    }

    /**
     * Envoyer un message
     * 1. Stocke localement immédiatement
     * 2. Envoie via Socket.IO
     * 3. Le serveur détermine si livraison directe ou stockage offline
     */
    fun sendMessage(
        receiverId: Int,
        receiverEmail: String,
        content: String,
        conversationId: String
    ) {
        val currentUserId = _currentUserId.value
        val currentUserEmail = _currentUserEmail.value

        if (currentUserId == null || currentUserEmail == null) {
            Log.e(TAG, "❌ Impossible d'envoyer : utilisateur non connecté")
            _sendStatus.value = SendStatus.Error("Utilisateur non connecté")
            return
        }

        if (content.isBlank()) {
            Log.e(TAG, "❌ Message vide")
            _sendStatus.value = SendStatus.Error("Message vide")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "📨 ENVOI DE MESSAGE")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "📝 Message en CLAIR : '$content'")
                Log.d(TAG, "👤 Destinataire : $receiverEmail (ID: $receiverId)")

                val tempId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                // CHIFFREMENT E2EE : Récupérer la clé publique du destinataire
                Log.d(TAG, "🔑 Récupération de la clé publique du destinataire...")
                val recipientPublicKey = getRecipientPublicKey(receiverId)

                val messageToSend: String
                if (recipientPublicKey != null) {
                    // CHIFFRER le message avec la clé publique du destinataire
                    Log.d(TAG, "🔐 Chiffrement du message avec RSA...")
                    messageToSend = cryptoManager.encryptMessage(content, recipientPublicKey)
                    Log.d(TAG, "✅ Message CHIFFRÉ : ${messageToSend.take(50)}...")
                    Log.d(TAG, "📏 Taille chiffrée : ${messageToSend.length} caractères")
                } else {
                    // Pas de clé publique = envoyer en clair (fallback)
                    Log.w(TAG, "⚠️ ATTENTION : Pas de clé publique pour le destinataire !")
                    Log.w(TAG, "⚠️ Message envoyé EN CLAIR (non sécurisé)")
                    messageToSend = content
                }

                // 1. Stocker localement EN CLAIR (pour pouvoir le relire)
                val localMessage = Message(
                    senderId = currentUserId,
                    receiverId = receiverId,
                    senderEmail = currentUserEmail,
                    text = content, // Stocké EN CLAIR localement
                    timestamp = timestamp,
                    conversationId = conversationId,
                    isSentByMe = true,
                    serverMessageId = null,
                    fromServer = false
                )

                dao.insertMessage(localMessage)
                Log.d(TAG, "💾 Message stocké localement EN CLAIR (pour relecture)")

                // 2. Envoyer le message CHIFFRÉ via Socket.IO
                _sendStatus.value = SendStatus.Sending
                messagingService.sendMessage(receiverId, messageToSend, tempId)
                Log.d(TAG, "📤 Message ${if (recipientPublicKey != null) "CHIFFRÉ" else "EN CLAIR"} envoyé via Socket.IO")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'envoi", e)
                e.printStackTrace()
                _sendStatus.value = SendStatus.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    /**
     * Récupère la clé publique du destinataire
     */
    private suspend fun getRecipientPublicKey(userId: Int): String? {
        return try {
            // Récupérer depuis l'AuthRepository
            val token = "Bearer ${tokenManager.getAuthData()?.first ?: ""}"
            val result = authRepository.getPublicKey(token, userId)

            result.getOrNull()?.publicKey.also { key ->
                if (key != null) {
                    Log.d(TAG, "✅ Clé publique récupérée : ${key.take(30)}...")
                } else {
                    Log.w(TAG, "⚠️ Clé publique non disponible pour l'utilisateur $userId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur récupération clé publique", e)
            null
        }
    }

    /**
     * Gérer la réception d'un message
     * Stocke le message localement après déchiffrement
     */
    private fun handleReceivedMessage(received: com.example.testmessagesimple.data.MessageReceived) {
        viewModelScope.launch {
            try {
                val currentUserId = _currentUserId.value ?: return@launch

                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "📨 RÉCEPTION DE MESSAGE")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(TAG, "👤 Expéditeur : ${received.senderEmail} (ID: ${received.senderId})")
                Log.d(TAG, "📦 Message REÇU (potentiellement chiffré) : ${received.content.take(50)}...")
                Log.d(TAG, "📏 Taille reçue : ${received.content.length} caractères")

                // Créer l'ID de conversation (même format que pour l'envoi)
                val conversationId = createConversationId(currentUserId, received.senderId)

                // DÉCHIFFREMENT E2EE : Essayer de déchiffrer avec notre clé privée
                val decryptedContent: String = try {
                    Log.d(TAG, "🔓 Tentative de déchiffrement avec clé privée...")
                    val decrypted = cryptoManager.decryptMessage(received.content)
                    Log.d(TAG, "✅ Message DÉCHIFFRÉ : '$decrypted'")
                    Log.d(TAG, "🔐 Le message était bien CHIFFRÉ !")
                    decrypted
                } catch (e: Exception) {
                    // Si le déchiffrement échoue, c'est que le message était en clair
                    Log.w(TAG, "⚠️ Déchiffrement échoué - Le message était EN CLAIR")
                    Log.w(TAG, "📝 Contenu en clair : '${received.content}'")
                    received.content
                }

                val message = Message(
                    senderId = received.senderId,
                    receiverId = currentUserId,
                    senderEmail = received.senderEmail,
                    text = decryptedContent, // Stocker EN CLAIR pour pouvoir le lire
                    timestamp = received.timestamp,
                    conversationId = conversationId,
                    isSentByMe = false,
                    serverMessageId = if (received.fromServer) received.id else null,
                    fromServer = received.fromServer
                )

                dao.insertMessage(message)

                val source = if (received.fromServer) "serveur (était offline)" else "livraison directe"
                Log.d(TAG, "💾 Message déchiffré et stocké localement ($source)")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du traitement du message reçu", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Récupérer les messages d'une conversation (depuis Room local)
     */
    fun getMessagesForConversation(conversationId: String) = dao.getMessagesForConversation(conversationId)

    /**
     * Supprimer les messages d'une conversation
     */
    fun deleteMessagesForConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                dao.deleteMessagesForConversation(conversationId)
                Log.d(TAG, "🗑️ Messages supprimés pour conversation $conversationId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de la suppression des messages", e)
            }
        }
    }

    /**
     * Réinitialiser le statut d'envoi
     */
    fun clearSendStatus() {
        _sendStatus.value = null
    }

    /**
     * Créer un ID de conversation unique et cohérent entre deux utilisateurs
     */
    private fun createConversationId(userId1: Int, userId2: Int): String {
        val sorted = listOf(userId1, userId2).sorted()
        return "conv_${sorted[0]}_${sorted[1]}"
    }

    override fun onCleared() {
        super.onCleared()
        disconnectMessaging()
    }
}

/**
 * Statut d'envoi d'un message
 */
sealed class SendStatus {
    object Sending : SendStatus()
    object DeliveredDirect : SendStatus() // Livré directement au destinataire
    object StoredOffline : SendStatus() // Stocké sur le serveur (destinataire offline)
    data class Error(val message: String) : SendStatus()
}

