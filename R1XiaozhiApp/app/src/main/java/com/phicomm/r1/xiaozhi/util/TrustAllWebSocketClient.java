package com.phicomm.r1.xiaozhi.util;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.Socket;
import java.net.URI;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * Custom WebSocketClient that bypasses SSL certificate validation
 * 
 * ⚠️ WARNING: FOR TESTING ONLY - NEVER USE IN PRODUCTION!
 * 
 * This class overrides socket creation to use TrustAllCertificates SSLSocketFactory
 */
public abstract class TrustAllWebSocketClient extends WebSocketClient {
    
    private static final String TAG = "TrustAllWSClient";
    
    public TrustAllWebSocketClient(URI serverUri, Map<String, String> httpHeaders) {
        super(serverUri, httpHeaders);
    }
    
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // To be implemented by subclass
    }
    
    @Override
    public void onMessage(String message) {
        // To be implemented by subclass
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        // To be implemented by subclass
    }
    
    @Override
    public void onError(Exception ex) {
        // To be implemented by subclass
    }
    
    /**
     * Override socket creation to use custom SSLSocketFactory
     * This is the ONLY reliable way to bypass SSL in Java-WebSocket 1.3.9
     */
    @Override
    protected Socket createSocket() {
        try {
            if (getURI().getScheme().equals("wss")) {
                Log.d(TAG, "Creating SSL socket with TrustAll factory");
                SSLSocketFactory factory = TrustAllCertificates.getSSLSocketFactory();
                return factory.createSocket();
            } else {
                Log.d(TAG, "Creating regular socket (non-SSL)");
                return super.createSocket();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create custom SSL socket", e);
            // Fallback to default
            return super.createSocket();
        }
    }
}