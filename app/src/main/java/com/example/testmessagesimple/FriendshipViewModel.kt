package com.example.testmessagesimple

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testmessagesimple.data.FriendRequestItemResponse
import com.example.testmessagesimple.data.FriendResponse
import com.example.testmessagesimple.data.FriendshipRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FriendshipViewModel(private val token: String) : ViewModel() {
    private val repository = FriendshipRepository()

    var receivedRequests by mutableStateOf<List<FriendRequestItemResponse>>(emptyList())
        private set

    var friends by mutableStateOf<List<FriendResponse>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadAllData()
        // Rafraîchir automatiquement toutes les 10 secondes (pour détecter les suppressions)
        viewModelScope.launch {
            while (true) {
                delay(10000)
                loadReceivedRequests()
                loadFriends() // Ajouter aussi le rafraîchissement des amis
            }
        }
    }

    private fun loadAllData() {
        loadReceivedRequests()
        loadFriends()
    }

    fun sendFriendRequestByEmail(email: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            println("🔍 ViewModel: Recherche utilisateur avec email: $email")

            // D'abord, rechercher l'utilisateur par email pour obtenir son ID
            repository.searchUserByEmail(token, email)
                .onSuccess { user ->
                    println("✅ ViewModel: Utilisateur trouvé")
                    println("   - ID: ${user.id}")
                    println("   - Email: ${user.email}")
                    println("   - Roles: ${user.roles}")

                    if (user.id <= 0) {
                        println("❌ ViewModel: ID utilisateur invalide (${user.id})")
                        errorMessage = "Erreur : ID utilisateur invalide"
                        isLoading = false
                        return@onSuccess
                    }

                    // Ensuite, envoyer la demande d'ami avec l'ID
                    sendFriendRequestById(user.id, onSuccess)
                }
                .onFailure {
                    println("❌ ViewModel: Échec de la recherche - ${it.message}")
                    errorMessage = "Utilisateur non trouvé : ${it.message}"
                    isLoading = false
                }
        }
    }

    fun sendFriendRequestById(receiverId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            println("DEBUG ViewModel: Envoi demande à l'ID $receiverId")

            // Validation de l'ID
            if (receiverId <= 0) {
                println("❌ ViewModel: receiverId invalide ($receiverId)")
                errorMessage = "Erreur : ID utilisateur invalide ($receiverId)"
                isLoading = false
                return@launch
            }
            repository.sendFriendRequest(token, receiverId)
                .onSuccess {
                    println("DEBUG ViewModel: Demande envoyée avec succès")
                    loadAllData() // Recharger les données
                    onSuccess()
                }
                .onFailure {
                    println("DEBUG ViewModel: Erreur - ${it.message}")
                    errorMessage = it.message ?: "Erreur lors de l'envoi"
                }
            isLoading = false
        }
    }

    fun acceptRequest(requestId: Int) {
        viewModelScope.launch {
            repository.updateFriendRequest(token, requestId, true)
                .onSuccess {
                    loadAllData()
                }
                .onFailure {
                    errorMessage = it.message
                }
        }
    }

    fun declineRequest(requestId: Int) {
        viewModelScope.launch {
            repository.updateFriendRequest(token, requestId, false)
                .onSuccess {
                    loadReceivedRequests()
                }
                .onFailure {
                    errorMessage = it.message
                }
        }
    }

    fun deleteFriend(friendId: Int) {
        viewModelScope.launch {
            repository.deleteFriend(token, friendId)
                .onSuccess {
                    loadFriends()
                }
                .onFailure {
                    errorMessage = it.message
                }
        }
    }

    private fun loadReceivedRequests() {
        viewModelScope.launch {
            repository.getReceivedRequests(token)
                .onSuccess {
                    println("DEBUG ViewModel: ${it.size} demandes reçues")
                    receivedRequests = it
                }
                .onFailure {
                    println("DEBUG ViewModel: Erreur chargement demandes - ${it.message}")
                }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            repository.getFriends(token)
                .onSuccess {
                    println("DEBUG ViewModel: ${it.size} amis chargés")
                    friends = it
                }
                .onFailure {
                    println("DEBUG ViewModel: Erreur chargement amis - ${it.message}")
                }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun refresh() {
        println("DEBUG ViewModel: Rafraîchissement manuel")
        loadAllData()
    }
}

