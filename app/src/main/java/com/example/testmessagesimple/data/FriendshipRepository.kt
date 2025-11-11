package com.example.testmessagesimple.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FriendshipRepository {
    private val api = RetrofitClient.api

    suspend fun searchUserByEmail(token: String, email: String): Result<UserInfo> {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG: Recherche utilisateur - email: $email")
                val response = api.searchUserByEmail("Bearer $token", email)
                println("DEBUG: Response code: ${response.code()}")
                println("DEBUG: Response body: ${response.body()}")

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Utilisateur non trouvé"
                    println("DEBUG: Erreur: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                println("DEBUG: Exception lors de la recherche: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun sendFriendRequest(token: String, receiverId: Int): Result<FriendRequestResponse> {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG Repository: ===== ENVOI DEMANDE D'AMI =====")
                println("DEBUG Repository: receiverId: $receiverId")
                println("DEBUG Repository: Token présent: ${token.isNotEmpty()}")

                val requestBody = FriendRequestRequest(receiverId)
                println("DEBUG Repository: Request body créé: $requestBody")

                val response = api.sendFriendRequest(
                    "Bearer $token",
                    requestBody
                )

                println("DEBUG Repository: Response code: ${response.code()}")
                println("DEBUG Repository: Response success: ${response.isSuccessful}")
                println("DEBUG Repository: Response body: ${response.body()}")

                val errorBody = response.errorBody()?.string()
                if (errorBody != null) {
                    println("DEBUG Repository: Response error body: $errorBody")
                }

                if (response.isSuccessful && response.body() != null) {
                    println("DEBUG Repository: ✅ Demande envoyée avec succès!")
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = errorBody ?: "Erreur inconnue (${response.code()})"
                    println("DEBUG Repository: ❌ Erreur: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                println("DEBUG Repository: ❌ EXCEPTION lors de l'envoi: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun getReceivedRequests(token: String): Result<List<FriendRequestItemResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG Repository: ===== RÉCUPÉRATION DES DEMANDES REÇUES =====")
                val response = api.getReceivedFriendRequests("Bearer $token")
                println("DEBUG Repository: Response code: ${response.code()}")
                println("DEBUG Repository: Response success: ${response.isSuccessful}")
                println("DEBUG Repository: Response body: ${response.body()}")

                if (response.isSuccessful && response.body() != null) {
                    val requests = response.body()!!.requests
                    println("DEBUG Repository: ✅ ${requests.size} demande(s) trouvée(s)")
                    requests.forEach { req ->
                        println("DEBUG Repository:   - ID: ${req.id}, De: ${req.sender.email}, Status: ${req.status}")
                    }
                    Result.success(requests)
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Erreur: ${response.message()}"
                    println("DEBUG Repository: ❌ Erreur: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                println("DEBUG Repository: ❌ EXCEPTION: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    suspend fun updateFriendRequest(token: String, requestId: Int, accept: Boolean): Result<FriendRequestResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val action = if (accept) "accept" else "decline"
                println("DEBUG: Mise à jour demande $requestId - action: $action")
                val response = api.updateFriendRequest(
                    "Bearer $token",
                    requestId,
                    UpdateFriendRequestRequest(action)
                )
                println("DEBUG: Response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                println("DEBUG: Exception: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun getFriends(token: String): Result<List<FriendResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG: Récupération de la liste d'amis")
                val response = api.getFriends("Bearer $token")
                println("DEBUG: Response code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!.friends)
                } else {
                    Result.failure(Exception("Erreur: ${response.message()}"))
                }
            } catch (e: Exception) {
                println("DEBUG: Exception: ${e.message}")
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

