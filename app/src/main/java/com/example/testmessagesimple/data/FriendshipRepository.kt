package com.example.testmessagesimple.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FriendshipRepository {
    private val api = RetrofitClient.api

    suspend fun sendFriendRequest(token: String, receiverEmail: String): Result<FriendRequestResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendFriendRequest(
                    "Bearer $token",
                    FriendRequestRequest(receiverEmail)
                )
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Erreur inconnue"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getReceivedRequests(token: String): Result<List<FriendRequestResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getReceivedFriendRequests("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSentRequests(token: String): Result<List<FriendRequestResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getSentFriendRequests("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateFriendRequest(token: String, requestId: Int, accept: Boolean): Result<FriendRequestResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val status = if (accept) "ACCEPTED" else "DECLINED"
                val response = api.updateFriendRequest(
                    "Bearer $token",
                    requestId,
                    UpdateFriendRequestRequest(status)
                )
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getFriends(token: String): Result<List<FriendResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getFriends("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteFriend(token: String, friendId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.deleteFriend("Bearer $token", friendId)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

