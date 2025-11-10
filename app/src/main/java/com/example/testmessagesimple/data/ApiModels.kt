package com.example.testmessagesimple.data

import com.google.gson.annotations.SerializedName

// ===== REQUÊTES =====

data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class SendMessageRequest(
    val receiverId: Int,
    val content: String
)

// ===== RÉPONSES =====

data class AuthResponse(
    val message: String,
    val token: String,
    val user: UserInfo
)

data class UserInfo(
    val id: Int,
    val email: String,
    val roles: List<String>
)

data class ErrorResponse(
    val error: String,
    val message: String
)

data class Message(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val content: String,
    val createdAt: String,
    val sender: UserBasic? = null,
    val receiver: UserBasic? = null
)

data class UserBasic(
    val id: Int,
    val email: String
)

// ===== AMITIÉS =====

data class FriendRequestRequest(
    val receiverEmail: String
)

data class FriendRequestResponse(
    val id: Int,
    val senderId: Int,
    val receiverId: Int,
    val status: String, // "PENDING", "ACCEPTED", "DECLINED"
    val createdAt: String,
    val sender: UserBasic,
    val receiver: UserBasic
)

data class UpdateFriendRequestRequest(
    val status: String // "ACCEPTED" ou "DECLINED"
)

data class FriendResponse(
    val id: Int,
    val email: String
)

