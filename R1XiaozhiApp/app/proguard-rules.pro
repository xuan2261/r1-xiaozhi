# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep all classes in our package
-keep class com.phicomm.r1.xiaozhi.** { *; }

# Java-WebSocket library
-keep class org.java_websocket.** { *; }
-keep interface org.java_websocket.** { *; }
-keepclassmembers class * extends org.java_websocket.client.WebSocketClient {
    <methods>;
}
-dontwarn org.java_websocket.**

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