package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Service kết nối với Xiaozhi API (Cloud hoặc Self-hosted)
 * Hỗ trợ tự động fallback từ Cloud sang Self-hosted khi cần
 */
public class XiaozhiConnectionService extends Service {
    
    private static final String TAG = "XiaozhiConnection";
    private static final int RECONNECT_DELAY = 5000; // 5 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    private WebSocket webSocket;
    private OkHttpClient client;
    private XiaozhiConfig config;
    private boolean isConnected = false;
    private boolean useCloudMode = true;
    private int retryAttempts = 0;
    private Handler reconnectHandler;
    
    private ConnectionCallback callback;
    
    public interface ConnectionCallback {
        void onConnected(boolean isCloud);
        void onDisconnected();
        void onMessageReceived(String message);
        void onAudioReceived(String audioUrl);
        void onError(String error);
    }
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public XiaozhiConnectionService getService() {
            return XiaozhiConnectionService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        config = new XiaozhiConfig(this);
        reconnectHandler = new Handler();
        
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES) // No timeout for WebSocket
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build();
            
        Log.d(TAG, "XiaozhiConnectionService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectToXiaozhi();
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public void setCallback(ConnectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Kết nối đến Xiaozhi API
     * Ưu tiên Cloud, fallback sang Self-hosted nếu thất bại
     */
    public void connectToXiaozhi() {
        useCloudMode = config.isUseCloud();
        String url = useCloudMode ? config.getCloudUrl() : config.getSelfHostedUrl();
        
        Log.d(TAG, "Connecting to Xiaozhi: " + url + " (Cloud: " + useCloudMode + ")");
        
        Request.Builder requestBuilder = new Request.Builder().url(url);
        
        // Add API key if available and using cloud
        if (useCloudMode && !config.getApiKey().isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + config.getApiKey());
        }
        
        Request request = requestBuilder.build();
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                retryAttempts = 0;
                Log.d(TAG, "WebSocket connected successfully");
                
                if (callback != null) {
                    callback.onConnected(useCloudMode);
                }
                
                // Send initial handshake
                sendHandshake();
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received message: " + text);
                handleXiaozhiResponse(text);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.d(TAG, "Received binary message: " + bytes.size() + " bytes");
                // Handle binary audio data if needed
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                webSocket.close(1000, null);
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                Log.d(TAG, "WebSocket closed: " + reason);
                
                if (callback != null) {
                    callback.onDisconnected();
                }
                
                scheduleReconnect();
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnected = false;
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                
                if (callback != null) {
                    callback.onError(t.getMessage());
                }
                
                // Try fallback if in cloud mode
                if (useCloudMode && retryAttempts < MAX_RETRY_ATTEMPTS) {
                    retryAttempts++;
                    Log.d(TAG, "Attempting fallback to self-hosted (attempt " + retryAttempts + ")");
                    useCloudMode = false;
                    scheduleReconnect();
                } else {
                    scheduleReconnect();
                }
            }
        });
    }
    
    /**
     * Send initial handshake to Xiaozhi server
     */
    private void sendHandshake() {
        try {
            JSONObject handshake = new JSONObject();
            handshake.put("type", "handshake");
            handshake.put("version", "1.0");
            handshake.put("device", "Phicomm R1");
            handshake.put("wake_word", config.getWakeWord());
            
            sendMessage(handshake.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating handshake", e);
        }
    }
    
    /**
     * Gửi audio data đến Xiaozhi để xử lý
     */
    public void sendAudioToXiaozhi(byte[] audioData, int sampleRate, int channels) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send audio");
            return;
        }
        
        try {
            String audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP);
            
            JSONObject message = new JSONObject();
            message.put("type", "audio");
            message.put("data", audioBase64);
            message.put("format", "pcm");
            message.put("sample_rate", sampleRate);
            message.put("channels", channels);
            message.put("bits_per_sample", 16);
            
            sendMessage(message.toString());
            Log.d(TAG, "Sent audio data: " + audioData.length + " bytes");
        } catch (JSONException e) {
            Log.e(TAG, "Error creating audio message", e);
        }
    }
    
    /**
     * Gửi text command đến Xiaozhi
     */
    public void sendTextCommand(String text) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send text");
            return;
        }
        
        try {
            JSONObject message = new JSONObject();
            message.put("type", "text");
            message.put("text", text);
            
            sendMessage(message.toString());
            Log.d(TAG, "Sent text command: " + text);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating text message", e);
        }
    }
    
    /**
     * Send message to WebSocket
     */
    private void sendMessage(String message) {
        if (webSocket != null && isConnected) {
            webSocket.send(message);
        }
    }
    
    /**
     * Xử lý response từ Xiaozhi server
     */
    private void handleXiaozhiResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);
            String type = json.getString("type");
            
            if (callback != null) {
                callback.onMessageReceived(response);
            }
            
            switch (type) {
                case "tts":
                case "audio":
                    // Audio response - URL hoặc base64
                    if (json.has("audio_url")) {
                        String audioUrl = json.getString("audio_url");
                        if (callback != null) {
                            callback.onAudioReceived(audioUrl);
                        }
                        
                        // Gửi đến AudioPlaybackService
                        Intent intent = new Intent(this, AudioPlaybackService.class);
                        intent.setAction(AudioPlaybackService.ACTION_PLAY_URL);
                        intent.putExtra("audio_url", audioUrl);
                        startService(intent);
                    } else if (json.has("audio_data")) {
                        // Base64 encoded audio
                        String audioBase64 = json.getString("audio_data");
                        byte[] audioData = Base64.decode(audioBase64, Base64.DEFAULT);
                        
                        Intent intent = new Intent(this, AudioPlaybackService.class);
                        intent.setAction(AudioPlaybackService.ACTION_PLAY_DATA);
                        intent.putExtra("audio_data", audioData);
                        startService(intent);
                    }
                    break;
                    
                case "text":
                    // Text response
                    String text = json.getString("text");
                    Log.d(TAG, "Received text: " + text);
                    break;
                    
                case "command":
                    // System command
                    String command = json.getString("command");
                    executeCommand(command, json);
                    break;
                    
                case "error":
                    String error = json.getString("message");
                    Log.e(TAG, "Server error: " + error);
                    if (callback != null) {
                        callback.onError(error);
                    }
                    break;
                    
                case "ping":
                    // Respond to ping
                    sendPong();
                    break;
                    
                default:
                    Log.w(TAG, "Unknown message type: " + type);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing response", e);
        }
    }
    
    /**
     * Execute system commands from Xiaozhi
     */
    private void executeCommand(String command, JSONObject data) {
        Log.d(TAG, "Executing command: " + command);
        
        Intent intent = new Intent("com.phicomm.r1.xiaozhi.COMMAND");
        intent.putExtra("command", command);
        intent.putExtra("data", data.toString());
        sendBroadcast(intent);
    }
    
    /**
     * Send pong response
     */
    private void sendPong() {
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "pong");
            sendMessage(pong.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating pong", e);
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        reconnectHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isConnected) {
                    Log.d(TAG, "Attempting to reconnect...");
                    connectToXiaozhi();
                }
            }
        }, RECONNECT_DELAY);
    }
    
    /**
     * Disconnect from Xiaozhi
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "User disconnect");
            webSocket = null;
        }
        isConnected = false;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public boolean isUsingCloud() {
        return useCloudMode;
    }
    
    @Override
    public void onDestroy() {
        disconnect();
        reconnectHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        Log.d(TAG, "XiaozhiConnectionService destroyed");
    }
}