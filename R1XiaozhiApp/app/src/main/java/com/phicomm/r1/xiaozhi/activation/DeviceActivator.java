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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Device Activator - Handle device activation flow
 * Based on py-xiaozhi/src/utils/device_activator.py
 */
public class DeviceActivator {
    
    private static final String TAG = "DeviceActivator";
    private static final String ACTIVATION_URL = "https://api.tenclass.net/xiaozhi/ota/activate";
    private static final int MAX_RETRIES = 60; // 5 minutes max
    private static final int RETRY_INTERVAL_MS = 5000; // 5 seconds
    
    private final Context context;
    private final DeviceFingerprint fingerprint;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isActivating = new AtomicBoolean(false);
    
    private ActivationListener listener;
    
    public interface ActivationListener {
        void onActivationStarted(String verificationCode);
        void onActivationProgress(int attempt, int maxAttempts);
        void onActivationSuccess(String accessToken);
        void onActivationFailed(String error);
    }
    
    public DeviceActivator(Context context) {
        this.context = context.getApplicationContext();
        this.fingerprint = DeviceFingerprint.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setListener(ActivationListener listener) {
        this.listener = listener;
    }
    
    /**
     * Start activation process
     */
    public void startActivation() {
        if (isActivating.getAndSet(true)) {
            Log.w(TAG, "Activation already in progress");
            return;
        }
        
        if (fingerprint.isActivated()) {
            Log.i(TAG, "Device already activated");
            notifySuccess(fingerprint.getAccessToken());
            isActivating.set(false);
            return;
        }
        
        executor.execute(new Runnable() {
            @Override
            public void run() {
                performActivation();
            }
        });
    }
    
    /**
     * Cancel activation
     */
    public void cancelActivation() {
        isActivating.set(false);
        Log.i(TAG, "Activation cancelled");
    }
    
    /**
     * Perform activation flow
     */
    private void performActivation() {
        try {
            String serialNumber = fingerprint.getSerialNumber();
            String deviceId = fingerprint.getMacAddress();
            
            if (serialNumber == null || deviceId == null) {
                notifyError("Device identity not found");
                return;
            }
            
            Log.i(TAG, "Starting activation for device: " + deviceId);
            
            // Retry loop
            for (int attempt = 1; attempt <= MAX_RETRIES && isActivating.get(); attempt++) {
                
                notifyProgress(attempt, MAX_RETRIES);
                
                try {
                    ActivationResponse response = sendActivationRequest(serialNumber, deviceId);
                    
                    if (response.success) {
                        // Activation successful
                        Log.i(TAG, "Activation successful!");
                        fingerprint.setActivationStatus(true);
                        if (response.accessToken != null) {
                            fingerprint.setAccessToken(response.accessToken);
                        }
                        notifySuccess(response.accessToken);
                        isActivating.set(false);
                        return;
                        
                    } else if (response.statusCode == 202) {
                        // Waiting for user input
                        if (attempt == 1 && response.verificationCode != null) {
                            notifyVerificationCode(response.verificationCode);
                        }
                        Log.i(TAG, "Waiting for user to enter verification code...");
                        Thread.sleep(RETRY_INTERVAL_MS);
                        
                    } else {
                        // Other errors - log but continue retrying
                        Log.w(TAG, "Activation attempt " + attempt + " failed: " + response.error);
                        Thread.sleep(RETRY_INTERVAL_MS);
                    }
                    
                } catch (InterruptedException e) {
                    Log.i(TAG, "Activation interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Activation request failed", e);
                    Thread.sleep(RETRY_INTERVAL_MS);
                }
            }
            
            // Max retries reached
            if (isActivating.get()) {
                notifyError("Activation timeout - max retries reached");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Activation process failed", e);
            notifyError("Activation failed: " + e.getMessage());
        } finally {
            isActivating.set(false);
        }
    }
    
    /**
     * Send activation request to server
     */
    private ActivationResponse sendActivationRequest(String serialNumber, String deviceId) throws Exception {
        
        // For first request, we need to get challenge from server
        // In py-xiaozhi, this comes from server's activation flow
        // For now, we'll use a simplified approach
        
        String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
        String hmac = fingerprint.generateHmac(challenge);
        
        if (hmac == null) {
            throw new Exception("Failed to generate HMAC");
        }
        
        // Build request payload
        JSONObject payload = new JSONObject();
        JSONObject innerPayload = new JSONObject();
        innerPayload.put("algorithm", "hmac-sha256");
        innerPayload.put("serial_number", serialNumber);
        innerPayload.put("challenge", challenge);
        innerPayload.put("hmac", hmac);
        payload.put("Payload", innerPayload);
        
        // Send HTTP request
        URL url = new URL(ACTIVATION_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Activation-Version", "2");
            conn.setRequestProperty("Device-Id", deviceId);
            conn.setRequestProperty("Client-Id", java.util.UUID.randomUUID().toString());
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
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
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "Response (" + statusCode + "): " + response);
            
            // Parse response
            ActivationResponse result = new ActivationResponse();
            result.statusCode = statusCode;
            
            if (statusCode == 200) {
                // Success
                result.success = true;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.accessToken = json.optString("access_token", null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse success response", e);
                }
                
            } else if (statusCode == 202) {
                // Waiting for verification
                result.success = false;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.verificationCode = json.optString("code", null);
                    result.message = json.optString("message", "Please enter verification code");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse 202 response", e);
                }
                
            } else {
                // Error
                result.success = false;
                try {
                    JSONObject json = new JSONObject(response.toString());
                    result.error = json.optString("error", "Unknown error");
                } catch (Exception e) {
                    result.error = "HTTP " + statusCode + ": " + response;
                }
            }
            
            return result;
            
        } finally {
            conn.disconnect();
        }
    }
    
    // Notification helpers (main thread)
    
    private void notifyVerificationCode(final String code) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationStarted(code);
                }
            });
        }
    }
    
    private void notifyProgress(final int attempt, final int max) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationProgress(attempt, max);
                }
            });
        }
    }
    
    private void notifySuccess(final String token) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationSuccess(token);
                }
            });
        }
    }
    
    private void notifyError(final String error) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onActivationFailed(error);
                }
            });
        }
    }
    
    /**
     * Check if device is activated
     */
    public boolean isActivated() {
        return fingerprint.isActivated() && fingerprint.getAccessToken() != null;
    }
    
    /**
     * Get access token
     */
    public String getAccessToken() {
        return fingerprint.getAccessToken();
    }
    
    /**
     * Reset activation (for testing)
     */
    public void resetActivation() {
        fingerprint.resetIdentity();
        Log.i(TAG, "Activation reset");
    }
    
    /**
     * Response from activation API
     */
    private static class ActivationResponse {
        boolean success;
        int statusCode;
        String verificationCode;
        String message;
        String accessToken;
        String error;
    }
}