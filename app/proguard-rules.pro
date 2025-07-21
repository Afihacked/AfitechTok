# =============================
# ✅ Anotasi, Debug Info
# =============================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# =============================
# ✅ Room Database
# =============================
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-dontwarn androidx.room.**

# =============================
# ✅ Glide
# =============================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.Glide
-keep class com.bumptech.glide.RequestManager
-dontwarn com.bumptech.glide.**

# =============================
# ✅ Lottie (animasi)
# =============================
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# =============================
# ✅ OkHttp (untuk jaringan)
# =============================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# =============================
# ✅ AdMob / Google Ads
# =============================
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# =============================
# ✅ Media3 / ExoPlayer
# =============================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# =============================
# ✅ AndroidX Core / AppCompat / Material
# =============================
-keep class androidx.core.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn androidx.core.**
-dontwarn androidx.appcompat.**
-dontwarn com.google.android.material.**

# =============================
# ✅ JSON Model (Gson/Moshi/Manual parsing)
# =============================
-keep class com.afitech.sosmedtoolkit.data.model.** { *; }

# =============================
# ✅ Parcelable / Serializable
# =============================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep class * implements java.io.Serializable { *; }
-keepnames class * implements java.io.Serializable

# =============================
# ✅ Unit Testing
# =============================
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn androidx.test.**
-dontwarn androidx.test.espresso.**
-dontwarn androidx.test.ext.**

# =============================
# ✅ Ignore warning umum
# =============================
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit

# =============================
# ✅ Hapus Log Release
# =============================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# =============================
# ✅ Optimasi ProGuard
# =============================
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
