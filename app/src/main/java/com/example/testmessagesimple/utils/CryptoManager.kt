package com.example.testmessagesimple.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

/**
 * CryptoManager gère le chiffrement E2EE avec RSA
 * - Génère et stocke les clés RSA dans Android Keystore
 * - Chiffre et déchiffre les messages
 */
class CryptoManager(private val context: Context) {

    companion object {
        private const val TAG = "CryptoManager"
        private const val KEY_ALIAS_PREFIX = "DashKeyE2EEKey_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALGORITHM = "RSA"
        private const val CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_SIZE = 2048

        // Prefs pour stocker la clé publique au format String
        private const val PREFS_NAME = "crypto_prefs"
        private const val KEY_PUBLIC_KEY_PREFIX = "public_key_string_"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // L'ID de l'utilisateur actuel pour isoler les clés
    private var currentUserId: Int? = null

    /**
     * Définit l'utilisateur actuel pour isoler ses clés
     */
    fun setCurrentUser(userId: Int) {
        Log.d(TAG, "🔑 Définition de l'utilisateur actuel: $userId")
        currentUserId = userId
        sharedPrefs.edit().putInt(KEY_CURRENT_USER_ID, userId).apply()
        val keyAlias = getKeyAlias()
        Log.d(TAG, "🔑 Alias de clé pour cet utilisateur: $keyAlias")
    }

    /**
     * Récupère l'alias de clé spécifique à l'utilisateur
     */
    private fun getKeyAlias(): String {
        val userId = currentUserId ?: sharedPrefs.getInt(KEY_CURRENT_USER_ID, -1)
        if (userId == -1) {
            throw IllegalStateException("Aucun utilisateur défini. Appelez setCurrentUser() d'abord.")
        }
        return "$KEY_ALIAS_PREFIX$userId"
    }

    /**
     * Récupère la clé de préférence pour la clé publique
     */
    private fun getPublicKeyPrefKey(): String {
        val userId = currentUserId ?: sharedPrefs.getInt(KEY_CURRENT_USER_ID, -1)
        if (userId == -1) {
            throw IllegalStateException("Aucun utilisateur défini. Appelez setCurrentUser() d'abord.")
        }
        return "$KEY_PUBLIC_KEY_PREFIX$userId"
    }

    /**
     * Initialise les clés RSA. Si elles n'existent pas, elles sont générées.
     * @return La clé publique au format Base64 pour l'envoyer au serveur
     */
    fun initializeKeys(): String {
        val keyAlias = getKeyAlias()
        Log.d(TAG, "🔑 Initialisation des clés pour l'alias: $keyAlias")

        // Vérifier si les clés existent déjà
        if (!keyStore.containsAlias(keyAlias)) {
            Log.d(TAG, "🔑 Génération d'une nouvelle paire de clés RSA pour l'utilisateur $currentUserId")
            generateKeyPair()
        } else {
            Log.d(TAG, "🔑 Clés RSA existantes trouvées pour l'utilisateur $currentUserId")
        }

        val publicKey = getPublicKeyString()
        Log.d(TAG, "🔑 Clé publique récupérée (${publicKey.length} caractères): ${publicKey.take(50)}...")
        return publicKey
    }

    /**
     * Génère une nouvelle paire de clés RSA et la stocke dans Android Keystore
     *
     * SÉCURITÉ :
     * - La clé PRIVÉE est stockée dans Android Keystore (chiffrée, ne quitte jamais l'appareil)
     * - La clé PUBLIQUE est sauvegardée en SharedPreferences et sera envoyée au serveur
     */
    private fun generateKeyPair() {
        try {
            val keyAlias = getKeyAlias()
            Log.d(TAG, "🔐 Génération d'une paire de clés RSA pour l'alias: $keyAlias")

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )

            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).apply {
                setKeySize(KEY_SIZE)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            }.build()

            keyPairGenerator.initialize(parameterSpec)
            val keyPair = keyPairGenerator.generateKeyPair()

            // Vérifier que la clé privée a bien été créée
            Log.d(TAG, "🔒 CLÉ PRIVÉE générée et stockée dans Android Keystore (JAMAIS exportée)")
            Log.d(TAG, "🔒 Algorithme: ${keyPair.private.algorithm}, Format: ${keyPair.private.format}")

            // Sauvegarder la clé publique au format String
            val publicKeyString = publicKeyToString(keyPair.public)
            val prefKey = getPublicKeyPrefKey()
            sharedPrefs.edit().putString(prefKey, publicKeyString).apply()

            Log.d(TAG, "🔓 CLÉ PUBLIQUE sauvegardée (sera envoyée au serveur)")
            Log.d(TAG, "✅ Paire de clés RSA générée avec succès pour l'utilisateur $currentUserId")
            Log.d(TAG, "   - Clé privée: SÉCURISÉE dans Keystore (locale uniquement)")
            Log.d(TAG, "   - Clé publique: ${publicKeyString.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la génération des clés", e)
            throw e
        }
    }

    /**
     * Récupère la clé privée depuis le Keystore
     */
    private fun getPrivateKey(): PrivateKey {
        val keyAlias = getKeyAlias()
        val entry = keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    /**
     * Récupère la clé publique depuis le Keystore
     */
    private fun getPublicKey(): PublicKey {
        val keyAlias = getKeyAlias()
        val entry = keyStore.getEntry(keyAlias, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey
    }

    /**
     * Convertit une clé publique en String Base64
     */
    private fun publicKeyToString(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Récupère la clé publique au format String
     */
    fun getPublicKeyString(): String {
        val prefKey = getPublicKeyPrefKey()
        // Essayer d'abord depuis les SharedPreferences
        var publicKeyString = sharedPrefs.getString(prefKey, null)

        if (publicKeyString == null) {
            // Sinon, la récupérer depuis le Keystore
            val publicKey = getPublicKey()
            publicKeyString = publicKeyToString(publicKey)
            sharedPrefs.edit().putString(prefKey, publicKeyString).apply()
        }

        return publicKeyString
    }

    /**
     * Convertit une String Base64 en PublicKey
     */
    private fun stringToPublicKey(publicKeyString: String): PublicKey {
        val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
        val keyFactory = java.security.KeyFactory.getInstance(RSA_ALGORITHM)
        val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Chiffre un message avec la clé publique du destinataire
     * @param message Le message en clair
     * @param recipientPublicKey La clé publique du destinataire (format Base64)
     * @return Le message chiffré en Base64
     */
    fun encryptMessage(message: String, recipientPublicKey: String): String {
        try {
            val publicKey = stringToPublicKey(recipientPublicKey)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chiffrement du message", e)
            throw e
        }
    }

    /**
     * Déchiffre un message avec la clé privée de l'utilisateur
     * @param encryptedMessage Le message chiffré en Base64
     * @return Le message en clair
     */
    fun decryptMessage(encryptedMessage: String): String {
        try {
            val privateKey = getPrivateKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)

            val encryptedBytes = Base64.decode(encryptedMessage, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du déchiffrement du message", e)
            // En cas d'erreur, retourner le message tel quel (pour compatibilité avec anciens messages non chiffrés)
            return encryptedMessage
        }
    }

    /**
     * Supprime les clés du Keystore (utile pour les tests ou la déconnexion)
     */
    fun deleteKeys() {
        try {
            val keyAlias = getKeyAlias()
            val prefKey = getPublicKeyPrefKey()
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                sharedPrefs.edit().remove(prefKey).apply()
                Log.d(TAG, "Clés RSA supprimées pour l'utilisateur $currentUserId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression des clés", e)
        }
    }

    /**
     * Vérifie si les clés existent
     */
    fun hasKeys(): Boolean {
        return try {
            val keyAlias = getKeyAlias()
            keyStore.containsAlias(keyAlias)
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Vérifie si la clé privée est accessible dans le Keystore
     * Cette méthode confirme que la clé privée est bien stockée localement
     */
    fun hasPrivateKey(): Boolean {
        return try {
            val keyAlias = getKeyAlias()
            if (keyStore.containsAlias(keyAlias)) {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
                val hasPrivateKey = entry?.privateKey != null
                if (hasPrivateKey) {
                    Log.d(TAG, "✅ Clé privée trouvée dans Keystore pour l'utilisateur $currentUserId")
                } else {
                    Log.w(TAG, "⚠️ Alias trouvé mais clé privée manquante pour l'utilisateur $currentUserId")
                }
                hasPrivateKey
            } else {
                Log.d(TAG, "ℹ️ Aucune clé privée trouvée pour l'utilisateur $currentUserId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la vérification de la clé privée", e)
            false
        }
    }

    /**
     * Affiche un résumé de la configuration des clés pour l'utilisateur actuel
     * Utile pour le débogage et la vérification de la sécurité
     */
    fun logKeysSummary() {
        try {
            val keyAlias = getKeyAlias()
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "📊 RÉSUMÉ DES CLÉS - Utilisateur $currentUserId")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            if (keyStore.containsAlias(keyAlias)) {
                val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry

                if (entry != null) {
                    Log.d(TAG, "🔒 CLÉ PRIVÉE:")
                    Log.d(TAG, "   ✅ Présente dans Android Keystore (local uniquement)")
                    Log.d(TAG, "   📍 Alias: $keyAlias")
                    Log.d(TAG, "   🔐 Algorithme: ${entry.privateKey.algorithm}")
                    Log.d(TAG, "   🛡️ Format: ${entry.privateKey.format}")
                    Log.d(TAG, "   ⚠️ NON EXPORTABLE (sécurisé par le système)")

                    Log.d(TAG, "")
                    Log.d(TAG, "🔓 CLÉ PUBLIQUE:")
                    val publicKeyString = getPublicKeyString()
                    Log.d(TAG, "   ✅ Disponible")
                    Log.d(TAG, "   📏 Taille: ${publicKeyString.length} caractères")
                    Log.d(TAG, "   📤 Peut être partagée (envoyée au serveur)")
                    Log.d(TAG, "   🔑 Aperçu: ${publicKeyString.take(50)}...")
                } else {
                    Log.w(TAG, "⚠️ Alias trouvé mais entrée invalide")
                }
            } else {
                Log.d(TAG, "ℹ️ Aucune paire de clés n'existe pour cet utilisateur")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'affichage du résumé des clés", e)
        }
    }
}

