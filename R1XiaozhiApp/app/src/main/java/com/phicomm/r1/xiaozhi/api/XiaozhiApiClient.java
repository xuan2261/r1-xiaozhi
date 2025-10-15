package com.phicomm.r1.xiaozhi.api;

import android.util.Log;

import com.phicomm.r1.xiaozhi.api.model.DeviceStatus;
import com.phicomm.r1.xiaozhi.api.model.PairingResponse;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP Client để tương tác với Xiaozhi API
 * Endpoints:
 * - POST /api/device/register - Register device và nhận pairing code
 * - GET /api/device/status - Check pairing status và nhận token
 */
public class XiaozhiApiClient {
    
    private static final String TAG = "XiaozhiApiClient";
    private static final String BASE_URL = "https://xiaozhi.me/api";
    private static final int TIMEOUT = 10000; // 10 seconds
    
    /**
     * Register device với Xiaozhi server và nhận pairing code
     * 
     * @param deviceId Device ID (MAC-based)
     * @param deviceType Device type (android, esp32, etc)
     * @return PairingResponse với code từ server
     */
    public PairingResponse registerDevice(String deviceId, String deviceType) throws Exception {
        String endpoint = BASE_URL + "/device/register";
        
        // Prepare request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("device_id", deviceId);
        requestBody.put("device_type", deviceType);
        
        Log.d(TAG, "Registering device: " + deviceId);
        
        // Make HTTP POST request
        String response = post(endpoint, requestBody.toString());
        
        // Parse response
        JSONObject json = new JSONObject(response);
        String code = json.getString("code");
        String returnedDeviceId = json.getString("device_id");
        long expiresAt = json.getLong("expires_at");
        
        Log.i(TAG, "Device registered successfully. Code: " + code);
        
        return new PairingResponse(code, returnedDeviceId, expiresAt);
    }
    
    /**
     * Check device pairing status
     * 
     * @param deviceId Device ID
     * @return DeviceStatus với status và token (nếu paired)
     */
    public DeviceStatus checkPairingStatus(String deviceId) throws Exception {
        String endpoint = BASE_URL + "/device/status?device_id=" + deviceId;
        
        Log.d(TAG, "Checking pairing status for: " + deviceId);
        
        // Make HTTP GET request
        String response = get(endpoint);
        
        // Parse response
        JSONObject json = new JSONObject(response);
        String status = json.getString("status");
        String token = json.optString("token", null);
        String returnedDeviceId = json.getString("device_id");
        
        Log.d(TAG, "Status: " + status + (token != null ? ", Token received" : ""));
        
        return new DeviceStatus(status, token, returnedDeviceId);
    }
    
    /**
     * Make HTTP POST request
     */
    private String post(String urlString, String body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setDoOutput(true);
            
            // Write body
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && 
                responseCode != HttpURLConnection.HTTP_CREATED) {
                throw new Exception("HTTP error code: " + responseCode);
            }
            
            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            return response.toString();
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Make HTTP GET request
     */
    private String get(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error code: " + responseCode);
            }
            
            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            
            return response.toString();
            
        } finally {
            conn.disconnect();
        }
    }
}