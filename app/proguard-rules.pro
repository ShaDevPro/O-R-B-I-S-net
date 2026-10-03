# ====================================================================
# ORBISNET - PRODUCTION PROGUARD & R8 HARDENING
# ====================================================================

# 1. Obfuscation & Security Hardening (Without breaking reflection)
-allowaccessmodification
-renamesourcefileattribute "SourceFile"
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# 2. Strip Logging & Debug Output in Release Builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 3. Preserve Enum Field Constants (for JSON string serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public *;
}

# 4. Jetpack Compose Compatibility Rules
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 5. AndroidX Startup & Initializers (Prevents StartupException at launch)
-keep class androidx.startup.** { *; }
-keep class * extends androidx.startup.Initializer {
    public <init>();
    *;
}

# 6. AndroidX WorkManager & Room Database (Fixes WorkDatabase InstantiationException)
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
    public *** clearAllTables();
    *;
}
-keep class **_Impl extends androidx.room.RoomDatabase {
    public <init>();
    *;
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

-keep class androidx.work.** {
    public <init>(...);
    *;
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
    *;
}

# 7. Kotlin Coroutines & ViewModel
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 8. Cryptography, KeyStore & Biometric Interfaces
-keep class androidx.biometric.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# 9. ZXing QR Code Engine & JSON
-keep class com.google.zxing.** { *; }
-dontwarn org.json.**
-keep class org.json.** { *; }

# 10. OkHttp & WebSocket (Nostr Relay connections)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# 11. Bouncy Castle Crypto Engine (BIP-340 Schnorr / secp256k1)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
