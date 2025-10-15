package com.phicomm.r1.xiaozhi.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Generator mã pairing theo chuẩn xiaozhi-esp32
 * Pairing code = 6 ký tự cuối của Device ID (MAC address)
 * KHÔNG có API call - gen LOCAL hoàn toàn
 */
public class PairingCodeGenerator {
    
    private static final String TAG = "PairingCode";
    private static final String PREFS_NAME = "xiaozhi_pairing";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PAIRED = "paired";
    
    /**
     * Lấy Device ID (MAC address without colons)
     * Cached để consistent across reboots
     */
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        
        if (deviceId == null) {
            deviceId = generateDeviceId(context);
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            Log.i(TAG, "Generated device ID: " + deviceId);
        }
        
        return deviceId;
    }
    
    /**
     * Generate Device ID từ WiFi MAC address
     */
    private static String generateDeviceId(Context context) {
        try {
            // Try WiFi MAC first
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String macAddress = wifiInfo.getMacAddress();
                
                if (macAddress != null && !macAddress.equals("02:00:00:00:00:00")) {
                    // Remove colons: AA:BB:CC:DD:EE:FF -> AABBCCDDEEFF
                    String deviceId = macAddress.replace(":", "").toUpperCase();
                    Log.d(TAG, "Device ID from MAC: " + deviceId);
                    return deviceId;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get MAC: " + e.getMessage());
        }
        
        // Fallback to Android ID
        try {
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            
            if (androidId != null && !androidId.equals("9774d56d682e549c")) {
                // Pad/trim to 12 chars
                while (androidId.length() < 12) {
                    androidId = "0" + androidId;
                }
                String deviceId = androidId.substring(0, 12).toUpperCase();
                Log.d(TAG, "Device ID from Android ID: " + deviceId);
                return deviceId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get Android ID: " + e.getMessage());
        }
        
        // Last resort - timestamp
        String deviceId = String.format("%012X", System.currentTimeMillis() & 0xFFFFFFFFFFFL);
        Log.w(TAG, "Device ID from timestamp: " + deviceId);
        return deviceId;
    }
    
    /**
     * Lấy pairing code = 6 ký tự cuối của Device ID
     * Theo ESP32: deviceId.substring(deviceId.length() - 6)
     * FORCE uppercase để đảm bảo match với server
     */
    public static String getPairingCode(Context context) {
        String deviceId = getDeviceId(context);
        String code = deviceId.substring(deviceId.length() - 6).toUpperCase();
        Log.i(TAG, "=== PAIRING CODE DEBUG ===");
        Log.i(TAG, "Device ID: " + deviceId);
        Log.i(TAG, "Pairing Code: " + code);
        Log.i(TAG, "=========================");
        return code;
    }
    
    /**
     * Format code để hiển thị: DDEEFF -> DD EE FF
     */
    public static String formatPairingCode(String code) {
        if (code == null || code.length() != 6) {
            return code;
        }
        return code.substring(0, 2) + " " + code.substring(2, 4) + " " + code.substring(4, 6);
    }
    
    /**
     * Check paired status
     */
    public static boolean isPaired(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PAIRED, false);
    }
    
    /**
     * Mark as paired (gọi sau khi nhận Authorize response success)
     */
    public static void markAsPaired(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PAIRED, true).apply();
        Log.i(TAG, "Device marked as paired");
    }
    
    /**
     * Reset pairing status
     */
    public static void resetPairing(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PAIRED, false).apply();
        Log.i(TAG, "Pairing reset");
    }
}