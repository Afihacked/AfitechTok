# -----------------------------
# ✅ Umum: Keep anotasi penting & metadata debug
# -----------------------------
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# -----------------------------
# ✅ Room Database
# -----------------------------
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-dontwarn androidx.room.**

# -----------------------------
# ✅ Material Components & AndroidX
# -----------------------------
-keep class com.google.android.material.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.constraintlayout.** { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.core.**
-dontwarn androidx.appcompat.**
-dontwarn androidx.constraintlayout.**

# -----------------------------
# ✅ Glide
# -----------------------------
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep class * extends com.bumptech.glide.module.LibraryGlideModule { *; }

# -----------------------------
# ✅ OkHttp / Retrofit (kalau pakai)
# -----------------------------
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# -----------------------------
# ✅ AdMob / Google Play Services
# -----------------------------
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# -----------------------------
# ✅ Testing (JUnit, Espresso)
# -----------------------------
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn androidx.test.**
-dontwarn androidx.test.espresso.**
-dontwarn androidx.test.ext.**

# -----------------------------
# ✅ Ignore warning umum
# -----------------------------
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit

# -----------------------------
# ✅ Hapus Log di Rilis
# -----------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# -----------------------------
# ✅ Optimasi tambahan
# -----------------------------
# Hapus metode/metode tak terpakai (kecuali beberapa yang sensitif)
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Jaga class turunan jika pakai reflection / Parcelable / Serializable
-keep class * implements java.io.Serializable { *; }
-keepnames class * implements java.io.Serializable

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# -----------------------------
# ✅ Keep model untuk JSON parsing (Gson/Moshi)
# -----------------------------
-keep class com.afitech.sosmedtoolkit.data.model.** { *; }
