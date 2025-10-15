package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * HTTP Server đơn giản để expose pairing code qua REST API
 * Theo ESP32: Code gen LOCAL, không có async API calls
 */
public class HTTPServerService extends Service {
    
    private static final String TAG = "HTTPServer";
    private static final int PORT = 8080;
    
    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isRunning = false;
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            startServer();
        }
        return START_STICKY;
    }
    
    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    isRunning = true;
                    Log.i(TAG, "HTTP Server started on port " + PORT);
                    
                    while (isRunning && !serverSocket.isClosed()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            handleClient(clientSocket);
                        } catch (IOException e) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting client: " + e.getMessage());
                            }
                        }
                    }
                    
                } catch (IOException e) {
                    Log.e(TAG, "Failed to start server: " + e.getMessage(), e);
                } finally {
                    isRunning = false;
                }
            }
        });
        serverThread.start();
    }
    
    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            
            // Read request line
            String requestLine = reader.readLine();
            if (requestLine == null) {
                clientSocket.close();
                return;
            }
            
            Log.d(TAG, "Request: " + requestLine);
            
            // Skip headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip
            }
            
            // Parse request
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(writer, 400, "Bad Request");
                clientSocket.close();
                return;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            // Route request
            if ("GET".equals(method) && "/pairing-code".equals(path)) {
                servePairingCode(writer);
            } else if ("GET".equals(method) && "/status".equals(path)) {
                serveStatus(writer);
            } else if ("POST".equals(method) && "/reset".equals(path)) {
                serveResetPairing(writer);
            } else {
                sendResponse(writer, 404, "Not Found");
            }
            
            clientSocket.close();
            
        } catch (IOException e) {
            Log.e(TAG, "Error handling client: " + e.getMessage());
        }
    }
    
    /**
     * GET /pairing-code
     * Trả về pairing code LOCAL (không API call)
     */
    private void servePairingCode(PrintWriter writer) {
        try {
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            String pairingCode = PairingCodeGenerator.getPairingCode(this);
            boolean isPaired = PairingCodeGenerator.isPaired(this);
            
            JSONObject response = new JSONObject();
            response.put("device_id", deviceId);
            response.put("pairing_code", pairingCode);
            response.put("paired", isPaired);
            
            sendJsonResponse(writer, 200, response.toString());
            Log.d(TAG, "Served pairing code: " + pairingCode);
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }
    
    /**
     * GET /status
     * Trả về trạng thái pairing
     */
    private void serveStatus(PrintWriter writer) {
        try {
            boolean isPaired = PairingCodeGenerator.isPaired(this);
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            
            JSONObject response = new JSONObject();
            response.put("paired", isPaired);
            response.put("device_id", deviceId);
            response.put("status", isPaired ? "paired" : "not_paired");
            
            sendJsonResponse(writer, 200, response.toString());
            Log.d(TAG, "Served status: " + (isPaired ? "paired" : "not_paired"));
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }
    
    /**
     * POST /reset
     * Reset pairing status - đơn giản, KHÔNG có async
     */
    private void serveResetPairing(PrintWriter writer) {
        PairingCodeGenerator.resetPairing(this);
        
        try {
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Pairing reset successfully");
            
            sendJsonResponse(writer, 200, response.toString());
            Log.i(TAG, "Pairing reset via HTTP");
            
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON response: " + e.getMessage());
            sendResponse(writer, 500, "Internal Server Error");
        }
    }
    
    private void sendResponse(PrintWriter writer, int statusCode, String statusMessage) {
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Content-Type: text/plain");
        writer.println("Connection: close");
        writer.println();
        writer.println(statusMessage);
    }
    
    private void sendJsonResponse(PrintWriter writer, int statusCode, String json) {
        String statusMessage = statusCode == 200 ? "OK" : "Error";
        writer.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        writer.println("Content-Type: application/json");
        writer.println("Content-Length: " + json.length());
        writer.println("Connection: close");
        writer.println();
        writer.println(json);
    }
    
    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
        Log.i(TAG, "HTTP Server stopped");
    }
    
    private void stopServer() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket: " + e.getMessage());
            }
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }
}