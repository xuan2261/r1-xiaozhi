package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.activation.DeviceActivator;
import com.phicomm.r1.xiaozhi.activation.DeviceFingerprint;
import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.core.DeviceState;
import com.phicomm.r1.xiaozhi.core.EventBus;
import com.phicomm.r1.xiaozhi.core.ListeningMode;
import com.phicomm.r1.xiaozhi.core.XiaozhiCore;
import com.phicomm.r1.xiaozhi.events.ConnectionEvent;
import com.phicomm.r1.xiaozhi.events.MessageReceivedEvent;
import com.phicomm.r1.xiaozhi.util.ErrorCodes;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý kết nối WebSocket với Xiaozhi Cloud
 *
 * UPDATED: Sử dụng py-xiaozhi authentication method
 * - Token-based authentication (Bearer token trong WebSocket header)
 * - Device activation flow với HMAC challenge-response
 * - Hello message thay vì Authorize handshake
 *
 * Refactored để sử dụng XiaozhiCore và EventBus
 */
public class XiaozhiConnectionService extends Service {
    
    private static final String TAG = "XiaozhiConnection";
    private static final int MAX_RETRIES = 3;
    
    private WebSocketClient webSocketClient;
    private final IBinder binder = new LocalBinder();
    private ConnectionListener connectionListener;
    
    // XiaozhiCore và EventBus
    private XiaozhiCore core;
    private EventBus eventBus;
    
    // Device activation
    private DeviceActivator deviceActivator;
    private DeviceFingerprint deviceFingerprint;
    
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
        void onActivationRequired(String verificationCode);
        void onActivationProgress(int attempt, int maxAttempts);
        void onPairingSuccess();
        void onPairingFailed(String error);
        void onMessage(String message);
        void onError(String error);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Get XiaozhiCore instance
        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        
        // Initialize device activation
        deviceFingerprint = DeviceFingerprint.getInstance(this);
        deviceActivator = new DeviceActivator(this);
        
        // Setup activation listener
        deviceActivator.setListener(new DeviceActivator.ActivationListener() {
            @Override
            public void onActivationStarted(String verificationCode) {
                Log.i(TAG, "Activation started - code: " + verificationCode);
                if (connectionListener != null) {
                    connectionListener.onActivationRequired(verificationCode);
                }
            }
            
            @Override
            public void onActivationProgress(int attempt, int maxAttempts) {
                Log.d(TAG, "Activation progress: " + attempt + "/" + maxAttempts);
                if (connectionListener != null) {
                    connectionListener.onActivationProgress(attempt, maxAttempts);
                }
            }
            
            @Override
            public void onActivationSuccess(String accessToken) {
                Log.i(TAG, "Activation successful!");
                // Auto connect with token
                connectWithToken(accessToken);
            }
            
            @Override
            public void onActivationFailed(String error) {
                Log.e(TAG, "Activation failed: " + error);
                if (connectionListener != null) {
                    connectionListener.onPairingFailed(error);
                }
            }
        });
        
        // Register this service với core
        core.setConnectionService(this);
        
        Log.i(TAG, "Service created and registered with XiaozhiCore");
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
     * Connect to Xiaozhi Cloud
     * Sử dụng py-xiaozhi method:
     * 1. Check if device is activated
     * 2. If not activated -> start activation flow
     * 3. If activated -> connect with token
     */
    public void connect() {
        // Check if already connected
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.w(TAG, "Already connected");
            return;
        }
        
        // Check activation status
        if (!deviceActivator.isActivated()) {
            Log.i(TAG, "Device not activated - starting activation flow");
            deviceActivator.startActivation();
            return;
        }
        
        // Get access token
        String accessToken = deviceActivator.getAccessToken();
        if (accessToken == null) {
            Log.e(TAG, "No access token available");
            if (connectionListener != null) {
                connectionListener.onError("No access token - please activate device");
            }
            return;
        }
        
        // Connect with token
        connectWithToken(accessToken);
    }
    
    /**
     * Connect to WebSocket with Bearer token
     * py-xiaozhi method: Token trong WebSocket header
     */
    private void connectWithToken(final String accessToken) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.w(TAG, "Already connected");
            return;
        }
        
        try {
            URI serverUri = new URI(XiaozhiConfig.WEBSOCKET_URL);
            Log.i(TAG, "Connecting with token to: " + serverUri);
            
            // Create headers with Bearer token
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + accessToken);
            
            webSocketClient = new WebSocketClient(serverUri, headers) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "WebSocket connected with token");
                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }
                    
                    // Send hello message (py-xiaozhi method)
                    sendHelloMessage();
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
     * Send hello message (py-xiaozhi method)
     * Format:
     * {
     *   "header": {
     *     "name": "hello",
     *     "namespace": "ai.xiaoai.common",
     *     "message_id": "uuid"
     *   },
     *   "payload": {
     *     "device_id": "MAC_ADDRESS",
     *     "serial_number": "SN-HASH-MAC",
     *     "device_type": "android",
     *     "os_version": "11",
     *     "app_version": "1.0.0"
     *   }
     * }
     */
    private void sendHelloMessage() {
        try {
            String deviceId = deviceFingerprint.getMacAddress();
            String serialNumber = deviceFingerprint.getSerialNumber();
            
            JSONObject message = new JSONObject();
            
            // Header
            JSONObject header = new JSONObject();
            header.put("name", "hello");
            header.put("namespace", "ai.xiaoai.common");
            header.put("message_id", UUID.randomUUID().toString());
            
            // Payload
            JSONObject payload = new JSONObject();
            payload.put("device_id", deviceId);
            payload.put("serial_number", serialNumber);
            payload.put("device_type", "android");
            payload.put("os_version", android.os.Build.VERSION.RELEASE);
            payload.put("app_version", "1.0.0");
            
            message.put("header", header);
            message.put("payload", payload);
            
            String json = message.toString();
            Log.i(TAG, "=== HELLO MESSAGE (py-xiaozhi) ===");
            Log.i(TAG, "Device ID: " + deviceId);
            Log.i(TAG, "Serial Number: " + serialNumber);
            Log.i(TAG, "JSON: " + json);
            Log.i(TAG, "==================================");
            webSocketClient.send(json);
            
            // Mark as paired after successful hello
            core.setDeviceState(DeviceState.IDLE);
            eventBus.post(new ConnectionEvent(true, "Connected with py-xiaozhi method"));
            
            if (connectionListener != null) {
                connectionListener.onPairingSuccess();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send hello message: " + e.getMessage(), e);
            if (connectionListener != null) {
                connectionListener.onError("Hello message failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle message từ server
     */
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            
            // Broadcast message received event
            eventBus.post(new MessageReceivedEvent(json));
            
            // Handle TTS messages
            String type = json.optString("type");
            if ("tts".equals(type)) {
                handleTTSMessage(json);
            }
            
            // Handle other message types here
            Log.d(TAG, "Message type: " + type);
            
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse message: " + e.getMessage());
        }
    }
    
    /**
     * Handle TTS messages theo logic py-xiaozhi
     */
    private void handleTTSMessage(JSONObject json) {
        try {
            String state = json.optString("state");
            
            if ("start".equals(state)) {
                // Check listening mode
                if (core.isKeepListening() &&
                    core.getListeningMode() == ListeningMode.REALTIME) {
                    // Keep listening during TTS in realtime mode
                    core.setDeviceState(DeviceState.LISTENING);
                } else {
                    core.setDeviceState(DeviceState.SPEAKING);
                }
            } else if ("stop".equals(state)) {
                if (core.isKeepListening()) {
                    // Resume listening
                    core.setDeviceState(DeviceState.LISTENING);
                    // Restart listening theo py-xiaozhi logic
                    sendStartListening(core.getListeningMode());
                } else {
                    core.setDeviceState(DeviceState.IDLE);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling TTS message", e);
        }
    }
    
    
    /**
     * Send start listening message theo py-xiaozhi
     */
    public void sendStartListening(ListeningMode mode) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "Cannot send message - not connected");
            return;
        }
        
        try {
            JSONObject message = new JSONObject();
            
            JSONObject header = new JSONObject();
            header.put("name", "StartListening");
            header.put("namespace", "ai.xiaoai.recognizer");
            header.put("message_id", UUID.randomUUID().toString());
            
            JSONObject payload = new JSONObject();
            payload.put("mode", mode.getValue());
            
            message.put("header", header);
            message.put("payload", payload);
            
            String json = message.toString();
            Log.d(TAG, "Sending StartListening: " + json);
            webSocketClient.send(json);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send StartListening: " + e.getMessage(), e);
        }
    }
    
    /**
     * Send stop listening message
     */
    public void sendStopListening() {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "Cannot send message - not connected");
            return;
        }
        
        try {
            JSONObject message = new JSONObject();
            
            JSONObject header = new JSONObject();
            header.put("name", "StopListening");
            header.put("namespace", "ai.xiaoai.recognizer");
            header.put("message_id", UUID.randomUUID().toString());
            
            message.put("header", header);
            message.put("payload", new JSONObject());
            
            String json = message.toString();
            Log.d(TAG, "Sending StopListening: " + json);
            webSocketClient.send(json);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send StopListening: " + e.getMessage(), e);
        }
    }
    
    /**
     * Send abort speaking message
     */
    public void sendAbortSpeaking(String reason) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.w(TAG, "Cannot send message - not connected");
            return;
        }
        
        try {
            JSONObject message = new JSONObject();
            
            JSONObject header = new JSONObject();
            header.put("name", "AbortSpeaking");
            header.put("namespace", "ai.xiaoai.tts");
            header.put("message_id", UUID.randomUUID().toString());
            
            JSONObject payload = new JSONObject();
            if (reason != null) {
                payload.put("reason", reason);
            }
            
            message.put("header", header);
            message.put("payload", payload);
            
            String json = message.toString();
            Log.d(TAG, "Sending AbortSpeaking: " + json);
            webSocketClient.send(json);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to send AbortSpeaking: " + e.getMessage(), e);
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
    
    /**
     * Check if device is activated
     */
    public boolean isActivated() {
        return deviceActivator != null && deviceActivator.isActivated();
    }
    
    /**
     * Start device activation manually
     */
    public void startActivation() {
        if (deviceActivator != null) {
            deviceActivator.startActivation();
        }
    }
    
    /**
     * Cancel device activation
     */
    public void cancelActivation() {
        if (deviceActivator != null) {
            deviceActivator.cancelActivation();
        }
    }
    
    /**
     * Reset activation (for testing)
     */
    public void resetActivation() {
        if (deviceActivator != null) {
            deviceActivator.resetActivation();
        }
    }
    
    @Override
    public void onDestroy() {
        cancelRetries();
        disconnect();
        
        // Unregister from core
        if (core != null) {
            core.setConnectionService(null);
        }
        
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }
}