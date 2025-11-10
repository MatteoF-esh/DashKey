package com.example.testmessagesimple

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testmessagesimple.data.FriendRequestResponse
import com.example.testmessagesimple.data.FriendResponse
import com.example.testmessagesimple.data.FriendshipRepository
import kotlinx.coroutines.launch

class FriendshipViewModel(private val token: String) : ViewModel() {
    private val repository = FriendshipRepository()

    var receivedRequests by mutableStateOf<List<FriendRequestResponse>>(emptyList())
        private set

    var sentRequests by mutableStateOf<List<FriendRequestResponse>>(emptyList())
        private set

    var friends by mutableStateOf<List<FriendResponse>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadAllData()
    }

    private fun loadAllData() {
        loadReceivedRequests()
        loadSentRequests()
        loadFriends()
    }

    fun sendFriendRequest(email: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repository.sendFriendRequest(token, email)
                .onSuccess {
                    loadSentRequests()
                    onSuccess()
                }
                .onFailure {
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
                .onSuccess { receivedRequests = it }
        }
    }

    private fun loadSentRequests() {
        viewModelScope.launch {
            repository.getSentRequests(token)
                .onSuccess { sentRequests = it }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            repository.getFriends(token)
                .onSuccess { friends = it }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun refresh() {
        loadAllData()
    }
}

