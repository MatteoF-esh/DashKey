package com.example.testmessagesimple.data


// ===== REQUÊTES =====

data class RegisterRequest(
    val email: String,
    val password: String,
    val publicKey: String? = null
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
    val roles: List<String> = emptyList(),
    val publicKey: String? = null
)

data class UserSearchResponse(
    val users: List<UserInfo>
)

data class UpdatePublicKeyRequest(
    val publicKey: String
)

data class UpdatePublicKeyResponse(
    val message: String,
    val publicKey: String
)

data class PublicKeyResponse(
    val userId: Int,
    val email: String,
    val publicKey: String
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
    val email: String,
    val publicKey: String? = null
)

// ===== AMITIÉS =====

data class FriendRequestRequest(
    val receiverId: Int? = null,
    val receiverEmail: String? = null
)

data class FriendRequestResponse(
    val id: Int,
    val senderId: Int? = null,
    val receiverId: Int? = null,
    val status: String, // "pending", "accepted", "declined"
    val createdAt: String,
    val sender: UserBasic? = null,
    val receiver: UserBasic? = null,
    val message: String? = null // Pour le message de succès du serveur
)

data class FriendRequestsWrapper(
    val requests: List<FriendRequestItemResponse>
)

data class FriendRequestItemResponse(
    val id: Int,
    val sender: UserBasic,
    val status: String,
    val createdAt: String
)

data class UpdateFriendRequestRequest(
    val action: String // "accept" ou "decline"
)

data class FriendResponse(
    val friendshipId: Int,
    val friend: UserBasic,
    val since: String
)

data class FriendsWrapper(
    val friends: List<FriendResponse>
)

