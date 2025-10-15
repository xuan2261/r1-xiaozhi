package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
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
 */
public class XiaozhiConnectionService extends Service {
    
    private static final String TAG = "XiaozhiConnection";
    private WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private ConnectionListener connectionListener;
    
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
                }
                
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);
                    if (connectionListener != null) {
                        connectionListener.onError(ex.getMessage());
                    }
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
            String code = payload.optString("code", "-1");
            String errorMessage = payload.optString("message", "Unknown error");
            
            if ("0".equals(code)) {
                Log.i(TAG, "Pairing SUCCESS!");
                
                // Mark as paired
                PairingCodeGenerator.markAsPaired(this);
                
                if (connectionListener != null) {
                    connectionListener.onPairingSuccess();
                }
            } else {
                Log.e(TAG, "Pairing FAILED: code=" + code + ", message=" + errorMessage);
                
                if (connectionListener != null) {
                    connectionListener.onPairingFailed("Code: " + code + ", " + errorMessage);
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse Authorize response: " + e.getMessage(), e);
            if (connectionListener != null) {
                connectionListener.onPairingFailed("Invalid response format");
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
     * Disconnect
     */
    public void disconnect() {
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
        disconnect();
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }
}