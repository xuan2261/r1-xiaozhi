# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep all classes in our package
-keep class com.phicomm.r1.xiaozhi.** { *; }
-keepclassmembers class com.phicomm.r1.xiaozhi.** { *; }

# Android Support Libraries
-keep class android.support.** { *; }
-keep interface android.support.** { *; }
-dontwarn android.support.**

# Java-WebSocket library
-keep class org.java_websocket.** { *; }
-keep interface org.java_websocket.** { *; }
-keepclassmembers class * extends org.java_websocket.client.WebSocketClient {
    <methods>;
}
-dontwarn org.java_websocket.**

# Okio - Required by OkHttp (MUST come before OkHttp rules)
-dontwarn okio.**
-keep class okio.** { *; }
-keep interface okio.** { *; }
-keepclassmembers class okio.** { *; }

# OkHttp - Depends on Okio
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** { *; }

# Gson - JSON serialization
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn com.google.gson.**

# Timber - Logging
-keep class com.jakewharton.timber.** { *; }
-dontwarn org.jetbrains.annotations.**
-dontwarn com.jakewharton.timber.**

# NanoHTTPD
-keep class org.nanohttpd.** { *; }
-keep interface org.nanohttpd.** { *; }
-keepclassmembers class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep service classes
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep audio/media classes (don't need explicit keep for framework classes)
-dontwarn android.media.**

# Keep classes accessed via reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Suppress warnings for dynamic references
-dontwarn java.lang.invoke.**
-dontwarn javax.naming.**

# SSL/TLS - Required for WebSocket secure connections
-keep class javax.net.ssl.** { *; }
-keep class javax.security.** { *; }
-dontwarn javax.net.ssl.**
-dontwarn javax.security.**

# Keep all exceptions (for proper error handling)
-keep public class * extends java.lang.Exception
-keep public class * extends java.lang.Error

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}