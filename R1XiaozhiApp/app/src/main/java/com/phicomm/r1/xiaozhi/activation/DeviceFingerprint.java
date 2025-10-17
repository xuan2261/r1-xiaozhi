package com.phicomm.r1.xiaozhi.activation;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Device Fingerprint - Generate unique device identity
 * Based on py-xiaozhi/src/utils/device_fingerprint.py
 */
public class DeviceFingerprint {
    
    private static final String TAG = "DeviceFingerprint";
    private static final String PREFS_NAME = "xiaozhi_device_identity";
    private static final String KEY_SERIAL_NUMBER = "serial_number";
    private static final String KEY_HMAC_KEY = "hmac_key";
    private static final String KEY_ACTIVATION_STATUS = "activation_status";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    
    private static DeviceFingerprint instance;
    private final Context context;
    private final SharedPreferences prefs;
    
    private DeviceFingerprint(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureDeviceIdentity();
    }
    
    public static synchronized DeviceFingerprint getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceFingerprint(context);
        }
        return instance;
    }
    
    /**
     * Ensure device identity exists
     */
    private void ensureDeviceIdentity() {
        String serialNumber = prefs.getString(KEY_SERIAL_NUMBER, null);
        String hmacKey = prefs.getString(KEY_HMAC_KEY, null);
        
        if (serialNumber == null || hmacKey == null) {
            Log.i(TAG, "Generating new device identity");
            generateAndSaveIdentity();
        } else {
            Log.i(TAG, "Device identity exists: " + serialNumber);
        }
    }
    
    /**
     * Generate and save device identity
     */
    private void generateAndSaveIdentity() {
        try {
            // Get MAC address
            String macAddress = retrieveMacAddress();
            
            // Generate serial number from MAC
            String serialNumber = generateSerialNumber(macAddress);
            
            // Generate HMAC key from hardware info
            String hmacKey = generateHardwareHash(macAddress);
            
            // Save to SharedPreferences
            prefs.edit()
                .putString(KEY_MAC_ADDRESS, macAddress)
                .putString(KEY_SERIAL_NUMBER, serialNumber)
                .putString(KEY_HMAC_KEY, hmacKey)
                .putBoolean(KEY_ACTIVATION_STATUS, false)
                .apply();
            
            Log.i(TAG, "Generated serial number: " + serialNumber);
            Log.i(TAG, "Generated HMAC key: " + hmacKey.substring(0, 8) + "...");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate device identity", e);
        }
    }
    
    /**
     * Retrieve MAC address from system
     */
    private String retrieveMacAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String macAddress = wifiInfo.getMacAddress();
                
                if (macAddress != null && !macAddress.equals("02:00:00:00:00:00")) {
                    // Normalize: remove colons, lowercase
                    return macAddress.replace(":", "").toLowerCase();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get MAC address: " + e.getMessage());
        }
        
        // Fallback to Android ID
        try {
            String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
            );
            
            if (androidId != null && !androidId.equals("9774d56d682e549c")) {
                // Pad to 12 chars
                while (androidId.length() < 12) {
                    androidId = "0" + androidId;
                }
                return androidId.substring(0, 12).toLowerCase();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get Android ID: " + e.getMessage());
        }
        
        // Last resort: timestamp-based
        String fallback = String.format("%012x", System.currentTimeMillis() & 0xFFFFFFFFFFL);
        Log.w(TAG, "Using timestamp-based MAC: " + fallback);
        return fallback;
    }
    
    /**
     * Generate serial number from MAC address
     * Format: SN-{hash}-{mac}
     */
    private String generateSerialNumber(String macAddress) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(macAddress.getBytes());
            
            StringBuilder shortHash = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                shortHash.append(String.format("%02X", hash[i]));
            }
            
            return "SN-" + shortHash + "-" + macAddress.toUpperCase();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate serial number", e);
            return "SN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + 
                   "-" + macAddress.toUpperCase();
        }
    }
    
    /**
     * Generate hardware hash (HMAC key)
     */
    private String generateHardwareHash(String macAddress) {
        try {
            // Combine hardware identifiers
            StringBuilder identifiers = new StringBuilder();
            identifiers.append(macAddress);
            identifiers.append("||");
            identifiers.append(Build.MANUFACTURER);
            identifiers.append("||");
            identifiers.append(Build.MODEL);
            identifiers.append("||");
            identifiers.append(Build.DEVICE);
            
            // SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(identifiers.toString().getBytes());
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate hardware hash", e);
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
    
    /**
     * Generate HMAC signature for challenge
     */
    public String generateHmac(String challenge) {
        String hmacKey = getHmacKey();
        if (hmacKey == null) {
            Log.e(TAG, "HMAC key not found");
            return null;
        }
        
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                hmacKey.getBytes(),
                "HmacSHA256"
            );
            mac.init(secretKey);
            
            byte[] hmacBytes = mac.doFinal(challenge.getBytes());
            
            // Convert to hex
            StringBuilder hexString = new StringBuilder();
            for (byte b : hmacBytes) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate HMAC", e);
            return null;
        }
    }
    
    // Getters
    
    public String getMacAddress() {
        return prefs.getString(KEY_MAC_ADDRESS, null);
    }
    
    public String getSerialNumber() {
        return prefs.getString(KEY_SERIAL_NUMBER, null);
    }
    
    public String getHmacKey() {
        return prefs.getString(KEY_HMAC_KEY, null);
    }
    
    public boolean isActivated() {
        return prefs.getBoolean(KEY_ACTIVATION_STATUS, false);
    }
    
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }
    
    // Setters
    
    public void setActivationStatus(boolean activated) {
        prefs.edit().putBoolean(KEY_ACTIVATION_STATUS, activated).apply();
        Log.i(TAG, "Activation status: " + activated);
    }
    
    public void setAccessToken(String token) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply();
        Log.i(TAG, "Access token saved");
    }
    
    /**
     * Reset device identity (for testing)
     */
    public void resetIdentity() {
        prefs.edit().clear().apply();
        Log.i(TAG, "Device identity reset");
        ensureDeviceIdentity();
    }
    
    /**
     * Get device info for debugging
     */
    public String getDeviceInfo() {
        return String.format(
            "Serial: %s\nMAC: %s\nActivated: %b\nToken: %s",
            getSerialNumber(),
            getMacAddress(),
            isActivated(),
            getAccessToken() != null ? "Yes" : "No"
        );
    }
}