# ---- kotlinx.serialization -------------------------------------------------
# Keep the serializer for every @Serializable type; the plugin generates
# Companion serializers that R8 would otherwise strip.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dugan.agent.**$$serializer { *; }
-keepclassmembers class com.dugan.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.dugan.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp ----------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger ---------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * { @javax.inject.* <fields>; }
-keepclasseswithmembernames class * { @javax.inject.* <init>(...); }
-dontwarn com.google.errorprone.annotations.**

# ---- Android Telecom -------------------------------------------------------
# Bound by the system framework via reflection; never obfuscate these.
-keep class com.dugan.agent.domain.telecom.VoiceConnectionService { *; }
-keep class com.dugan.agent.domain.telecom.VoiceInCallService { *; }
-keep class com.dugan.agent.ui.call.CallActivity { *; }
-keep class com.dugan.agent.service.AgentForegroundService { *; }
-keep class com.dugan.agent.DuganApplication { *; }

# ---- Kotlin coroutines -----------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**

# ---- Firebase (only present when -Pdugan.firebase=true) ---------------------
-dontwarn com.google.firebase.**
