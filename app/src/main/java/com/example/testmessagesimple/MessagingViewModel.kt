package com.example.testmessagesimple

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testmessagesimple.data.Message
import com.example.testmessagesimple.data.MessagingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessagingViewModel(private val token: String) : ViewModel() {
    private val repository = MessagingRepository()
    private var currentOtherUserId: Int? = null

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
            println("DEBUG MessagingViewModel: Chargement des messages avec user $otherUserId")

            repository.getMessages(token, otherUserId)
                .onSuccess { loadedMessages ->
                    println("DEBUG MessagingViewModel: ${loadedMessages.size} messages chargés")
                    messages = loadedMessages
                }
                .onFailure { error ->
                    println("DEBUG MessagingViewModel: Erreur - ${error.message}")
                    errorMessage = error.message
                }

            isLoading = false
        }

        // Démarrer le rafraîchissement automatique
        startAutoRefresh()
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
                                println("DEBUG MessagingViewModel: Nouveaux messages détectés (${loadedMessages.size} vs ${messages.size})")
                                messages = loadedMessages
                            }
                        }
                        .onFailure { error ->
                            println("DEBUG MessagingViewModel: Erreur auto-refresh - ${error.message}")
                        }
                }
            }
        }
    }

    fun sendMessage(receiverId: Int, content: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            println("DEBUG MessagingViewModel: Envoi message à $receiverId: $content")

            repository.sendMessage(token, receiverId, content)
                .onSuccess { newMessage ->
                    println("DEBUG MessagingViewModel: Message envoyé avec succès - ID ${newMessage.id}")
                    // Rafraîchir immédiatement les messages après envoi
                    loadMessages(receiverId)
                    onSuccess()
                }
                .onFailure { error ->
                    println("DEBUG MessagingViewModel: Erreur d'envoi - ${error.message}")
                    errorMessage = error.message
                }

            isLoading = false
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

