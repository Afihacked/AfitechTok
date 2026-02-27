# ==================================================
# ✅ BASIC ATTRIBUTES (WAJIB)
# ==================================================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses,EnclosingMethod

# ==================================================
# ✅ KOTLIN
# ==================================================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ==================================================
# ✅ ROOM DATABASE (SAFE & MINIMAL)
# ==================================================
-keep class androidx.room.RoomDatabase
-keep @androidx.room.* class * { *; }
-dontwarn androidx.room.**

# ==================================================
# ✅ ANDROIDX & MATERIAL (LEAN)
# ==================================================
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# ❗ tidak perlu keep seluruh package (biar shrink maksimal)

# ==================================================
# ✅ GLIDE (WAJIB)
# ==================================================
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule

-dontwarn com.bumptech.glide.**

# ==================================================
# ✅ OKHTTP
# ==================================================
-dontwarn okhttp3.**
-dontwarn okio.**

# (tidak perlu keep semua class → biar kecil)

# ==================================================
# ✅ ADMOB / PLAY SERVICES
# ==================================================
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.gms.internal.ads.**

# ==================================================
# ✅ FIREBASE (MODERN SDK)
# ==================================================
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# Crashlytics mapping
-keepattributes SourceFile,LineNumberTable

# ==================================================
# ✅ FIREBASE PERFORMANCE
# ==================================================
-dontwarn com.google.firebase.perf.**

# ==================================================
# ✅ START.IO SDK
# ==================================================
-dontwarn com.startapp.**
-keep class com.startapp.** { *; }

# ==================================================
# ✅ MEDIA3 EXOPLAYER
# ==================================================
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# ==================================================
# ✅ JSoup
# ==================================================
-dontwarn org.jsoup.**

# ==================================================
# ✅ LOTTIE
# ==================================================
-dontwarn com.airbnb.lottie.**

# ==================================================
# ✅ SHIMMER
# ==================================================
-dontwarn com.facebook.shimmer.**

# ==================================================
# ✅ MODEL JSON APP KAMU
# ==================================================
-keep class com.afitech.afitechtok.data.model.** { *; }

# ==================================================
# ✅ PARCELABLE / SERIALIZABLE
# ==================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keep class * implements java.io.Serializable { *; }
-keepnames class * implements java.io.Serializable

# ==================================================
# ✅ REMOVE LOG DI RELEASE (GOOD)
# ==================================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ==================================================
# ✅ R8 OPTIMIZATION (SAFE)
# ==================================================
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# ==================================================
# ✅ TESTING (IGNORE)
# ==================================================
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn androidx.test.**
