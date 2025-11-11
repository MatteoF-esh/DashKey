package com.example.testmessagesimple

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Représente la structure de données pour un seul message stocké localement.
 * Les messages sont stockés UNIQUEMENT sur le téléphone, pas dans la BDD serveur (sauf si destinataire offline).
 * @property id ID local unique du message
 * @property senderId ID de l'utilisateur qui a envoyé le message
 * @property receiverId ID de l'utilisateur qui reçoit le message
 * @property senderEmail Email de l'expéditeur (pour affichage)
 * @property text Contenu du message
 * @property timestamp Horodatage du message
 * @property conversationId ID de la conversation (pour lier aux amitiés)
 * @property isSentByMe True si le message a été envoyé par l'utilisateur actuel
 * @property serverMessageId ID du message côté serveur (null si livraison directe)
 * @property fromServer True si le message provient du serveur (était offline)
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderId: Int,
    val receiverId: Int,
    val senderEmail: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String,
    val isSentByMe: Boolean = false,
    val serverMessageId: Int? = null,
    val fromServer: Boolean = false
)