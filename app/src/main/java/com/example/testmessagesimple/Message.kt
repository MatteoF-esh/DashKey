package com.example.testmessagesimple

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente la structure de données pour un seul message.
 * @property text Le contenu du message.
 * @property sender L'identifiant de l'expéditeur (ex: l'email).
 * @property timestamp L'heure à laquelle le message a été créé.
 * @property conversationId L'ID de la conversation, reliant ce message à une amitié.
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val sender: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String
)