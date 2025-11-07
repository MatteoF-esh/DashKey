package com.example.testmessagesimple.data

import retrofit2.Response
import retrofit2.http.*

interface MessagingApi {

    // Inscription
    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    // Connexion
    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    // Profil utilisateur
    @GET("me")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<UserInfo>

    // Historique messages
    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Query("userId") otherUserId: Int,
        @Query("limit") limit: Int = 50
    ): Response<List<Message>>

    // Envoyer un message
    @POST("messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: SendMessageRequest
    ): Response<Message>
}