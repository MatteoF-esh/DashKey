package com.example.testmessagesimple

import androidx.room.Entity

enum class FriendshipStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

/**
 * Représente la relation d'amitié entre deux utilisateurs.
 * Pour garantir l'unicité (A->B est identique à B->A), la convention est que
 * userOneEmail est toujours l'email lexicographiquement inférieur.
 *
 * @property userOneEmail Le premier utilisateur dans la relation (email trié).
 * @property userTwoEmail Le second utilisateur dans la relation (email trié).
 * @property status Le statut actuel de la relation.
 * @property initiatorUserId L'email de l'utilisateur qui a envoyé la demande.
 */
@Entity(tableName = "friendships", primaryKeys = ["userOneEmail", "userTwoEmail"])
data class Friendship(
    val userOneEmail: String,
    val userTwoEmail: String,
    val status: FriendshipStatus,
    val initiatorUserId: String
)