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

    // Rechercher un utilisateur par email
    @GET("users/search")
    suspend fun searchUserByEmail(
        @Header("Authorization") token: String,
        @Query("email") email: String
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

    // ===== ENDPOINTS AMITIÉS =====

    // Envoyer une demande d'ami
    @POST("friends/request")
    suspend fun sendFriendRequest(
        @Header("Authorization") token: String,
        @Body request: FriendRequestRequest
    ): Response<FriendRequestResponse>

    // Récupérer toutes les demandes reçues
    @GET("friends/requests")
    suspend fun getReceivedFriendRequests(
        @Header("Authorization") token: String
    ): Response<FriendRequestsWrapper>

    // Accepter/Refuser une demande
    @PUT("friends/request/{requestId}")
    suspend fun updateFriendRequest(
        @Header("Authorization") token: String,
        @Path("requestId") requestId: Int,
        @Body request: UpdateFriendRequestRequest
    ): Response<FriendRequestResponse>

    // Liste des amis acceptés
    @GET("friends")
    suspend fun getFriends(
        @Header("Authorization") token: String
    ): Response<FriendsWrapper>

    // Supprimer un ami
    @DELETE("friends/{friendId}")
    suspend fun deleteFriend(
        @Header("Authorization") token: String,
        @Path("friendId") friendId: Int
    ): Response<Void>
}