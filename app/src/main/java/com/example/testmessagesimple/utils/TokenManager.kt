package com.example.testmessagesimple.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.testmessagesimple.data.UserInfo

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = try {
        // Créer une MasterKey sécurisée pour le chiffrement
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Utiliser EncryptedSharedPreferences pour chiffrer les données sensibles
        EncryptedSharedPreferences.create(
            context,
            "auth_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("TokenManager", "❌ Erreur lors de la création des SharedPreferences chiffrées", e)
        // Fallback sur SharedPreferences classiques (pour compatibilité)
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }

    fun saveAuthData(token: String, user: UserInfo) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_USER_EMAIL, user.email)
            .apply()
    }

    fun getAuthData(): Pair<String, UserInfo>? {
        val token = prefs.getString(KEY_TOKEN, null)
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val userEmail = prefs.getString(KEY_USER_EMAIL, null)

        return if (token != null && userId != -1 && userEmail != null) {
            token to UserInfo(id = userId, email = userEmail, roles = emptyList())
        } else {
            null
        }
    }

    fun clearData() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun saveLockoutUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_LOCKOUT_UNTIL, timestamp).apply()
    }

    fun getLockoutUntil(): Long {
        return prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
    }

    fun clearLockout() {
        prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
    }
}