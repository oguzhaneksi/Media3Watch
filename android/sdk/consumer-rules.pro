# ============================================================
# Media3Watch SDK — Consumer ProGuard Rules
#
# These rules are applied to the consuming application's
# R8/ProGuard pass. They protect the classes and members
# that kotlinx.serialization, OkHttp, and the SDK's own
# public API rely on at runtime.
# ============================================================

# ── kotlinx.serialization ───────────────────────────────────
# Keep the serialization marker annotation itself.
-keepattributes *Annotation*, InnerClasses

# Keep all @Serializable-annotated classes AND their
# generated $serializer companion / nested class.
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName <fields>;
}

-keep,includedescriptorclasses class **$$serializer { *; }

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# Keep the Serializable annotation so the serialization
# plugin's intrinsic checks work at runtime.
-keep @kotlinx.serialization.Serializable class * { *; }

# Serializer lookup via companion object
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── OkHttp ──────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── SDK Public API ───────────────────────────────────────────
-keep public class com.media3watch.sdk.Media3WatchAnalytics { *; }
-keep public class com.media3watch.sdk.Media3WatchConfig { *; }
-keep public interface com.media3watch.sdk.MetricsObserver { *; }
-keep public class com.media3watch.sdk.model.SessionSnapshot { *; }
-keep public enum com.media3watch.sdk.model.SessionPlaybackState { *; }
