package com.phicomm.r1.xiaozhi.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Generator cho mã pairing dựa trên Device ID (giống xiaozhi-esp32)
 * Pairing code = 6 ký tự cuối của device_id
 * Theo: https://github.com/78/xiaozhi-esp32
 */
public class PairingCodeGenerator {
    
    private static final String TAG = "PairingCode";
    private static final String PREFS_NAME = "xiaozhi_pairing";
    private static final String KEY_PAIRING_CODE = "pairing_code";
    private static final String KEY_DEVICE_ID = "device_id";
    
    /**
     * Lấy pairing code từ Device ID
     * Pairing code = 6 ký tự cuối của device_id (theo xiaozhi-esp32 protocol)
     */
    public static String getPairingCode(Context context) {
        String deviceId = getDeviceId(context);
        // Lấy 6 ký tự cuối của device ID
        if (deviceId.length() >= 6) {
            return deviceId.substring(deviceId.length() - 6).toUpperCase();
        } else {
            // Fallback nếu device ID quá ngắn
            return String.format("%06d", deviceId.hashCode() & 0xFFFFFF);
        }
    }
    
    /**
     * Lấy Device ID duy nhất cho thiết bị
     * Ưu tiên: WiFi MAC > Android ID
     * Format: AABBCCDDEEFF (giống ESP32)
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
     * Generate Device ID từ MAC address hoặc Android ID
     */
    private static String generateDeviceId(Context context) {
        try {
            // Option 1: Dùng WiFi MAC address (giống ESP32)
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
            Log.w(TAG, "Failed to get MAC address: " + e.getMessage());
        }
        
        // Option 2: Fallback to Android ID
        try {
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            
            if (androidId != null && !androidId.equals("9774d56d682e549c")) { // Not emulator
                // Pad to 12 chars if needed
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
        
        // Option 3: Last resort - generate from timestamp
        String timestamp = String.format("%012X", System.currentTimeMillis() & 0xFFFFFFFFFFFL);
        Log.w(TAG, "Device ID from timestamp (last resort): " + timestamp);
        return timestamp;
    }
    
    /**
     * Reset device ID và pairing code
     * CHÚ Ý: Pairing code được tính từ device ID, nên reset device ID sẽ tạo code mới
     */
    public static String resetPairingCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Xóa device ID cũ
        prefs.edit().remove(KEY_DEVICE_ID).apply();
        // Generate device ID mới (sẽ dùng thời gian hiện tại)
        String newDeviceId = generateDeviceId(context);
        prefs.edit().putString(KEY_DEVICE_ID, newDeviceId).apply();
        
        String newCode = getPairingCode(context);
        Log.i(TAG, "===========================================");
        Log.i(TAG, "RESET PAIRING");
        Log.i(TAG, "New Device ID: " + newDeviceId);
        Log.i(TAG, "New Pairing Code: " + newCode);
        Log.i(TAG, "===========================================");
        return newCode;
    }
    
    /**
     * Format mã pairing code để hiển thị (xxx-xxx)
     */
    public static String formatPairingCode(String code) {
        if (code.length() == 6) {
            return code.substring(0, 3) + "-" + code.substring(3);
        }
        return code;
    }
}