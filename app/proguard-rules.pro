##################################################
# BASIC ATTRIBUTES
##################################################
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

##################################################
# KOTLIN
##################################################
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

##################################################
# ROOM DATABASE
##################################################
-keep class androidx.room.RoomDatabase
-keep @androidx.room.* class * { *; }
-dontwarn androidx.room.**

##################################################
# GLIDE
##################################################
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.**

##################################################
# OKHTTP
##################################################
-dontwarn okhttp3.**
-dontwarn okio.**

##################################################
# ADMOB / PLAY SERVICES ADS
##################################################
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.android.gms.internal.ads.**

##################################################
# START.IO SDK
##################################################
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**

##################################################
# MEDIA3 EXOPLAYER
##################################################
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

##################################################
# LOTTIE
##################################################
-dontwarn com.airbnb.lottie.**

##################################################
# JSoup
##################################################
-dontwarn org.jsoup.**

##################################################
# SHIMMER
##################################################
-dontwarn com.facebook.shimmer.**

##################################################
# MODEL DATA APP
##################################################
-keep class com.afitech.afitechtok.data.model.** { *; }

##################################################
# PARCELABLE
##################################################
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

##################################################
# REMOVE LOG IN RELEASE
##################################################
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}