package com.example.testmessagesimple

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testmessagesimple.data.AuthRepository
import com.example.testmessagesimple.data.Message as ApiMessage
import com.example.testmessagesimple.data.MessagingRepository
import com.example.testmessagesimple.utils.CryptoManager
import com.example.testmessagesimple.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MessagingViewModel(application: Application, private val token: String) : AndroidViewModel(application) {
    private val repository = MessagingRepository()

    // E2EE : Gestionnaire de chiffrement
    private val cryptoManager = CryptoManager(application)
    private val tokenManager = TokenManager(application)
    private val authRepository = AuthRepository()

    // Accès à la base de données Room pour stocker les messages en clair
    private val database = androidx.room.Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "app_database"
    )
        .fallbackToDestructiveMigration()
        .build()

    private val dao = database.appDao()

    companion object {
        private const val TAG = "MessagingViewModel"
    }
    private var currentOtherUserId: Int? = null

    // Cache local : Stocker les messages envoyés en CLAIR
    // Clé = ID du message, Valeur = Contenu en clair
    private val sentMessagesCache = mutableMapOf<Int, String>()

    var messages by mutableStateOf<List<ApiMessage>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun loadMessages(otherUserId: Int) {
        currentOtherUserId = otherUserId

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            Log.d(TAG, "📥 Chargement initial des messages avec user $otherUserId")

            // Charger les messages Room en clair
            val roomMessagesMap = loadRoomMessagesMap(otherUserId)
            Log.d(TAG, "💾 ${roomMessagesMap.size} messages trouvés dans Room")

            // Charger depuis l'API et déchiffrer
            repository.getMessages(token, otherUserId)
                .onSuccess { loadedMessages ->
                    Log.d(TAG, "📥 ${loadedMessages.size} messages chargés de l'API")

                    // Déchiffrer les messages en utilisant Room comme source si disponible
                    messages = loadedMessages.map { msg ->
                        decryptMessageIfNeeded(msg, roomMessagesMap)
                    }
                    Log.d(TAG, "✅ ${messages.size} messages déchiffrés et affichés")

                    // Sauvegarder en arrière-plan dans Room pour l'historique
                    storeMessagesInRoom(messages, otherUserId)
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Erreur chargement - ${error.message}")
                    errorMessage = error.message
                }

            isLoading = false
        }

        // Démarrer le rafraîchissement automatique
        startAutoRefresh()
    }

    /**
     * Charge les messages Room en clair dans un Map pour accès rapide
     */
    private suspend fun loadRoomMessagesMap(otherUserId: Int): Map<Int?, Message> {
        val authData = tokenManager.getAuthData() ?: return emptyMap()

        return try {
            val currentUserId = authData.second.id
            val conversationId = createConversationId(currentUserId, otherUserId)

            // Charger les messages depuis Room
            val messagesList = dao.getMessagesForConversation(conversationId).first()

            Log.d(TAG, "💾 ${messagesList.size} messages chargés depuis Room pour conversationId: $conversationId")
            messagesList.forEach { msg ->
                Log.d(TAG, "   → Message ID ${msg.serverMessageId}: '${msg.text.take(30)}...' (isSentByMe=${msg.isSentByMe})")
            }

            // Créer un Map avec serverMessageId comme clé
            messagesList.associateBy { it.serverMessageId }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur chargement Room", e)
            emptyMap()
        }
    }

    /**
     * Déchiffre un message s'il est chiffré
     * Priorité : 1. Room (historique en clair) 2. Cache mémoire 3. Déchiffrement
     */
    private fun decryptMessageIfNeeded(message: ApiMessage, roomMessagesMap: Map<Int?, Message> = emptyMap()): ApiMessage {
        Log.d(TAG, "🔍 Déchiffrement message ID ${message.id}")
        Log.d(TAG, "   → Room Map contient ${roomMessagesMap.size} messages")
        Log.d(TAG, "   → Clés disponibles dans Room: ${roomMessagesMap.keys.joinToString()}")

        // 1. Vérifier d'abord dans Room (historique en clair)
        val roomMessage = roomMessagesMap[message.id]
        if (roomMessage != null) {
            Log.d(TAG, "✅ Message ${message.id} TROUVÉ dans Room EN CLAIR : ${roomMessage.text.take(30)}...")
            return message.copy(content = roomMessage.text)
        } else {
            Log.d(TAG, "❌ Message ${message.id} NON TROUVÉ dans Room")
        }

        // 2. Vérifier dans le cache mémoire (messages envoyés dans cette session)
        val cachedContent = sentMessagesCache[message.id]
        if (cachedContent != null) {
            Log.d(TAG, "✅ Message ${message.id} TROUVÉ dans cache mémoire : ${cachedContent.take(30)}...")
            return message.copy(content = cachedContent)
        } else {
            Log.d(TAG, "❌ Message ${message.id} NON TROUVÉ dans cache mémoire")
        }

        // 3. Sinon, essayer de déchiffrer (nouveau message reçu)
        Log.d(TAG, "🔐 Tentative de déchiffrement pour message ${message.id}")
        return try {
            val decrypted = cryptoManager.decryptMessage(message.content)
            Log.d(TAG, "✅ Message ${message.id} DÉCHIFFRÉ : ${decrypted.take(30)}...")
            message.copy(content = decrypted)
        } catch (e: Exception) {
            // Si le déchiffrement échoue, le message était en clair
            Log.w(TAG, "⚠️ Déchiffrement échoué pour message ${message.id}, affichage en clair : ${message.content.take(30)}...")
            message
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (currentOtherUserId != null) {
                delay(3000) // Rafraîchir toutes les 3 secondes
                currentOtherUserId?.let { userId ->
                    // Charger les messages Room pour le déchiffrement
                    val roomMessagesMap = loadRoomMessagesMap(userId)

                    // Rafraîchir depuis l'API
                    repository.getMessages(token, userId)
                        .onSuccess { loadedMessages ->
                            // Déchiffrer avec Room comme source
                            val decryptedMessages = loadedMessages.map { msg ->
                                decryptMessageIfNeeded(msg, roomMessagesMap)
                            }

                            // Mettre à jour seulement si changement
                            if (decryptedMessages.size != messages.size) {
                                Log.d(TAG, "🔄 Nouveaux messages détectés (${decryptedMessages.size} vs ${messages.size})")
                                messages = decryptedMessages

                                // Sauvegarder en arrière-plan dans Room
                                storeMessagesInRoom(decryptedMessages, userId)
                            }
                        }
                        .onFailure { error ->
                            Log.d(TAG, "⚠️ Erreur auto-refresh - ${error.message}")
                        }
                }
            }
        }
    }

    fun sendMessage(receiverId: Int, content: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "📨 ENVOI DE MESSAGE")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "📝 Message en CLAIR : '$content'")
            Log.d(TAG, "👤 Destinataire ID : $receiverId")

            // CHIFFREMENT E2EE : Récupérer la clé publique du destinataire
            val messageToSend: String = try {
                Log.d(TAG, "🔑 Récupération de la clé publique du destinataire...")
                val recipientPublicKey = getRecipientPublicKey(receiverId)
                
                if (recipientPublicKey != null) {
                    // CHIFFRER le message avec la clé publique du destinataire
                    Log.d(TAG, "🔐 Chiffrement du message avec RSA...")
                    val encrypted = cryptoManager.encryptMessage(content, recipientPublicKey)
                    Log.d(TAG, "✅ Message CHIFFRÉ : ${encrypted.take(50)}...")
                    Log.d(TAG, "📏 Taille chiffrée : ${encrypted.length} caractères")
                    encrypted
                } else {
                    // Pas de clé publique = envoyer en clair (fallback)
                    Log.w(TAG, "⚠️ ATTENTION : Pas de clé publique pour le destinataire !")
                    Log.w(TAG, "⚠️ Message envoyé EN CLAIR (non sécurisé)")
                    content
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du chiffrement", e)
                content // Fallback : envoyer en clair
            }

            // Envoyer le message (chiffré ou en clair)
            repository.sendMessage(token, receiverId, messageToSend)
                .onSuccess { newMessage ->
                    Log.d(TAG, "✅ Message envoyé avec succès - ID ${newMessage.id}")

                    // IMPORTANT : Sauvegarder le message en CLAIR dans le cache local
                    // pour pouvoir le réafficher correctement
                    sentMessagesCache[newMessage.id] = content
                    Log.d(TAG, "💾 Message ${newMessage.id} sauvegardé en clair dans le cache")

                    // Stocker en CLAIR dans Room pour historique permanent
                    val authData = tokenManager.getAuthData()
                    if (authData != null) {
                        val currentUserId = authData.second.id
                        val currentUserEmail = authData.second.email
                        val conversationId = createConversationId(currentUserId, receiverId)

                        val localMessage = Message(
                            senderId = currentUserId,
                            receiverId = receiverId,
                            senderEmail = currentUserEmail,
                            text = content, // Stocké EN CLAIR localement
                            timestamp = System.currentTimeMillis(),
                            conversationId = conversationId,
                            isSentByMe = true,
                            serverMessageId = newMessage.id,
                            fromServer = false
                        )

                        // Sauvegarder dans Room AVANT de recharger
                        dao.insertMessage(localMessage)
                        Log.d(TAG, "💾 Message stocké en clair dans Room")
                        Log.d(TAG, "   → conversationId: $conversationId")
                        Log.d(TAG, "   → serverMessageId: ${newMessage.id}")
                        Log.d(TAG, "   → text: '${content.take(30)}...'")
                        Log.d(TAG, "   → isSentByMe: true")
                    }

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    // Recharger immédiatement depuis l'API pour afficher le nouveau message
                    loadMessages(receiverId)
                    onSuccess()
                }
                .onFailure { error ->
                    Log.e(TAG, "❌ Erreur d'envoi - ${error.message}")
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    errorMessage = error.message
                }

            isLoading = false
        }
    }

    /**
     * Récupère la clé publique du destinataire
     */
    private suspend fun getRecipientPublicKey(userId: Int): String? {
        return try {
            val fullToken = "Bearer $token"
            val result = authRepository.getPublicKey(fullToken, userId)

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

    fun clearError() {
        errorMessage = null
    }

    /**
     * Stocke les messages déchiffrés en CLAIR dans Room pour historique permanent
     */
    private fun storeMessagesInRoom(messages: List<ApiMessage>, otherUserId: Int) {
        viewModelScope.launch {
            try {
                val authData = tokenManager.getAuthData()
                if (authData != null) {
                    val currentUserId = authData.second.id
                    val conversationId = createConversationId(currentUserId, otherUserId)

                    Log.d(TAG, "💾 Sauvegarde de ${messages.size} messages dans Room")
                    Log.d(TAG, "   → conversationId: $conversationId")

                    messages.forEach { msg ->
                        // Déterminer si c'est un message envoyé ou reçu
                        val isSentByMe = msg.senderId == currentUserId
                        val senderId = msg.senderId
                        val receiverId = msg.receiverId

                        // Obtenir l'email de l'expéditeur
                        val senderEmail = if (isSentByMe) {
                            authData.second.email
                        } else {
                            msg.sender?.email ?: "Inconnu"
                        }

                        val localMessage = Message(
                            senderId = senderId,
                            receiverId = receiverId,
                            senderEmail = senderEmail,
                            text = msg.content, // Déjà déchiffré, stocké EN CLAIR
                            timestamp = System.currentTimeMillis(),
                            conversationId = conversationId,
                            isSentByMe = isSentByMe,
                            serverMessageId = msg.id,
                            fromServer = true
                        )

                        dao.insertMessage(localMessage)
                        Log.d(TAG, "   ✅ Message ${msg.id} sauvegardé: '${msg.content.take(30)}...' (isSentByMe=$isSentByMe)")
                    }

                    Log.d(TAG, "✅ ${messages.size} messages stockés en clair dans Room")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur stockage messages dans Room", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Crée un ID de conversation unique et cohérent entre deux utilisateurs
     * Format : conv_<userId_plus_petit>_<userId_plus_grand>
     */
    private fun createConversationId(userId1: Int, userId2: Int): String {
        val sorted = listOf(userId1, userId2).sorted()
        return "conv_${sorted[0]}_${sorted[1]}"
    }

    override fun onCleared() {
        super.onCleared()
        currentOtherUserId = null // Arrêter le rafraîchissement automatique
    }
}

