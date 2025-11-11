package com.example.testmessagesimple.data

class MessagingRepository {
    private val api = RetrofitClient.api

    suspend fun getMessages(token: String, otherUserId: Int): Result<List<Message>> {
        return try {
            val response = api.getMessages("Bearer $token", otherUserId)
            if (response.isSuccessful && response.body() != null) {
                val messages = response.body()!!
                println("DEBUG Repository: Messages récupérés - ${messages.size} messages")
                Result.success(messages)
            } else {
                val errorMsg = "Erreur ${response.code()}: ${response.message()}"
                println("DEBUG Repository: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            println("DEBUG Repository: Exception - ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(token: String, receiverId: Int, content: String): Result<Message> {
        return try {
            val request = SendMessageRequest(receiverId = receiverId, content = content)
            println("DEBUG Repository: Envoi message - receiverId=$receiverId, content=$content")

            val response = api.sendMessage("Bearer $token", request)
            if (response.isSuccessful && response.body() != null) {
                val message = response.body()!!
                println("DEBUG Repository: Message envoyé avec succès - ID ${message.id}")
                Result.success(message)
            } else {
                val errorMsg = "Erreur ${response.code()}: ${response.message()}"
                println("DEBUG Repository: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            println("DEBUG Repository: Exception - ${e.message}")
            Result.failure(e)
        }
    }
}

