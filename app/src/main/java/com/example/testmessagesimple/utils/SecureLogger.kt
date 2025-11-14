package com.example.testmessagesimple.utils

import android.util.Log

/**
 * SecureLogger - Logger sécurisé qui désactive automatiquement les logs en production
 *
 * Usage :
 * SecureLogger.d(TAG, "Message de debug")  // Visible uniquement en DEBUG
 * SecureLogger.e(TAG, "Erreur critique")   // Toujours visible
 */
object SecureLogger {

    // En production (BuildConfig.DEBUG == false), seules les erreurs critiques sont loggées
    internal val ENABLE_DEBUG_LOGS = com.example.testmessagesimple.BuildConfig.DEBUG
    internal val ENABLE_INFO_LOGS = com.example.testmessagesimple.BuildConfig.DEBUG
    private const val ENABLE_WARNING_LOGS = true // Warnings toujours actifs
    private const val ENABLE_ERROR_LOGS = true   // Erreurs toujours actives

    /**
     * Log de debug (désactivé en production)
     */
    fun d(tag: String, message: String) {
        if (ENABLE_DEBUG_LOGS) {
            Log.d(tag, message)
        }
    }

    /**
     * Log d'information (désactivé en production)
     */
    fun i(tag: String, message: String) {
        if (ENABLE_INFO_LOGS) {
            Log.i(tag, message)
        }
    }

    /**
     * Log de warning (toujours actif)
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (ENABLE_WARNING_LOGS) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    /**
     * Log d'erreur (toujours actif)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (ENABLE_ERROR_LOGS) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    /**
     * Log verbose (désactivé en production)
     */
    fun v(tag: String, message: String) {
        if (ENABLE_DEBUG_LOGS) {
            Log.v(tag, message)
        }
    }

    /**
     * Log conditionnel - ne log que si la condition est vraie
     */
    internal inline fun logIf(condition: Boolean, tag: String, messageProvider: () -> String) {
        if (ENABLE_DEBUG_LOGS && condition) {
            Log.d(tag, messageProvider())
        }
    }

    /**
     * Log avec redaction automatique des données sensibles
     * Remplace les patterns sensibles par [REDACTED]
     */
    fun dSecure(tag: String, message: String) {
        if (ENABLE_DEBUG_LOGS) {
            val sanitized = sanitizeSensitiveData(message)
            Log.d(tag, sanitized)
        }
    }

    /**
     * Sanitize les données sensibles avant logging
     */
    private fun sanitizeSensitiveData(message: String): String {
        var sanitized = message

        // Masquer les tokens JWT
        sanitized = sanitized.replace(Regex("Bearer [A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]+"), "Bearer [REDACTED]")

        // Masquer les clés (publiques/privées)
        sanitized = sanitized.replace(Regex("[A-Za-z0-9+/]{100,}={0,2}"), "[KEY_REDACTED]")

        // Masquer les emails
        sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL_REDACTED]")

        // Masquer les mots de passe
        sanitized = sanitized.replace(Regex("password[\"']?:\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE), "password: [REDACTED]")

        return sanitized
    }
}

/**
 * Extension functions pour faciliter l'usage
 */
fun Any.logDebug(message: String) {
    SecureLogger.d(this::class.java.simpleName, message)
}

fun Any.logInfo(message: String) {
    SecureLogger.i(this::class.java.simpleName, message)
}

fun Any.logWarning(message: String, throwable: Throwable? = null) {
    SecureLogger.w(this::class.java.simpleName, message, throwable)
}

fun Any.logError(message: String, throwable: Throwable? = null) {
    SecureLogger.e(this::class.java.simpleName, message, throwable)
}

fun Any.logSecure(message: String) {
    SecureLogger.dSecure(this::class.java.simpleName, message)
}

