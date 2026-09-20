# -----------------------------------------------------------------------------
# 3D LEDGER ANTI-REVERSE ENGINEERING & R8 COMPILATION HARDENING RULES
# -----------------------------------------------------------------------------

# 1. Obfuscation & Package Flattening
-repackageclasses 'com.threeDLedger.obf'
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute ""

# 2. Strip Debug Information and Source File metadata
# Drops LineNumberTable and LocalVariableTable to prevent decompilers from reconstructing variable names
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 3. Strip All Debug Logging in Production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 4. Entry Points (Activities, Services, Application)
-keep public class com.threeDLedger.MainActivity { *; }
-keep public class com.threeDLedger.logic.LotteryMessagingService { *; }

# 5. Serialization & Network Models (Preserve JSON field names for Moshi/Retrofit)
-keepclassmembers class com.threeDLedger.network.** {
    <fields>;
}
-keep class com.threeDLedger.network.** { *; }
-dontwarn com.threeDLedger.network.**

# 6. Room Database & Local Entities
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.threeDLedger.data.** { *; }
-keepclassmembers class com.threeDLedger.data.** {
    <fields>;
}

# 7. Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 8. Retrofit / OkHttp / Okio / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}

# 9. Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-dontwarn kotlinx.coroutines.**

# 10. Firebase & Google Services
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.database.** { *; }

# 11. Anti-Reverse Engineering & Output Stripping
-assumenosideeffects class java.lang.System {
    public static void println(...);
    public static void print(...);
}
-flattenpackagehierarchy 'com.threeDLedger.obf'
-optimizationpasses 5

# -----------------------------------------------------------------------------
