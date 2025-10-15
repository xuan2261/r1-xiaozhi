package com.phicomm.r1.xiaozhi.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Random;

/**
 * Generator cho mã pairing 6 số để kết nối với Xiaozhi Cloud
 * Mã này được tạo một lần và lưu vĩnh viễn cho thiết bị
 */
public class PairingCodeGenerator {
    
    private static final String TAG = "PairingCode";
    private static final String PREFS_NAME = "xiaozhi_pairing";
    private static final String KEY_PAIRING_CODE = "pairing_code";
    private static final String KEY_DEVICE_ID = "device_id";
    
    /**
     * Lấy hoặc tạo mã pairing code cho thiết bị
     * Mã này sẽ được tạo một lần và lưu lại
     */
    public static String getPairingCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_PAIRING_CODE, null);
        
        if (code == null) {
            // Tạo mã mới
            code = generatePairingCode();
            prefs.edit().putString(KEY_PAIRING_CODE, code).apply();
            Log.d(TAG, "Generated new pairing code: " + code);
        } else {
            Log.d(TAG, "Using existing pairing code: " + code);
        }
        
        return code;
    }
    
    /**
     * Tạo Device ID duy nhất cho thiết bị
     */
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        
        if (deviceId == null) {
            deviceId = generateDeviceId();
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            Log.d(TAG, "Generated new device ID: " + deviceId);
        }
        
        return deviceId;
    }
    
    /**
     * Tạo mã 6 số ngẫu nhiên
     */
    private static String generatePairingCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 100000 đến 999999
        return String.valueOf(code);
    }
    
    /**
     * Tạo Device ID từ thông tin thiết bị
     */
    private static String generateDeviceId() {
        // Kết hợp nhiều yếu tố để tạo ID duy nhất
        long timestamp = System.currentTimeMillis();
        int random = new Random().nextInt(10000);
        return "R1-" + timestamp + "-" + random;
    }
    
    /**
     * Reset pairing code (dùng khi cần pair lại với agent khác)
     * Tạo mã mới hoàn toàn
     */
    public static String resetPairingCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PAIRING_CODE).apply();
        String newCode = getPairingCode(context); // Tự động tạo mã mới
        Log.d(TAG, "Pairing code reset to: " + newCode);
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