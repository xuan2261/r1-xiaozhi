package com.phicomm.r1.xiaozhi.hardware;

import android.util.Log;

/**
 * Native JNI wrapper for R1 LED hardware control.
 * 
 * This class loads the native library 'libledLight-jni.so' which is already
 * present in the Phicomm R1 firmware. It provides direct hardware access
 * for LED control, which is significantly faster and more reliable than
 * shell command execution.
 * 
 * Requirements:
 * - Root access
 * - SELinux permissive mode (setenforce 0)
 * - Native library libledLight-jni.so (pre-installed in R1 firmware)
 * 
 * Based on r1-helper implementation:
 * r1-helper/app/src/main/java/com/phicomm/speaker/player/light/LedLight.java
 */
public class LedLight {
    private static final String TAG = "LedLight";
    
    /**
     * Flag indicating whether the native library was loaded successfully.
     * Check this before calling setColor() methods.
     */
    public static boolean loaded = false;

    /**
     * Set LED color with maximum brightness.
     * 
     * @param color RGB color value (0xRRGGBB format)
     *              Example: 0xFF0000 = red, 0x00FF00 = green, 0x0000FF = blue
     */
    public static void setColor(int color) {
        setColor(32767L, color);  // 32767 = 0x7FFF = max brightness
    }

    /**
     * Set LED color with custom brightness.
     * 
     * @param brightness Brightness level (0-32767, where 32767 is maximum)
     * @param color RGB color value (0xRRGGBB format)
     */
    public static void setColor(long brightness, int color) {
        if (loaded) {
            set_color(brightness, color);
        } else {
            Log.w(TAG, "Cannot set LED color - native library not loaded");
        }
    }

    /**
     * Native method that directly controls LED hardware.
     * 
     * This method is implemented in the native library libledLight-jni.so
     * which is part of the R1 firmware.
     * 
     * IMPORTANT: Requires SELinux permissive mode to access LED hardware.
     * 
     * @param brightness Brightness level (0-32767)
     * @param color RGB color value (0xRRGGBB)
     */
    public static native void set_color(long brightness, int color);

    /**
     * Static initializer - loads the native library on class load.
     * 
     * The library 'libledLight-jni.so' is already present in R1 firmware,
     * so we don't need to bundle it with the APK.
     */
    static {
        try {
            System.loadLibrary("ledLight-jni");
            loaded = true;
            Log.i(TAG, "✅ R1 native LED library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
            Log.w(TAG, "❌ R1 LED library not found - LED control disabled");
            Log.w(TAG, "Error: " + e.getMessage());
        }
    }
}

