# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the Proguard
# files in build.gradle.

# Keep Retrofit / Gson data models and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gson rules
-keepattributes Signature
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Coil
-dontwarn io.coil-kt.**
