package com.phicomm.r1.xiaozhi.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
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
    private TextView instructionsText;
    private Button connectButton;
    private Button copyButton;
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Da ket noi - Dang xac thuc...");
                            connectButton.setEnabled(false);
                        }
                    });
                }
                
                @Override
                public void onDisconnected() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Mat ket noi");
                            connectButton.setEnabled(true);
                        }
                    });
                }
                
                @Override
                public void onPairingSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("[OK] Da ghep noi thanh cong!");
                            Toast.makeText(MainActivity.this,
                                "Pairing thanh cong! Co the dung giong noi.",
                                Toast.LENGTH_LONG).show();
                            pairingCodeText.setText("Da Ghep Noi");
                            connectButton.setEnabled(false);
                        }
                    });
                }
                
                @Override
                public void onPairingFailed(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("[FAIL] Ghep noi that bai: " + error);
                            Toast.makeText(MainActivity.this,
                                "Pairing that bai: " + error,
                                Toast.LENGTH_LONG).show();
                            connectButton.setEnabled(true);
                        }
                    });
                }
                
                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Received: " + message);
                }
                
                @Override
                public void onError(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Loi: " + error);
                            Toast.makeText(MainActivity.this,
                                "Loi: " + error,
                                Toast.LENGTH_SHORT).show();
                        }
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
        
        // Initialize views - API 22 requires explicit cast
        statusText = (TextView) findViewById(R.id.statusText);
        pairingCodeText = (TextView) findViewById(R.id.pairingCodeText);
        instructionsText = (TextView) findViewById(R.id.instructionsText);
        connectButton = (Button) findViewById(R.id.connectButton);
        copyButton = (Button) findViewById(R.id.copyButton);
        resetButton = (Button) findViewById(R.id.resetButton);
        
        // Setup buttons
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToXiaozhi();
            }
        });
        
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyPairingCode();
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
            updateStatus("[OK] Da ghep noi - San sang su dung");
            pairingCodeText.setText("[OK] Da Ghep Noi");
            instructionsText.setVisibility(View.GONE);
            copyButton.setVisibility(View.GONE);
            connectButton.setEnabled(false);
        } else {
            // Hiển thị pairing code LOCAL
            String deviceId = PairingCodeGenerator.getDeviceId(this);
            String pairingCode = PairingCodeGenerator.getPairingCode(this);
            String formattedCode = PairingCodeGenerator.formatPairingCode(pairingCode);
            
            updateStatus("[!] Chua ghep noi - Lam theo huong dan ben duoi");
            pairingCodeText.setText(formattedCode);
            instructionsText.setVisibility(View.VISIBLE);
            copyButton.setVisibility(View.VISIBLE);
            connectButton.setEnabled(true);
            
            Log.i(TAG, "=== DEVICE INFO ===");
            Log.i(TAG, "Device ID: " + deviceId);
            Log.i(TAG, "Pairing Code: " + pairingCode);
            Log.i(TAG, "Formatted: " + formattedCode);
            Log.i(TAG, "==================");
        }
    }
    
    /**
     * Copy pairing code to clipboard
     */
    private void copyPairingCode() {
        String pairingCode = PairingCodeGenerator.getPairingCode(this);
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Xiaozhi Pairing Code", pairingCode);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this,
            "[OK] Da sao chep ma: " + pairingCode,
            Toast.LENGTH_SHORT).show();
        
        Log.i(TAG, "Pairing code copied: " + pairingCode);
    }
    
    /**
     * Connect to Xiaozhi và gửi Authorize handshake
     */
    private void connectToXiaozhi() {
        if (!xiaozhiBound) {
            Toast.makeText(this, "Service chua san sang", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("Dang ket noi...");
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
        
        Toast.makeText(this, "Da reset - Vui long ghep noi lai", Toast.LENGTH_SHORT).show();
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