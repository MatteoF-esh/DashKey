package com.example.testmessagesimple.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val api = RetrofitClient.api

    // Inscription
    suspend fun register(email: String, password: String, publicKey: String? = null): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = RegisterRequest(email, password, publicKey)
                val response = api.register(request)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur ${response.code()}: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Mettre à jour la clé publique
    suspend fun updatePublicKey(token: String, publicKey: String): Result<UpdatePublicKeyResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = UpdatePublicKeyRequest(publicKey)
                val response = api.updatePublicKey(token, request)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Erreur lors de la mise à jour de la clé publique"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Récupérer la clé publique d'un utilisateur
    suspend fun getPublicKey(token: String, userId: Int): Result<PublicKeyResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getPublicKey(token, userId)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Clé publique non disponible"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Connexion
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(email, password)
                val response = api.login(request)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Email ou mot de passe incorrect"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Récupérer profil
    suspend fun getProfile(token: String): Result<UserInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getProfile(token)

                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Token invalide"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}