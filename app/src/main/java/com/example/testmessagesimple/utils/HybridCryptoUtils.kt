package com.example.testmessagesimple.utils

import android.util.Log
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HybridCryptoUtils - Système de chiffrement hybride RSA + AES-GCM
 *
 * Cette classe implémente un système de chiffrement hybride pour gérer des messages longs
 * et des fichiers, en combinant la sécurité de RSA avec la performance de AES.
 *
 * Architecture :
 * - Les données sont chiffrées avec AES-256-GCM (rapide, adapté aux grandes données)
 * - La clé AES est chiffrée avec RSA-OAEP (sécurisé pour l'échange de clés)
 * - Utilise les fonctions RSA existantes via des lambdas pour éviter toute régression
 *
 * ⚠️ IMPORTANT : Cette classe N'utilise PAS directement les fonctions RSA existantes,
 * mais les reçoit en paramètres via des lambdas pour une isolation complète.
 */
class HybridCryptoUtils {

    companion object {
        private const val TAG = "HybridCryptoUtils"

        // Configuration AES-GCM
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE = 256 // AES-256 pour une sécurité maximale
        private const val GCM_IV_SIZE = 12 // 12 bytes (96 bits) recommandé pour GCM
        private const val GCM_TAG_SIZE = 128 // 128 bits pour l'authentification

        // Configuration RSA-OAEP (pour le chiffrement de la clé AES)
        private const val RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        /**
         * Chiffre des données avec un système hybride RSA + AES-GCM
         *
         * Processus :
         * 1. Génère une clé de session AES-256 aléatoire
         * 2. Génère un IV (Nonce) aléatoire pour GCM
         * 3. Chiffre les données avec AES-256-GCM
         * 4. Chiffre la clé AES avec RSA-OAEP via la fonction rsaEncryptor fournie
         *
         * @param data Les données à chiffrer (peut être de grande taille)
         * @param rsaPublicKey La clé publique RSA du destinataire
         * @param rsaEncryptor Lambda qui encapsule la fonction encryptRsa existante
         *        Format attendu : (ByteArray, PublicKey) -> ByteArray
         * @return HybridEncryptedData contenant toutes les informations nécessaires au déchiffrement
         * @throws Exception si le chiffrement échoue
         */
        fun encryptDataHybrid(
            data: ByteArray,
            rsaPublicKey: PublicKey,
            rsaEncryptor: (ByteArray, PublicKey) -> ByteArray
        ): HybridEncryptedData {
            try {
                Log.d(TAG, "🔐 Début du chiffrement hybride (${data.size} octets)")

                // Étape 1 : Générer une clé AES-256 aléatoire
                val aesKey = generateAESKey()
                Log.d(TAG, "✅ Clé AES-256 générée")

                // Étape 2 : Générer un IV aléatoire pour GCM
                val iv = generateIV()
                Log.d(TAG, "✅ IV généré (${iv.size} octets)")

                // Étape 3 : Chiffrer les données avec AES-GCM
                val encryptedAesData = encryptWithAES(data, aesKey, iv)
                Log.d(TAG, "✅ Données chiffrées avec AES-GCM (${encryptedAesData.size} octets)")

                // Étape 4 : Chiffrer la clé AES avec RSA-OAEP
                // On utilise le rsaEncryptor fourni pour rester compatible avec l'existant
                val aesKeyBytes = aesKey.encoded
                val encryptedAesKey = encryptAESKeyWithRSA(aesKeyBytes, rsaPublicKey, rsaEncryptor)
                Log.d(TAG, "✅ Clé AES chiffrée avec RSA (${encryptedAesKey.size} octets)")

                Log.d(TAG, "✅ Chiffrement hybride terminé avec succès")

                return HybridEncryptedData(
                    encryptedAesData = encryptedAesData,
                    encryptedAesKey = encryptedAesKey,
                    iv = iv
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du chiffrement hybride", e)
                throw Exception("Échec du chiffrement hybride: ${e.message}", e)
            }
        }

        /**
         * Déchiffre des données chiffrées avec le système hybride
         *
         * Processus :
         * 1. Déchiffre la clé AES avec RSA via la fonction rsaDecryptor fournie
         * 2. Déchiffre les données avec AES-256-GCM en utilisant la clé et l'IV
         *
         * @param encryptedData Les données chiffrées (HybridEncryptedData)
         * @param rsaPrivateKey La clé privée RSA du destinataire
         * @param rsaDecryptor Lambda qui encapsule la fonction decryptRsa existante
         *        Format attendu : (ByteArray, PrivateKey) -> ByteArray
         * @return Les données déchiffrées en clair
         * @throws Exception si le déchiffrement échoue
         */
        fun decryptDataHybrid(
            encryptedData: HybridEncryptedData,
            rsaPrivateKey: PrivateKey,
            rsaDecryptor: (ByteArray, PrivateKey) -> ByteArray
        ): ByteArray {
            try {
                Log.d(TAG, "🔓 Début du déchiffrement hybride")

                // Étape 1 : Déchiffrer la clé AES avec RSA
                val aesKeyBytes = decryptAESKeyWithRSA(
                    encryptedData.encryptedAesKey,
                    rsaPrivateKey,
                    rsaDecryptor
                )
                val aesKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)
                Log.d(TAG, "✅ Clé AES déchiffrée avec RSA")

                // Étape 2 : Déchiffrer les données avec AES-GCM
                val decryptedData = decryptWithAES(
                    encryptedData.encryptedAesData,
                    aesKey,
                    encryptedData.iv
                )
                Log.d(TAG, "✅ Données déchiffrées avec AES-GCM (${decryptedData.size} octets)")

                Log.d(TAG, "✅ Déchiffrement hybride terminé avec succès")

                return decryptedData
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors du déchiffrement hybride", e)
                throw Exception("Échec du déchiffrement hybride: ${e.message}", e)
            }
        }

        // ========== Fonctions privées pour AES-GCM ==========

        /**
         * Génère une clé AES-256 aléatoire
         */
        private fun generateAESKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
            keyGenerator.init(AES_KEY_SIZE, SecureRandom())
            return keyGenerator.generateKey()
        }

        /**
         * Génère un IV aléatoire pour GCM
         */
        private fun generateIV(): ByteArray {
            val iv = ByteArray(GCM_IV_SIZE)
            SecureRandom().nextBytes(iv)
            return iv
        }

        /**
         * Chiffre des données avec AES-256-GCM
         */
        private fun encryptWithAES(data: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec)
            return cipher.doFinal(data)
        }

        /**
         * Déchiffre des données avec AES-256-GCM
         */
        private fun decryptWithAES(encryptedData: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec)
            return cipher.doFinal(encryptedData)
        }

        // ========== Fonctions privées pour RSA-OAEP ==========

        /**
         * Chiffre la clé AES avec RSA-OAEP
         * Utilise le rsaEncryptor fourni pour rester compatible avec les fonctions RSA existantes
         */
        private fun encryptAESKeyWithRSA(
            aesKeyBytes: ByteArray,
            publicKey: PublicKey,
            rsaEncryptor: (ByteArray, PublicKey) -> ByteArray
        ): ByteArray {
            // Option 1 : Utiliser le rsaEncryptor fourni (peut utiliser PKCS1Padding)
            // Cette option est privilégiée pour éviter toute régression
            return try {
                Log.d(TAG, "Tentative de chiffrement RSA avec fonction fournie...")
                rsaEncryptor(aesKeyBytes, publicKey)
            } catch (e: Exception) {
                Log.w(TAG, "Échec avec fonction fournie, tentative avec RSA-OAEP direct...", e)
                // Option 2 : Utiliser RSA-OAEP directement (plus sécurisé pour les clés)
                encryptWithRSAOAEP(aesKeyBytes, publicKey)
            }
        }

        /**
         * Déchiffre la clé AES avec RSA-OAEP
         * Utilise le rsaDecryptor fourni pour rester compatible avec les fonctions RSA existantes
         */
        private fun decryptAESKeyWithRSA(
            encryptedAesKey: ByteArray,
            privateKey: PrivateKey,
            rsaDecryptor: (ByteArray, PrivateKey) -> ByteArray
        ): ByteArray {
            // Option 1 : Utiliser le rsaDecryptor fourni (peut utiliser PKCS1Padding)
            return try {
                Log.d(TAG, "Tentative de déchiffrement RSA avec fonction fournie...")
                rsaDecryptor(encryptedAesKey, privateKey)
            } catch (e: Exception) {
                Log.w(TAG, "Échec avec fonction fournie, tentative avec RSA-OAEP direct...", e)
                // Option 2 : Utiliser RSA-OAEP directement
                decryptWithRSAOAEP(encryptedAesKey, privateKey)
            }
        }

        /**
         * Chiffre avec RSA-OAEP directement (fallback)
         */
        private fun encryptWithRSAOAEP(data: ByteArray, publicKey: PublicKey): ByteArray {
            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return cipher.doFinal(data)
        }

        /**
         * Déchiffre avec RSA-OAEP directement (fallback)
         */
        private fun decryptWithRSAOAEP(encryptedData: ByteArray, privateKey: PrivateKey): ByteArray {
            val cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return cipher.doFinal(encryptedData)
        }
    }
}

