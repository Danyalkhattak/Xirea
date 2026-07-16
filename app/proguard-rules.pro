# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ========================================
# Xirea AI Chat App - ProGuard Rules
# ========================================

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========================================
# JNI / Native Methods
# ========================================
# Keep all native methods and the classes that contain them
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the LlamaCpp class and its callback interface
-keep class com.dannyk.xirea.ai.LlamaCpp { *; }
-keep class com.dannyk.xirea.ai.LlamaCpp$* { *; }
-keep interface com.dannyk.xirea.ai.LlamaCpp$TokenCallback { *; }

# ========================================
# Room Database
# ========================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep data model classes
-keep class com.dannyk.xirea.data.model.** { *; }

# ========================================
# Kotlin Coroutines
# ========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ========================================
# Compose
# ========================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ========================================
# DataStore
# ========================================
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ========================================
# General Android
# ========================================
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Keep the download service
-keep class com.dannyk.xirea.service.DownloadService { *; }

# ========================================
# Optimization Settings
# ========================================
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ========================================
# Debugging (Comment out for production)
# ========================================
# -dontobfuscate