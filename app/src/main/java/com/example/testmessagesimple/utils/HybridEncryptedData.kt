package com.example.testmessagesimple.utils

import android.util.Base64

/**
 * Structure de données pour stocker les résultats du chiffrement hybride
 *
 * @property encryptedAesData Les données chiffrées avec AES-GCM
 * @property encryptedAesKey La clé AES chiffrée avec RSA
 * @property iv Le vecteur d'initialisation (IV/Nonce) utilisé pour AES-GCM
 */
data class HybridEncryptedData(
    val encryptedAesData: ByteArray,
    val encryptedAesKey: ByteArray,
    val iv: ByteArray
) {
    /**
     * Convertit en format Base64 pour le transport/stockage
     */
    fun toBase64String(): String {
        val encryptedDataB64 = Base64.encodeToString(encryptedAesData, Base64.NO_WRAP)
        val encryptedKeyB64 = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        return "$encryptedDataB64:$encryptedKeyB64:$ivB64"
    }

    companion object {
        /**
         * Reconstitue l'objet depuis une chaîne Base64
         */
        fun fromBase64String(base64String: String): HybridEncryptedData {
            val parts = base64String.split(":")
            require(parts.size == 3) { "Format invalide pour HybridEncryptedData" }

            return HybridEncryptedData(
                encryptedAesData = Base64.decode(parts[0], Base64.NO_WRAP),
                encryptedAesKey = Base64.decode(parts[1], Base64.NO_WRAP),
                iv = Base64.decode(parts[2], Base64.NO_WRAP)
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HybridEncryptedData

        if (!encryptedAesData.contentEquals(other.encryptedAesData)) return false
        if (!encryptedAesKey.contentEquals(other.encryptedAesKey)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptedAesData.contentHashCode()
        result = 31 * result + encryptedAesKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

