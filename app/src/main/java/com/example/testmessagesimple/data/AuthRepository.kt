package com.example.testmessagesimple.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val api = RetrofitClient.api

    // Inscription
    suspend fun register(email: String, password: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = RegisterRequest(email, password)
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
                val response = api.getProfile("Bearer $token")

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