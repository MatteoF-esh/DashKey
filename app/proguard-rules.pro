# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================================================
# SÉCURITÉ - Obfuscation et Protection du Code
# ============================================================================

# Activer l'obfuscation aggressive
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Masquer les noms de fichiers source (anti reverse-engineering)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ============================================================================
# PROTECTION DES CLASSES CRYPTOGRAPHIQUES
# ============================================================================

# Protéger les classes de cryptographie (obfusquer les noms mais garder la logique)
-keep class com.example.testmessagesimple.utils.CryptoManager { *; }
-keep class com.example.testmessagesimple.utils.HybridCryptoUtils { *; }
-keep class com.example.testmessagesimple.utils.HybridEncryptedData { *; }
-keep class com.example.testmessagesimple.utils.TokenManager { *; }

# Obfusquer les méthodes mais garder les signatures publiques
-keepclassmembernames class com.example.testmessagesimple.utils.** { *; }

# ============================================================================
# RETROFIT & NETWORKING
# ============================================================================

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Garder les modèles de données API
-keep class com.example.testmessagesimple.data.** { *; }

# ============================================================================
# ROOM DATABASE
# ============================================================================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# ============================================================================
# SOCKET.IO
# ============================================================================

-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# ============================================================================
# JETPACK COMPOSE
# ============================================================================

-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ============================================================================
# KOTLIN
# ============================================================================

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================================================
# ANDROID SECURITY
# ============================================================================

# Android Keystore (ne pas obfusquer)
-keep class android.security.** { *; }
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }

# ============================================================================
# ANTI-DEBUGGING & ANTI-TAMPERING
# ============================================================================

# Supprimer les logs en production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Supprimer les print statements
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static *** println(...);
    public static *** print(...);
}

# ============================================================================
# GARDER LES CLASSES NÉCESSAIRES
# ============================================================================

# Garder les ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# Garder les Activity et Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.fragment.app.Fragment

# ============================================================================
# WARNINGS À IGNORER
# ============================================================================

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
