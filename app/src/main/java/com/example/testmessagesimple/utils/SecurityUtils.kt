package com.example.testmessagesimple.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * SecurityUtils - Utilitaires de sécurité pour détecter les environnements non sécurisés
 *
 * Fonctionnalités :
 * - Détection de root/jailbreak
 * - Détection d'émulateur
 * - Détection de débogage
 * - Détection de frameworks de hooking (Frida, Xposed)
 */
object SecurityUtils {

    private const val TAG = "SecurityUtils"

    /**
     * Vérifie si l'appareil est rooté
     * Un appareil rooté compromet la sécurité de l'Android Keystore
     */
    fun isDeviceRooted(): Boolean {
        return checkRootBuildTags() || checkRootFiles() || checkSuBinary()
    }

    /**
     * Vérifie les build tags de test
     */
    private fun checkRootBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Vérifie la présence de fichiers/apps typiques de root
     */
    private fun checkRootFiles(): Boolean {
        val rootFiles = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/Magisk.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        return rootFiles.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Tente d'exécuter la commande 'su'
     */
    private fun checkSuBinary(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            val result = reader.readText()
            reader.close()
            result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vérifie si l'application est en cours de débogage
     * Peut indiquer une tentative de reverse engineering
     */
    fun isBeingDebugged(context: Context): Boolean {
        return android.os.Debug.isDebuggerConnected() ||
               android.os.Debug.waitingForDebugger() ||
               (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Vérifie si l'app tourne sur un émulateur
     * Les émulateurs sont moins sécurisés que les vrais appareils
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Vérifie la présence de frameworks de hooking (Frida, Xposed)
     * Ces outils permettent de modifier le comportement de l'app en temps réel
     */
    fun isHookingFrameworkDetected(): Boolean {
        return checkFrida() || checkXposed()
    }

    /**
     * Détecte Frida (outil de hooking dynamique)
     */
    private fun checkFrida(): Boolean {
        val fridaLibraries = arrayOf(
            "frida-agent",
            "frida-gadget",
            "frida-server"
        )

        return try {
            val process = Runtime.getRuntime().exec("ps")
            val reader = process.inputStream.bufferedReader()
            val processes = reader.readText()
            reader.close()

            fridaLibraries.any { lib -> processes.contains(lib, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Détecte Xposed Framework
     */
    private fun checkXposed(): Boolean {
        return try {
            // Xposed ajoute des hooks détectables
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Vérifie l'intégrité de l'APK (détecte le repackaging)
     */
    fun checkApkIntegrity(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )

            // Vérifier la signature (vous devez remplacer par votre vraie signature)
            val signatures = packageInfo.signatures
            if (signatures == null || signatures.isEmpty()) {
                Log.w(TAG, "⚠️ Aucune signature trouvée")
                return false
            }

            // En production, comparer avec votre signature réelle
            // Pour l'instant, on vérifie juste qu'elle existe
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la vérification d'intégrité", e)
            false
        }
    }

    /**
     * Effectue une vérification de sécurité complète
     * Retourne un rapport de sécurité
     */
    fun performSecurityCheck(context: Context): SecurityReport {
        val isRooted = isDeviceRooted()
        val isDebugged = isBeingDebugged(context)
        val isEmulator = isEmulator()
        val isHooked = isHookingFrameworkDetected()
        val hasValidSignature = checkApkIntegrity(context)

        if (isRooted) Log.w(TAG, "⚠️ SÉCURITÉ : Appareil rooté détecté")
        if (isDebugged) Log.w(TAG, "⚠️ SÉCURITÉ : Débogage actif détecté")
        if (isEmulator) Log.w(TAG, "⚠️ SÉCURITÉ : Émulateur détecté")
        if (isHooked) Log.w(TAG, "⚠️ SÉCURITÉ : Framework de hooking détecté")
        if (!hasValidSignature) Log.w(TAG, "⚠️ SÉCURITÉ : Signature invalide")

        return SecurityReport(
            isRooted = isRooted,
            isDebugged = isDebugged,
            isEmulator = isEmulator,
            isHooked = isHooked,
            hasValidSignature = hasValidSignature
        )
    }

    /**
     * Détermine si l'environnement est sécurisé
     */
    fun isSecureEnvironment(context: Context): Boolean {
        val report = performSecurityCheck(context)
        return !report.isRooted &&
               !report.isDebugged &&
               !report.isHooked &&
               report.hasValidSignature
    }
}

/**
 * Rapport de sécurité contenant tous les résultats de vérification
 */
data class SecurityReport(
    val isRooted: Boolean,
    val isDebugged: Boolean,
    val isEmulator: Boolean,
    val isHooked: Boolean,
    val hasValidSignature: Boolean
) {
    val isSafe: Boolean
        get() = !isRooted && !isDebugged && !isHooked && hasValidSignature

    val threatLevel: ThreatLevel
        get() = when {
            isRooted || isHooked -> ThreatLevel.CRITICAL
            isDebugged -> ThreatLevel.HIGH
            isEmulator -> ThreatLevel.MEDIUM
            !hasValidSignature -> ThreatLevel.HIGH
            else -> ThreatLevel.LOW
        }
}

enum class ThreatLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

