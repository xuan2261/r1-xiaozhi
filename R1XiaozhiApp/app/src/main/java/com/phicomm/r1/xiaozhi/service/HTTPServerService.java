package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

import java.io.IOException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP Server để remote control và xem thông tin thiết bị
 * Endpoints:
 * - GET /status - Xem trạng thái
 * - GET /pairing - Xem pairing code
 * - GET /start - Khởi động services
 * - GET /stop - Dừng services
 * - GET /config - Xem cấu hình
 */
public class HTTPServerService extends Service {
    
    private static final String TAG = "HTTPServer";
    private XiaozhiHTTPServer server;
    private XiaozhiConfig config;
    
    @Override
    public void onCreate() {
        super.onCreate();
        config = new XiaozhiConfig(this);
        
        try {
            int port = config.getHttpServerPort();
            server = new XiaozhiHTTPServer(port);
            server.start();
            Log.d(TAG, "HTTP Server started on port " + port);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start HTTP server", e);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            Log.d(TAG, "HTTP Server stopped");
        }
        super.onDestroy();
    }
    
    /**
     * NanoHTTPD Server implementation
     */
    private class XiaozhiHTTPServer extends NanoHTTPD {
        
        public XiaozhiHTTPServer(int port) {
            super(port);
        }
        
        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Method method = session.getMethod();
            
            Log.d(TAG, "HTTP Request: " + method + " " + uri);
            
            try {
                switch (uri) {
                    case "/":
                        return serveHomePage();
                    case "/status":
                        return serveStatus();
                    case "/pairing":
                        return servePairingCode();
                    case "/reset-pairing":
                        return serveResetPairing();
                    case "/start":
                        return serveStartCommand();
                    case "/stop":
                        return serveStopCommand();
                    case "/config":
                        return serveConfig();
                    default:
                        return newFixedLengthResponse(Response.Status.NOT_FOUND,
                            MIME_PLAINTEXT, "404 Not Found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error serving request", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT, "500 Internal Server Error: " + e.getMessage());
            }
        }
        
        private Response serveHomePage() {
            String pairingCode = PairingCodeGenerator.getPairingCode(HTTPServerService.this);
            String deviceId = PairingCodeGenerator.getDeviceId(HTTPServerService.this);
            
            String html = "<html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Xiaozhi R1</title>" +
                "<style>" +
                "body { font-family: Arial; margin: 40px; background: #f5f5f5; }" +
                ".container { max-width: 600px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }" +
                "h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }" +
                ".pairing-code { font-size: 48px; font-weight: bold; color: #4CAF50; text-align: center; padding: 20px; background: #f0f0f0; border-radius: 5px; margin: 20px 0; letter-spacing: 5px; }" +
                ".info { background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 10px 0; }" +
                ".label { font-weight: bold; color: #666; }" +
                ".value { color: #333; margin-left: 10px; }" +
                "a { display: inline-block; margin: 5px; padding: 10px 20px; background: #4CAF50; color: white; text-decoration: none; border-radius: 5px; }" +
                "a:hover { background: #45a049; }" +
                ".footer { text-align: center; margin-top: 30px; color: #999; font-size: 12px; }" +
                "</style>" +
                "</head><body>" +
                "<div class='container'>" +
                "<h1>🎙️ Xiaozhi Voice Assistant</h1>" +
                "<h2>📱 Pairing Code</h2>" +
                "<div class='pairing-code'>" + PairingCodeGenerator.formatPairingCode(pairingCode) + "</div>" +
                "<div class='info'>" +
                "<span class='label'>Device ID:</span><span class='value'>" + deviceId + "</span><br>" +
                "<span class='label'>Device:</span><span class='value'>Phicomm R1</span><br>" +
                "<span class='label'>Wake Word:</span><span class='value'>" + config.getWakeWord() + "</span>" +
                "</div>" +
                "<h3>📋 Hướng dẫn:</h3>" +
                "<ol>" +
                "<li>Truy cập <a href='https://xiaozhi.me/console/agents' target='_blank'>Xiaozhi Console</a></li>" +
                "<li>Tạo Agent mới hoặc chọn Agent hiện có</li>" +
                "<li>Click <strong>\"Add Device\"</strong></li>" +
                "<li>Nhập mã pairing code ở trên</li>" +
                "<li>✅ Hoàn tất!</li>" +
                "</ol>" +
                "<h3>🔧 Control Panel:</h3>" +
                "<a href='/status'>Status</a>" +
                "<a href='/pairing'>Pairing Info</a>" +
                "<a href='/reset-pairing' style='background:#ff9800'>Reset Pairing</a>" +
                "<a href='/config'>Config</a>" +
                "<a href='/start'>Start</a>" +
                "<a href='/stop'>Stop</a>" +
                "<div class='footer'>Xiaozhi R1 HTTP Server v1.0</div>" +
                "</div>" +
                "</body></html>";
            
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html);
        }
        
        private Response servePairingCode() {
            String pairingCode = PairingCodeGenerator.getPairingCode(HTTPServerService.this);
            String deviceId = PairingCodeGenerator.getDeviceId(HTTPServerService.this);
            
            String json = "{" +
                "\"pairing_code\":\"" + pairingCode + "\"," +
                "\"pairing_code_formatted\":\"" + PairingCodeGenerator.formatPairingCode(pairingCode) + "\"," +
                "\"device_id\":\"" + deviceId + "\"," +
                "\"device\":\"Phicomm R1\"," +
                "\"wake_word\":\"" + config.getWakeWord() + "\"" +
                "}";
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        }
        
        private Response serveResetPairing() {
            // Reset pairing code
            String newCode = PairingCodeGenerator.resetPairingCode(HTTPServerService.this);
            String deviceId = PairingCodeGenerator.getDeviceId(HTTPServerService.this);
            
            String json = "{" +
                "\"status\":\"success\"," +
                "\"message\":\"Pairing code reset successfully\"," +
                "\"new_pairing_code\":\"" + newCode + "\"," +
                "\"pairing_code_formatted\":\"" + PairingCodeGenerator.formatPairingCode(newCode) + "\"," +
                "\"device_id\":\"" + deviceId + "\"" +
                "}";
            
            Log.i(TAG, "===========================================");
            Log.i(TAG, "PAIRING CODE RESET");
            Log.i(TAG, "NEW CODE: " + newCode);
            Log.i(TAG, "===========================================");
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        }
        
        private Response serveStatus() {
            String json = "{" +
                "\"status\":\"running\"," +
                "\"device\":\"Phicomm R1\"," +
                "\"version\":\"1.0.0\"" +
                "}";
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        }
        
        private Response serveStartCommand() {
            // Start services
            Intent voiceIntent = new Intent(HTTPServerService.this, VoiceRecognitionService.class);
            startService(voiceIntent);
            
            Intent xiaozhiIntent = new Intent(HTTPServerService.this, XiaozhiConnectionService.class);
            startService(xiaozhiIntent);
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", 
                "{\"status\":\"started\"}");
        }
        
        private Response serveStopCommand() {
            // Stop services
            Intent voiceIntent = new Intent(HTTPServerService.this, VoiceRecognitionService.class);
            stopService(voiceIntent);
            
            Intent xiaozhiIntent = new Intent(HTTPServerService.this, XiaozhiConnectionService.class);
            stopService(xiaozhiIntent);
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", 
                "{\"status\":\"stopped\"}");
        }
        
        private Response serveConfig() {
            String json = "{" +
                "\"use_cloud\":" + config.isUseCloud() + "," +
                "\"cloud_url\":\"" + config.getCloudUrl() + "\"," +
                "\"self_hosted_url\":\"" + config.getSelfHostedUrl() + "\"," +
                "\"wake_word\":\"" + config.getWakeWord() + "\"," +
                "\"auto_start\":" + config.isAutoStart() + "," +
                "\"led_enabled\":" + config.isLedEnabled() + "," +
                "\"http_server_port\":" + config.getHttpServerPort() +
                "}";
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        }
    }
}