package com.example.testmessagesimple.utils

import android.util.Log

/**
 * Exemple d'utilisation du système de chiffrement hybride
 *
 * Ce fichier montre comment utiliser le nouveau système de chiffrement hybride
 * pour chiffrer des messages longs et des fichiers.
 */
object HybridCryptoExample {

    private const val TAG = "HybridCryptoExample"

    /**
     * Exemple 1 : Chiffrer et déchiffrer un long message texte
     */
    fun exampleLongMessage(cryptoManager: CryptoManager, recipientPublicKey: String) {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "📝 EXEMPLE 1 : Message long")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Message long qui dépasserait la limite RSA classique
            val longMessage = """
                Ceci est un message très long qui dépasserait la limite de chiffrement RSA classique.
                Le chiffrement RSA standard (PKCS1Padding) ne peut chiffrer que des données dont la 
                taille est inférieure à la taille de la clé moins le padding (environ 245 octets pour RSA-2048).
                
                Avec le système hybride, nous pouvons chiffrer des messages de taille illimitée !
                
                Le système fonctionne ainsi :
                1. Génération d'une clé AES-256 aléatoire
                2. Chiffrement du message avec AES-256-GCM (rapide et sécurisé)
                3. Chiffrement de la clé AES avec RSA
                4. Transmission du tout ensemble
                
                Avantages :
                ✅ Peut chiffrer des messages de taille illimitée
                ✅ Performance optimale grâce à AES
                ✅ Sécurité maximale grâce à RSA pour l'échange de clés
                ✅ Authentification intégrée avec GCM
                ✅ Compatible avec l'implémentation RSA existante
            """.trimIndent()

            Log.d(TAG, "📏 Taille du message : ${longMessage.length} caractères")

            // Chiffrement
            Log.d(TAG, "🔐 Chiffrement du message...")
            val encryptedBase64 = cryptoManager.encryptLongText(longMessage, recipientPublicKey)
            Log.d(TAG, "✅ Message chiffré (${encryptedBase64.length} caractères)")

            // Déchiffrement
            Log.d(TAG, "🔓 Déchiffrement du message...")
            val decryptedMessage = cryptoManager.decryptLongText(encryptedBase64)
            Log.d(TAG, "✅ Message déchiffré (${decryptedMessage.length} caractères)")

            // Vérification
            if (longMessage == decryptedMessage) {
                Log.d(TAG, "✅ SUCCESS : Le message déchiffré correspond au message original !")
            } else {
                Log.e(TAG, "❌ ERREUR : Le message déchiffré ne correspond pas !")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur dans l'exemple de message long", e)
        }
    }

    /**
     * Exemple 2 : Chiffrer et déchiffrer un fichier
     */
    fun exampleFileEncryption(cryptoManager: CryptoManager, recipientPublicKey: String) {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "📁 EXEMPLE 2 : Chiffrement de fichier")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Simuler des données de fichier (par exemple, une image de 1MB)
            val fileSize = 1024 * 1024 // 1 MB
            val fileData = ByteArray(fileSize) { (it % 256).toByte() }
            Log.d(TAG, "📏 Taille du fichier simulé : ${fileSize / 1024} KB")

            // Chiffrement
            Log.d(TAG, "🔐 Chiffrement du fichier...")
            val startEncrypt = System.currentTimeMillis()
            val encryptedFile = cryptoManager.encryptFile(fileData, recipientPublicKey)
            val encryptTime = System.currentTimeMillis() - startEncrypt
            Log.d(TAG, "✅ Fichier chiffré en ${encryptTime}ms")
            Log.d(TAG, "   - Données chiffrées : ${encryptedFile.encryptedAesData.size} octets")
            Log.d(TAG, "   - Clé AES chiffrée : ${encryptedFile.encryptedAesKey.size} octets")
            Log.d(TAG, "   - IV : ${encryptedFile.iv.size} octets")

            // Déchiffrement
            Log.d(TAG, "🔓 Déchiffrement du fichier...")
            val startDecrypt = System.currentTimeMillis()
            val decryptedFile = cryptoManager.decryptFile(encryptedFile)
            val decryptTime = System.currentTimeMillis() - startDecrypt
            Log.d(TAG, "✅ Fichier déchiffré en ${decryptTime}ms")

            // Vérification
            if (fileData.contentEquals(decryptedFile)) {
                Log.d(TAG, "✅ SUCCESS : Le fichier déchiffré correspond au fichier original !")
                Log.d(TAG, "⚡ Performance : Chiffrement ${fileSize / encryptTime} KB/s, " +
                        "Déchiffrement ${fileSize / decryptTime} KB/s")
            } else {
                Log.e(TAG, "❌ ERREUR : Le fichier déchiffré ne correspond pas !")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur dans l'exemple de chiffrement de fichier", e)
        }
    }

    /**
     * Exemple 3 : Utilisation directe de HybridCryptoUtils avec des lambdas personnalisés
     */
    fun exampleDirectUsage(cryptoManager: CryptoManager, recipientPublicKey: String) {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔧 EXEMPLE 3 : Utilisation directe")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            val testData = "Message de test pour utilisation directe".toByteArray()
            Log.d(TAG, "📝 Données : ${String(testData)}")

            // Convertir la clé publique
            val publicKey = cryptoManager.javaClass
                .getDeclaredMethod("stringToPublicKey", String::class.java)
                .apply { isAccessible = true }
                .invoke(cryptoManager, recipientPublicKey) as java.security.PublicKey

            // Récupérer la clé privée
            val privateKey = cryptoManager.javaClass
                .getDeclaredMethod("getPrivateKey")
                .apply { isAccessible = true }
                .invoke(cryptoManager) as java.security.PrivateKey

            // Créer des lambdas qui utilisent les fonctions existantes
            val rsaEncryptor: (ByteArray, java.security.PublicKey) -> ByteArray = { data, key ->
                val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
                cipher.doFinal(data)
            }

            val rsaDecryptor: (ByteArray, java.security.PrivateKey) -> ByteArray = { data, key ->
                val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key)
                cipher.doFinal(data)
            }

            // Chiffrement
            Log.d(TAG, "🔐 Chiffrement...")
            val encrypted = HybridCryptoUtils.encryptDataHybrid(testData, publicKey, rsaEncryptor)
            Log.d(TAG, "✅ Chiffré")

            // Déchiffrement
            Log.d(TAG, "🔓 Déchiffrement...")
            val decrypted = HybridCryptoUtils.decryptDataHybrid(encrypted, privateKey, rsaDecryptor)
            Log.d(TAG, "✅ Déchiffré : ${String(decrypted)}")

            // Vérification
            if (testData.contentEquals(decrypted)) {
                Log.d(TAG, "✅ SUCCESS : Utilisation directe fonctionne correctement !")
            } else {
                Log.e(TAG, "❌ ERREUR : Les données ne correspondent pas !")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur dans l'exemple d'utilisation directe", e)
        }
    }

    /**
     * Exécute tous les exemples
     */
    fun runAllExamples(cryptoManager: CryptoManager, recipientPublicKey: String) {
        Log.d(TAG, "")
        Log.d(TAG, "╔════════════════════════════════════════════╗")
        Log.d(TAG, "║  EXEMPLES DE CHIFFREMENT HYBRIDE RSA+AES   ║")
        Log.d(TAG, "╚════════════════════════════════════════════╝")
        Log.d(TAG, "")

        exampleLongMessage(cryptoManager, recipientPublicKey)
        Log.d(TAG, "")

        exampleFileEncryption(cryptoManager, recipientPublicKey)
        Log.d(TAG, "")

        exampleDirectUsage(cryptoManager, recipientPublicKey)
        Log.d(TAG, "")

        Log.d(TAG, "╔════════════════════════════════════════════╗")
        Log.d(TAG, "║         TOUS LES EXEMPLES TERMINÉS         ║")
        Log.d(TAG, "╚════════════════════════════════════════════╝")
    }
}

