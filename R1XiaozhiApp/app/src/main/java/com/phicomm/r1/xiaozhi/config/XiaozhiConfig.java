package com.phicomm.r1.xiaozhi.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cấu hình Xiaozhi với khả năng chuyển đổi giữa Cloud và Self-hosted
 */
public class XiaozhiConfig {
    
    // Constants for XiaozhiConnectionService
    public static final String WEBSOCKET_URL = "wss://xiaozhi.me/v1/ws";
    public static final String CLIENT_ID = "1000013";
    
    // SSL Certificate Validation
    // ⚠️ WARNING: Set to true ONLY for testing with expired certificates!
    // ⚠️ NEVER enable in production - serious security risk!
    // Server certificate expired: Nov 08, 2024
    // Enable this to bypass SSL validation for testing purposes
    public static final boolean BYPASS_SSL_VALIDATION = true; // TODO: Set false in production!
    
    private static final String PREFS_NAME = "xiaozhi_config";
    private static final String KEY_USE_CLOUD = "use_cloud";
    private static final String KEY_CLOUD_URL = "cloud_url";
    private static final String KEY_SELF_HOSTED_URL = "self_hosted_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_WAKE_WORD = "wake_word";
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_LED_ENABLED = "led_enabled";
    private static final String KEY_HTTP_SERVER_PORT = "http_server_port";
    
    // Default values
    public static final String DEFAULT_CLOUD_URL = "wss://xiaozhi.me/v1/ws";
    public static final String DEFAULT_SELF_HOSTED_URL = "ws://192.168.1.100:8080/websocket";
    public static final String DEFAULT_WAKE_WORD = "小智";
    public static final int DEFAULT_HTTP_PORT = 8088;
    
    private SharedPreferences prefs;
    
    public XiaozhiConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Use Cloud API (Primary)
    public boolean isUseCloud() {
        return prefs.getBoolean(KEY_USE_CLOUD, true);
    }
    
    public void setUseCloud(boolean useCloud) {
        prefs.edit().putBoolean(KEY_USE_CLOUD, useCloud).apply();
    }
    
    // Cloud URL
    public String getCloudUrl() {
        return prefs.getString(KEY_CLOUD_URL, DEFAULT_CLOUD_URL);
    }
    
    public void setCloudUrl(String url) {
        prefs.edit().putString(KEY_CLOUD_URL, url).apply();
    }
    
    // Self-hosted URL (Fallback)
    public String getSelfHostedUrl() {
        return prefs.getString(KEY_SELF_HOSTED_URL, DEFAULT_SELF_HOSTED_URL);
    }
    
    public void setSelfHostedUrl(String url) {
        prefs.edit().putString(KEY_SELF_HOSTED_URL, url).apply();
    }
    
    // Get active URL based on current mode
    public String getActiveUrl() {
        return isUseCloud() ? getCloudUrl() : getSelfHostedUrl();
    }
    
    // API Key (optional for cloud)
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }
    
    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }
    
    // Wake Word
    public String getWakeWord() {
        return prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD);
    }
    
    public void setWakeWord(String wakeWord) {
        prefs.edit().putString(KEY_WAKE_WORD, wakeWord).apply();
    }
    
    // Auto Start on Boot
    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, true);
    }
    
    public void setAutoStart(boolean autoStart) {
        prefs.edit().putBoolean(KEY_AUTO_START, autoStart).apply();
    }
    
    // LED Control
    public boolean isLedEnabled() {
        return prefs.getBoolean(KEY_LED_ENABLED, true);
    }
    
    public void setLedEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LED_ENABLED, enabled).apply();
    }
    
    // HTTP Server Port
    public int getHttpServerPort() {
        return prefs.getInt(KEY_HTTP_SERVER_PORT, DEFAULT_HTTP_PORT);
    }
    
    public void setHttpServerPort(int port) {
        prefs.edit().putInt(KEY_HTTP_SERVER_PORT, port).apply();
    }
    
    // Reset to defaults
    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
    
    // Export config as JSON string
    public String exportConfig() {
        return String.format(
            "{\"use_cloud\":%b,\"cloud_url\":\"%s\",\"self_hosted_url\":\"%s\",\"wake_word\":\"%s\",\"auto_start\":%b,\"led_enabled\":%b,\"http_port\":%d}",
            isUseCloud(),
            getCloudUrl(),
            getSelfHostedUrl(),
            getWakeWord(),
            isAutoStart(),
            isLedEnabled(),
            getHttpServerPort()
        );
    }
}