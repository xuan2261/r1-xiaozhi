package com.phicomm.r1.xiaozhi.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;

import com.phicomm.r1.xiaozhi.api.XiaozhiApiClient;
import com.phicomm.r1.xiaozhi.api.model.DeviceStatus;
import com.phicomm.r1.xiaozhi.api.model.PairingResponse;

/**
 * Quản lý pairing code từ Xiaozhi API
 * Flow: Register device → Nhận code từ server → Poll status → Nhận token
 */
public class PairingCodeGenerator {
    
    private static final String TAG = "PairingCode";
    private static final String PREFS_NAME = "xiaozhi_pairing";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PAIRING_CODE = "pairing_code";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_PAIRED = "paired";
    private static final String KEY_CODE_EXPIRES = "code_expires_at";
    
    /**
     * Callback cho async operations
     */
    public interface PairingCallback {
        void onSuccess(String code);
        void onError(String error);
    }
    
    public interface StatusCallback {
        void onPaired(String token);
        void onPending();
        void onError(String error);
    }
    
    /**
     * Lấy pairing code (từ cache hoặc register mới)
     * ASYNC operation - dùng callback để nhận kết quả
     */
    public static void getPairingCode(Context context, PairingCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedCode = prefs.getString(KEY_PAIRING_CODE, null);
        long expiresAt = prefs.getLong(KEY_CODE_EXPIRES, 0);
        
        // Check cached code còn valid không
        if (cachedCode != null && System.currentTimeMillis() < expiresAt) {
            Log.d(TAG, "Using cached pairing code: " + cachedCode);
            callback.onSuccess(cachedCode);
            return;
        }
        
        // Code hết hạn hoặc chưa có → register mới
        Log.i(TAG, "Registering device to get new pairing code");
        registerDeviceAsync(context, callback);
    }
    
    /**
     * Register device với Xiaozhi API (async)
     */
    private static void registerDeviceAsync(final Context context, final PairingCallback callback) {
        new AsyncTask<Void, Void, PairingResponse>() {
            private Exception error;
            
            @Override
            protected PairingResponse doInBackground(Void... params) {
                try {
                    String deviceId = getDeviceId(context);
                    XiaozhiApiClient client = new XiaozhiApiClient();
                    return client.registerDevice(deviceId, "android_r1");
                } catch (Exception e) {
                    error = e;
                    return null;
                }
            }
            
            @Override
            protected void onPostExecute(PairingResponse response) {
                if (response != null) {
                    // Lưu code vào cache
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                        .putString(KEY_PAIRING_CODE, response.getCode())
                        .putLong(KEY_CODE_EXPIRES, response.getExpiresAt())
                        .apply();
                    
                    Log.i(TAG, "Pairing code received: " + response.getCode());
                    callback.onSuccess(response.getCode());
                } else {
                    String errorMsg = error != null ? error.getMessage() : "Unknown error";
                    Log.e(TAG, "Failed to register device: " + errorMsg);
                    callback.onError(errorMsg);
                }
            }
        }.execute();
    }
    
    /**
     * Check pairing status (async)
     * Poll này để xem user đã nhập code vào console chưa
     */
    public static void checkPairingStatus(final Context context, final StatusCallback callback) {
        new AsyncTask<Void, Void, DeviceStatus>() {
            private Exception error;
            
            @Override
            protected DeviceStatus doInBackground(Void... params) {
                try {
                    String deviceId = getDeviceId(context);
                    XiaozhiApiClient client = new XiaozhiApiClient();
                    return client.checkPairingStatus(deviceId);
                } catch (Exception e) {
                    error = e;
                    return null;
                }
            }
            
            @Override
            protected void onPostExecute(DeviceStatus status) {
                if (status != null) {
                    if (status.isPaired()) {
                        // Paired! Lưu token
                        saveAuthToken(context, status.getToken());
                        markAsPaired(context);
                        Log.i(TAG, "Device paired successfully!");
                        callback.onPaired(status.getToken());
                    } else if (status.isPending()) {
                        Log.d(TAG, "Pairing still pending");
                        callback.onPending();
                    } else {
                        Log.w(TAG, "Pairing status: " + status.getStatus());
                        callback.onError("Status: " + status.getStatus());
                    }
                } else {
                    String errorMsg = error != null ? error.getMessage() : "Unknown error";
                    Log.e(TAG, "Failed to check status: " + errorMsg);
                    callback.onError(errorMsg);
                }
            }
        }.execute();
    }
    
    /**
     * Format pairing code để hiển thị dễ đọc (XX XX XX)
     */
    public static String formatPairingCode(String code) {
        if (code == null || code.length() != 6) {
            return code;
        }
        return code.substring(0, 2) + " " + code.substring(2, 4) + " " + code.substring(4, 6);
    }
    
    /**
     * Lưu auth token sau khi paired
     */
    private static void saveAuthToken(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
        Log.d(TAG, "Auth token saved");
    }
    
    /**
     * Lấy auth token đã lưu
     */
    public static String getAuthToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }
    
    /**
     * Lấy cached pairing code (synchronous) - dùng cho display only
     * KHÔNG register với API, chỉ đọc từ cache
     * Trả về null nếu chưa có cache
     */
    public static String getCachedPairingCode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_PAIRING_CODE, null);
        long expiresAt = prefs.getLong(KEY_CODE_EXPIRES, 0);
        
        // Check còn valid không
        if (code != null && System.currentTimeMillis() < expiresAt) {
            return code;
        }
        return null;
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
     * Kiểm tra thiết bị đã được pair chưa
     */
    public static boolean isPaired(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean paired = prefs.getBoolean(KEY_PAIRED, false);
        String token = prefs.getString(KEY_AUTH_TOKEN, null);
        return paired && token != null;
    }
    
    /**
     * Đánh dấu thiết bị đã pair thành công
     */
    private static void markAsPaired(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PAIRED, true).apply();
        Log.i(TAG, "Device marked as paired");
    }
    
    /**
     * Reset pairing - xóa tất cả data và register lại
     */
    public static void resetPairing(Context context, PairingCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_PAIRING_CODE)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_PAIRED)
            .remove(KEY_CODE_EXPIRES)
            .apply();
        
        Log.i(TAG, "Pairing reset - registering new device");
        getPairingCode(context, callback);
    }
}