package com.phicomm.r1.xiaozhi.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.phicomm.r1.xiaozhi.R;
import com.phicomm.r1.xiaozhi.service.AudioPlaybackService;
import com.phicomm.r1.xiaozhi.service.HTTPServerService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

/**
 * MainActivity đơn giản theo chuẩn ESP32
 * - Hiển thị pairing code LOCAL (không API)
 * - Connect WebSocket và wait Authorize response
 * - Không polling - callback-driven
 */
public class MainActivity extends Activity {
    
    private static final String TAG = "MainActivity";
    
    private TextView statusText;
    private TextView pairingCodeText;
    private Button connectButton;
    private Button resetButton;
    
    private XiaozhiConnectionService xiaozhiService;
    private boolean xiaozhiBound = false;
    
    private final ServiceConnection xiaozhiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XiaozhiConnectionService.LocalBinder binder = 
                (XiaozhiConnectionService.LocalBinder) service;
            xiaozhiService = binder.getService();
            xiaozhiBound = true;
            
            // Setup listener
            xiaozhiService.setConnectionListener(new XiaozhiConnectionService.ConnectionListener() {
                @Override
                public void onConnected() {
                    runOnUiThread(() -> {
                        updateStatus("Đã kết nối - Đang xác thực...");
                        connectButton.setEnabled(false);
                    });
                }
                
                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> {
                        updateStatus("Mất kết nối");
                        connectButton.setEnabled(true);
                    });
                }
                
                @Override
                public void onPairingSuccess() {
                    runOnUiThread(() -> {
                        updateStatus("✓ Đã ghép nối thành công!");
                        Toast.makeText(MainActivity.this, 
                            "Pairing thành công! Có thể dùng giọng nói.", 
                            Toast.LENGTH_LONG).show();
                        pairingCodeText.setText("Đã ghép nối");
                        connectButton.setEnabled(false);
                    });
                }
                
                @Override
                public void onPairingFailed(String error) {
                    runOnUiThread(() -> {
                        updateStatus("✗ Ghép nối thất bại: " + error);
                        Toast.makeText(MainActivity.this, 
                            "Pairing thất bại: " + error, 
                            Toast.LENGTH_LONG).show();
                        connectButton.setEnabled(true);
                    });
                }
                
                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Received: " + message);
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        updateStatus("Lỗi: " + error);
                        Toast.makeText(MainActivity.this, 
                            "Lỗi: " + error, 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            });
            
            Log.i(TAG, "Xiaozhi service bound");
            checkPairingStatus();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            xiaozhiBound = false;
            Log.i(TAG, "Xiaozhi service unbound");
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        statusText = findViewById(R.id.statusText);
        pairingCodeText = findViewById(R.id.pairingCodeText);
        connectButton = findViewById(R.id.connectButton);
        resetButton = findViewById(R.id.resetButton);
        
        // Setup buttons
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToXiaozhi();
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPairing();
            }
        });
        
        // Start all services
        startService(new Intent(this, VoiceRecognitionService.class));
        startService(new Intent(this, AudioPlaybackService.class));
        startService(new Intent(this, LEDControlService.class));
        startService(new Intent(this, HTTPServerService.class));
        
        // Bind to Xiaozhi service
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        startService(xiaozhiIntent);
        bindService(xiaozhiIntent, xiaozhiConnection, Context.BIND_AUTO_CREATE);
        
        Log.i(TAG, "MainActivity created");
    }
    
    /**
     * Check pairing status và hiển thị code
     */
    private void checkPairingStatus() {
        boolean isPaired = PairingCodeGenerator.isPaired(this);
        
        if (isPaired) {
            updateStatus("Đã ghép nối - Sẵn sàng");
            pairingCodeText.setText("Đã ghép nối");
            connectButton.setEnabled(false);
        } else {
            // Hiển thị pairing code LOCAL
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            String pairingCode = PairingCodeGenerator.getPairingCode(this);
            String formattedCode = PairingCodeGenerator.formatPairingCode(pairingCode);
            
            updateStatus("Chưa ghép nối - Nhập mã vào console.xiaozhi.ai");
            pairingCodeText.setText("Mã ghép nối: " + formattedCode);
            connectButton.setEnabled(true);
            
            Log.i(TAG, "Device ID: " + deviceId);
            Log.i(TAG, "Pairing code: " + pairingCode);
        }
    }
    
    /**
     * Connect to Xiaozhi và gửi Authorize handshake
     */
    private void connectToXiaozhi() {
        if (!xiaozhiBound) {
            Toast.makeText(this, "Service chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("Đang kết nối...");
        connectButton.setEnabled(false);
        
        // Connect sẽ tự động gửi Authorize handshake
        xiaozhiService.connect();
        
        Log.i(TAG, "Connecting to Xiaozhi...");
    }
    
    /**
     * Reset pairing status
     */
    private void resetPairing() {
        PairingCodeGenerator.resetPairing(this);
        
        if (xiaozhiBound && xiaozhiService.isConnected()) {
            xiaozhiService.disconnect();
        }
        
        checkPairingStatus();
        
        Toast.makeText(this, "Đã reset - Vui lòng ghép nối lại", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Pairing reset");
    }
    
    /**
     * Update status text
     */
    private void updateStatus(String status) {
        statusText.setText(status);
        Log.d(TAG, "Status: " + status);
    }
    
    @Override
    protected void onDestroy() {
        if (xiaozhiBound) {
            unbindService(xiaozhiConnection);
            xiaozhiBound = false;
        }
        super.onDestroy();
        Log.i(TAG, "MainActivity destroyed");
    }
}