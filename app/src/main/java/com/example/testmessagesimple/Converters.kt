// Fichier : app/src/main/java/com/example/testmessagesimple/Converters.kt

package com.example.testmessagesimple

import androidx.room.TypeConverter

/**
 * Fournit les méthodes pour convertir les types non supportés par Room (comme les enums)
 * vers et depuis des types primitifs (comme String) qui peuvent être stockés.
 */
class Converters {
    /** Convertit un statut d'amitié en chaîne de caractères pour le stockage. */
    @TypeConverter
    fun fromStatus(status: FriendshipStatus): String {
        return status.name
    }

    /** Convertit une chaîne de caractères en statut d'amitié lors de la lecture. */
    @TypeConverter
    fun toStatus(statusName: String): FriendshipStatus {
        return FriendshipStatus.valueOf(statusName)
    }
}