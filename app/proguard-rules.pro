# -----------------------------------------------------------------------------
# 3D LEDGER R8 OPTIMIZATION & HARDENING RULES
# -----------------------------------------------------------------------------

# 1. Obfuscation & Package Flattening
-repackageclasses 'com.threeDLedger.obf'
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute ""

# 2. Strip Debug Information and Source File metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 3. Strip All Debug Logging in Production
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 4. Entry Points (Activities, Services)
-keep public class com.threeDLedger.MainActivity { *; }
-keep public class com.threeDLedger.logic.LotteryMessagingService { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 5. Serialization & Network Models (Preserve JSON field names for Moshi/Retrofit/Serialization)
-keepclassmembers class com.threeDLedger.network.** {
    <fields>;
}
-keep class com.threeDLedger.network.** { *; }
-dontwarn com.threeDLedger.network.**

-keep,includedescriptorclasses class com.threeDLedger.**$$serializer { *; }
-keepclassmembers class com.threeDLedger.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.threeDLedger.** { *; }

# 6. Room Database & Local Entities (Room AAR bundles its own consumer rules)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.threeDLedger.data.** { *; }
-keepclassmembers class com.threeDLedger.data.** {
    <fields>;
}

# 7. Compose & Libraries (Use AAR consumer rules so R8 strips unused icons/classes!)
-dontwarn androidx.compose.**
-dontwarn androidx.room.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.firebase.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}

# 8. Output & Debug Stripping
-assumenosideeffects class java.lang.System {
    public static void println(...);
    public static void print(...);
}
-optimizationpasses 3
