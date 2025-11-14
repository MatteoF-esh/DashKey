package com.example.testmessagesimple

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- Requêtes FRIENDSHIP ---

    /**
     * Récupère toutes les amitiés pour un utilisateur donné.
     * Une amitié est pertinente si l'utilisateur actuel est soit userOneEmail, soit userTwoEmail.
     */
    @Query("SELECT * FROM friendships WHERE userOneEmail = :currentUserEmail OR userTwoEmail = :currentUserEmail")
    fun getAllFriendships(currentUserEmail: String): Flow<List<Friendship>>

    /** Insère ou remplace une amitié. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: Friendship)

    /** Met à jour une amitié existante. */
    @Update
    suspend fun updateFriendship(friendship: Friendship)

    /** Supprime une amitié spécifique entre deux utilisateurs. */
    @Query("DELETE FROM friendships WHERE (userOneEmail = :user1 AND userTwoEmail = :user2) OR (userOneEmail = :user2 AND userTwoEmail = :user1)")
    suspend fun deleteFriendship(user1: String, user2: String)


    // --- Requêtes MESSAGE ---

    /** Récupère tous les messages pour un ID de conversation donné. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>>

    /** Insère un nouveau message (remplace si existe déjà). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    /** Supprime tous les messages d'une conversation. */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}