package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.util.ErrorCodes;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.UUID;

/**
 * Service quản lý kết nối WebSocket với Xiaozhi Cloud
 * Theo chuẩn xiaozhi-esp32: wss://xiaozhi.me/v1/ws + Authorize handshake
 * Enhanced với ErrorCodes và retry logic (học từ android-client & py-xiaozhi)
 */
public class XiaozhiConnectionService extends Service {
    
    private static final String TAG = "XiaozhiConnection";
    private static final int MAX_RETRIES = 3;
    
    private WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private ConnectionListener connectionListener;
    
    // Retry logic
    private Handler retryHandler;
    private int retryCount = 0;
    private boolean isRetrying = false;
    
    public class LocalBinder extends Binder {
        public XiaozhiConnectionService getService() {
            return XiaozhiConnectionService.this;
        }
    }
    
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onPairingSuccess();
        void onPairingFailed(String error);
        void onMessage(String message);
        void onError(String error);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        retryHandler = new Handler();
        return START_STICKY;
    }
    
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    /**
     * Connect và gửi Authorize handshake
     * Theo ESP32: connect đơn giản, KHÔNG có token trong URL
     */
    public void connect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.w(TAG, "Already connected");
            return;
        }
        
        try {
            // WebSocket URL đơn giản - KHÔNG có token!
            URI serverUri = new URI(XiaozhiConfig.WEBSOCKET_URL);
            Log.i(TAG, "Connecting to: " + serverUri);
            
            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "WebSocket connected");
                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }
                    
                    // Gửi Authorize handshake ngay sau khi connect
                    sendAuthorizeHandshake();
                }
                
                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Message received: " + message);
                    handleMessage(message);
                    
                    if (connectionListener != null) {
                        connectionListener.onMessage(message);
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "WebSocket closed: " + reason + " (code: " + code + ")");
                    
                    if (connectionListener != null) {
                        connectionListener.onDisconnected();
                    }
                    
                    // Auto retry if not manually disconnected
                    if (remote && !isRetrying) {
                        scheduleReconnect(ErrorCodes.WEBSOCKET_ERROR);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);
                    
                    String errorMsg = ErrorCodes.getMessage(ErrorCodes.WEBSOCKET_ERROR);
                    if (connectionListener != null) {
                        connectionListener.onError(errorMsg);
                    }
                    
                    // Retry on error
                    scheduleReconnect(ErrorCodes.WEBSOCKET_ERROR);
                }
            };
            
            webSocketClient.connect();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect: " + e.getMessage(), e);
            if (connectionListener != null) {
                connectionListener.onError("Connection failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Gửi Authorize handshake theo format ESP32
     * 
     * Format:
     * {
     *   "header": {
     *     "name": "Authorize",
     *     "namespace": "ai.xiaoai.authorize",
     *     "message_id": "uuid"
     *   },
     *   "payload": {
     *     "device_id": "AABBCCDDEEFF",
     *     "pairing_code": "DDEEFF",
     *     "device_type": "android_r1",
     *     "device_name": "Phicomm R1",
     *     "client_id": "1000013"
     *   }
     * }
     */
    private void sendAuthorizeHandshake() {
        try {
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            String pairingCode = PairingCodeGenerator.getPairingCode(this);
            
            JSONObject message = new JSONObject();
            
            // Header
            JSONObject header = new JSONObject();
            header.put("name", "Authorize");
            header.put("namespace", "ai.xiaoai.authorize");
            header.put("message_id", UUID.randomUUID().toString());
            
            // Payload
            JSONObject payload = new JSONObject();
            payload.put("device_id", deviceId);
            payload.put("pairing_code", pairingCode);
            payload.put("device_type", "android_r1");
            payload.put("device_name", "Phicomm R1");
            payload.put("client_id", XiaozhiConfig.CLIENT_ID);
            
            message.put("header", header);
            message.put("payload", payload);
            
            String json = message.toString();
            Log.i(TAG, "Sending Authorize handshake: " + json);
            webSocketClient.send(json);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create Authorize handshake: " + e.getMessage(), e);
            if (connectionListener != null) {
                connectionListener.onError("Handshake failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle message từ server
     * Authorize response format:
     * {
     *   "header": {"name": "Authorize", "namespace": "ai.xiaoai.authorize", ...},
     *   "payload": {"code": "0", "message": "success"}
     * }
     * code = "0" -> Success
     * code != "0" -> Failed
     */
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            
            // Check if this is Authorize response
            if (json.has("header")) {
                JSONObject header = json.getJSONObject("header");
                String name = header.optString("name", "");
                String namespace = header.optString("namespace", "");
                
                if ("Authorize".equals(name) && "ai.xiaoai.authorize".equals(namespace)) {
                    handleAuthorizeResponse(json);
                    return;
                }
            }
            
            // Handle other message types here
            Log.d(TAG, "Unhandled message type: " + message);
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse message: " + e.getMessage());
        }
    }
    
    /**
     * Handle Authorize response
     */
    private void handleAuthorizeResponse(JSONObject json) {
        try {
            JSONObject payload = json.getJSONObject("payload");
            String codeStr = payload.optString("code", "-1");
            String serverMessage = payload.optString("message", "");
            
            int errorCode = ErrorCodes.parseErrorCode(codeStr);
            
            if ("0".equals(codeStr)) {
                Log.i(TAG, "Pairing SUCCESS!");
                
                // Reset retry count on success
                retryCount = 0;
                isRetrying = false;
                
                // Mark as paired
                PairingCodeGenerator.markAsPaired(this);
                
                if (connectionListener != null) {
                    connectionListener.onPairingSuccess();
                }
            } else {
                String errorMsg = ErrorCodes.getMessage(errorCode);
                Log.e(TAG, "Pairing FAILED: code=" + errorCode +
                    " (" + ErrorCodes.getEnglishMessage(errorCode) + ")");
                
                if (connectionListener != null) {
                    connectionListener.onPairingFailed(errorMsg);
                }
                
                // Retry if error is retryable
                if (ErrorCodes.isRetryable(errorCode)) {
                    scheduleReconnect(errorCode);
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse Authorize response: " + e.getMessage(), e);
            String errorMsg = ErrorCodes.getMessage(ErrorCodes.INVALID_MESSAGE_FORMAT);
            if (connectionListener != null) {
                connectionListener.onPairingFailed(errorMsg);
            }
        }
    }
    
    /**
     * Send text message (sau khi paired)
     */
    public void sendTextMessage(String text) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "Cannot send message - not connected");
            return;
        }
        
        try {
            JSONObject message = new JSONObject();
            
            JSONObject header = new JSONObject();
            header.put("name", "Recognize");
            header.put("namespace", "ai.xiaoai.recognizer");
            header.put("message_id", UUID.randomUUID().toString());
            
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            
            message.put("header", header);
            message.put("payload", payload);
            
            String json = message.toString();
            Log.d(TAG, "Sending text: " + json);
            webSocketClient.send(json);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send text: " + e.getMessage(), e);
        }
    }
    
    /**
     * Schedule reconnect với exponential backoff
     */
    private void scheduleReconnect(final int errorCode) {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries reached. Giving up.");
            isRetrying = false;
            retryCount = 0;
            
            String errorMsg = ErrorCodes.getMessage(ErrorCodes.SERVER_UNAVAILABLE);
            if (connectionListener != null) {
                connectionListener.onError(errorMsg);
            }
            return;
        }
        
        isRetrying = true;
        int delay = ErrorCodes.getRetryDelay(errorCode, retryCount);
        retryCount++;
        
        Log.i(TAG, "Scheduling reconnect #" + retryCount + " in " + delay + "ms");
        
        if (retryHandler != null) {
            retryHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "Retrying connection...");
                    connect();
                }
            }, delay);
        }
    }
    
    /**
     * Cancel scheduled retries
     */
    private void cancelRetries() {
        isRetrying = false;
        retryCount = 0;
        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }
    }
    
    /**
     * Disconnect (manual - no auto retry)
     */
    public void disconnect() {
        cancelRetries();
        
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
            Log.i(TAG, "Disconnected");
        }
    }
    
    /**
     * Check connection status
     */
    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }
    
    @Override
    public void onDestroy() {
        cancelRetries();
        disconnect();
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }
}