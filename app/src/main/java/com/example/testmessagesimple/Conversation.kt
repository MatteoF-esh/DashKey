// Fichier : app/src/main/java/com/example/testmessagesimple/Conversation.kt

package com.example.testmessagesimple

/**
 * Représente une conversation avec un ami spécifique.
 * @property friendId L'identifiant de l'ami (son e-mail).
 * @property messages La liste des messages dans cette conversation.
 */
data class Conversation(
    val friendId: String,
    val messages: List<Message>
)
