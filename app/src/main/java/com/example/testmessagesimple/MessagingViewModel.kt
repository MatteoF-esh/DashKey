package com.example.testmessagesimple

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testmessagesimple.data.AuthRepository
import com.example.testmessagesimple.data.Message
import com.example.testmessagesimple.data.MessagingRepository
import com.example.testmessagesimple.utils.CryptoManager
import com.example.testmessagesimple.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessagingViewModel(application: Application, private val token: String) : AndroidViewModel(application) {
    private val repository = MessagingRepository()

    // E2EE : Gestionnaire de chiffrement
    private val cryptoManager = CryptoManager(application)
    private val tokenManager = TokenManager(application)
    private val authRepository = AuthRepository()

    companion object {
        private const val TAG = "MessagingViewModel"
    }
    private var currentOtherUserId: Int? = null

    // Cache local : Stocker les messages envoyés en CLAIR
    // Clé = ID du message, Valeur = Contenu en clair
    private val sentMessagesCache = mutableMapOf<Int, String>()

    var messages by mutableStateOf<List<Message>>(emptyList())
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
            Log.d(TAG, "📥 Chargement des messages avec user $otherUserId")

            repository.getMessages(token, otherUserId)
                .onSuccess { loadedMessages ->
                    Log.d(TAG, "📥 ${loadedMessages.size} messages chargés - Déchiffrement en cours...")
                    // Déchiffrer tous les messages reçus
                    messages = loadedMessages.map { msg -> decryptMessageIfNeeded(msg) }
                    Log.d(TAG, "✅ ${messages.size} messages déchiffrés et prêts")
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
     * Déchiffre un message s'il est chiffré
     * Pour les messages ENVOYÉS : utilise le cache local (texte en clair)
     * Pour les messages REÇUS : déchiffre avec notre clé privée
     */
    private fun decryptMessageIfNeeded(message: Message): Message {
        // 1. Vérifier d'abord si c'est un message qu'on a envoyé (dans le cache)
        val cachedContent = sentMessagesCache[message.id]
        if (cachedContent != null) {
            Log.d(TAG, "💾 Message ${message.id} récupéré du cache : ${cachedContent.take(20)}...")
            return message.copy(content = cachedContent)
        }

        // 2. Sinon, essayer de déchiffrer (message reçu)
        return try {
            val decrypted = cryptoManager.decryptMessage(message.content)
            Log.d(TAG, "🔓 Message ${message.id} déchiffré : ${decrypted.take(20)}...")
            message.copy(content = decrypted)
        } catch (e: Exception) {
            // Si le déchiffrement échoue, le message était en clair
            Log.d(TAG, "⚠️ Message ${message.id} en clair : ${message.content.take(20)}...")
            message
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (currentOtherUserId != null) {
                delay(3000) // Rafraîchir toutes les 3 secondes
                currentOtherUserId?.let { userId ->
                    // Rafraîchir sans afficher le loading
                    repository.getMessages(token, userId)
                        .onSuccess { loadedMessages ->
                            if (loadedMessages.size != messages.size) {
                                Log.d(TAG, "🔄 Nouveaux messages détectés (${loadedMessages.size} vs ${messages.size})")
                                // Déchiffrer les messages
                                messages = loadedMessages.map { msg -> decryptMessageIfNeeded(msg) }
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

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    // Rafraîchir immédiatement les messages après envoi
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

    override fun onCleared() {
        super.onCleared()
        currentOtherUserId = null // Arrêter le rafraîchissement automatique
    }
}

