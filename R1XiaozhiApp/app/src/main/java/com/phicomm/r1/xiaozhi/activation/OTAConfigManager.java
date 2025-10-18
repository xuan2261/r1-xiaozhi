package com.phicomm.r1.xiaozhi.activation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OTA Config Manager - Fetch device configuration from server
 * Based on py-xiaozhi/src/core/ota.py
 * 
 * This class handles:
 * 1. Fetching WebSocket and MQTT configurations
 * 2. Getting activation data (challenge + code) if device not activated
 * 3. Updating local configuration with server response
 */
public class OTAConfigManager {
    
    private static final String TAG = "OTAConfigManager";
    private static final String OTA_URL = "https://api.tenclass.net/xiaozhi/ota/";
    private static final String APP_VERSION = "1.0.0";
    private static final String BOARD_TYPE = "android";
    private static final String APP_NAME = "xiaozhi-android";
    
    private final Context context;
    private final DeviceFingerprint fingerprint;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    /**
     * OTA Response model
     */
    public static class OTAResponse {
        public boolean success;
        public String error;
        
        // WebSocket config
        public WebSocketConfig websocket;
        
        // Activation data (only present if device not activated)
        public ActivationData activation;
        
        public static class WebSocketConfig {
            public String url;
            public String token;
            public String protocol;
        }
        
        public static class ActivationData {
            public String challenge;
            public String code;
            public String url;
            public int timeout;
        }
    }
    
    /**
     * Callback for OTA requests
     */
    public interface OTACallback {
        void onSuccess(OTAResponse response);
        void onError(String error);
    }
    
    public OTAConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.fingerprint = DeviceFingerprint.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Fetch OTA configuration from server
     * Based on ota.py get_ota_config() - line 120-164
     */
    public void fetchOTAConfig(final OTACallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    OTAResponse response = performOTARequest();
                    notifySuccess(callback, response);
                } catch (Exception e) {
                    Log.e(TAG, "OTA request failed", e);
                    notifyError(callback, e.getMessage());
                }
            }
        });
    }
    
    /**
     * Perform OTA HTTP request
     */
    private OTAResponse performOTARequest() throws Exception {
        
        // Get device identity
        String deviceId = fingerprint.getMacAddress();
        String serialNumber = fingerprint.getSerialNumber();
        
        if (deviceId == null || serialNumber == null) {
            throw new Exception("Device identity not initialized");
        }
        
        // Build request URL with query parameters
        String clientId = getClientId();
        String requestUrl = OTA_URL + 
            "?device_id=" + deviceId +
            "&client_id=" + clientId;
        
        Log.i(TAG, "Fetching OTA config from: " + requestUrl);
        
        // Build headers (based on ota.py build_headers() - line 89-118)
        URL url = new URL(requestUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Device-Id", deviceId);
            conn.setRequestProperty("Client-Id", clientId);
            conn.setRequestProperty("User-Agent", BOARD_TYPE + "/" + APP_NAME + "-" + APP_VERSION);
            conn.setRequestProperty("Accept-Language", "zh-CN");
            
            // Add Activation-Version header for v2 protocol
            conn.setRequestProperty("Activation-Version", APP_VERSION);
            
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            // Build payload (based on ota.py build_payload() - line 68-87)
            JSONObject payload = buildPayload(deviceId);
            
            // Write payload
            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes());
            os.flush();
            os.close();
            
            // Read response
            int statusCode = conn.getResponseCode();
            
            BufferedReader reader;
            if (statusCode >= 200 && statusCode < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }
            
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
            reader.close();
            
            Log.d(TAG, "OTA Response (" + statusCode + "): " + responseBody);
            
            if (statusCode != 200) {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
            
            // Parse response
            return parseOTAResponse(responseBody.toString());
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Build OTA request payload
     * Based on ota.py build_payload() - line 68-87
     */
    private JSONObject buildPayload(String deviceId) throws Exception {
        String hmacKey = fingerprint.getHmacKey();
        
        JSONObject payload = new JSONObject();
        
        // Application info
        JSONObject application = new JSONObject();
        application.put("version", APP_VERSION);
        application.put("elf_sha256", hmacKey != null ? hmacKey : "unknown");
        payload.put("application", application);
        
        // Board info
        JSONObject board = new JSONObject();
        board.put("type", BOARD_TYPE);
        board.put("name", APP_NAME);
        board.put("ip", getLocalIP());
        board.put("mac", deviceId);
        payload.put("board", board);
        
        return payload;
    }
    
    /**
     * Parse OTA response
     * Based on system_initializer.py stage_3_ota_config() - line 161-209
     */
    private OTAResponse parseOTAResponse(String jsonString) throws Exception {
        JSONObject json = new JSONObject(jsonString);
        
        OTAResponse response = new OTAResponse();
        response.success = true;
        
        // Parse WebSocket config
        if (json.has("websocket")) {
            JSONObject wsJson = json.getJSONObject("websocket");
            OTAResponse.WebSocketConfig wsConfig = new OTAResponse.WebSocketConfig();
            wsConfig.url = wsJson.optString("url", null);
            wsConfig.token = wsJson.optString("token", null);
            wsConfig.protocol = wsJson.optString("protocol", "v1");
            response.websocket = wsConfig;
            
            Log.i(TAG, "WebSocket config received: " + wsConfig.url);
        }
        
        // Parse activation data
        // Based on system_initializer.py line 193-203
        if (json.has("activation")) {
            JSONObject activationJson = json.getJSONObject("activation");
            OTAResponse.ActivationData activationData = new OTAResponse.ActivationData();
            activationData.challenge = activationJson.optString("challenge", null);
            activationData.code = activationJson.optString("code", null);
            activationData.url = activationJson.optString("url", null);
            activationData.timeout = activationJson.optInt("timeout", 300);
            response.activation = activationData;
            
            Log.i(TAG, "Activation data received - Device needs activation");
            Log.i(TAG, "Verification code: " + activationData.code);
        } else {
            Log.i(TAG, "No activation data - Device already activated");
        }
        
        return response;
    }
    
    /**
     * Get or generate client ID
     */
    private String getClientId() {
        // Use a consistent client ID stored in SharedPreferences
        String clientId = context.getSharedPreferences("xiaozhi_ota", Context.MODE_PRIVATE)
            .getString("client_id", null);
        
        if (clientId == null) {
            clientId = UUID.randomUUID().toString();
            context.getSharedPreferences("xiaozhi_ota", Context.MODE_PRIVATE)
                .edit()
                .putString("client_id", clientId)
                .apply();
        }
        
        return clientId;
    }
    
    /**
     * Get local IP address
     */
    private String getLocalIP() {
        try {
            java.net.InetAddress inetAddress = java.net.InetAddress.getLocalHost();
            return inetAddress.getHostAddress();
        } catch (Exception e) {
            Log.w(TAG, "Failed to get local IP", e);
            return "127.0.0.1";
        }
    }
    
    // Notification helpers (main thread)
    
    private void notifySuccess(final OTACallback callback, final OTAResponse response) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(response);
                }
            });
        }
    }
    
    private void notifyError(final OTACallback callback, final String error) {
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(error);
                }
            });
        }
    }
}