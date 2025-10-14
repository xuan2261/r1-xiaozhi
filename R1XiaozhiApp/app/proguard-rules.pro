# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep all classes in our package
-keep class com.phicomm.r1.xiaozhi.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Timber
-dontwarn org.jetbrains.annotations.**

# NanoHTTPD
-keep class org.nanohttpd.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep service classes
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep audio recording classes
-keep class android.media.AudioRecord { *; }
-keep class android.media.MediaPlayer { *; }