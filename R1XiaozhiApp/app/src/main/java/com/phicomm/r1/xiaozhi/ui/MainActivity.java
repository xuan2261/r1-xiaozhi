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
import com.phicomm.r1.xiaozhi.core.DeviceState;
import com.phicomm.r1.xiaozhi.core.EventBus;
import com.phicomm.r1.xiaozhi.core.XiaozhiCore;
import com.phicomm.r1.xiaozhi.events.ConnectionEvent;
import com.phicomm.r1.xiaozhi.events.StateChangedEvent;
import com.phicomm.r1.xiaozhi.service.AudioPlaybackService;
import com.phicomm.r1.xiaozhi.service.HTTPServerService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

/**
 * MainActivity refactored để sử dụng XiaozhiCore và EventBus
 * - Event-driven architecture thay vì callbacks
 * - Centralized state management
 * - Clean separation of concerns
 */
public class MainActivity extends Activity {
    
    private static final String TAG = "MainActivity";
    
    // UI components
    private TextView statusText;
    private TextView pairingCodeText;
    private TextView stateText;
    private TextView instructionsText;
    private TextView activationCodeText;
    private TextView activationProgressText;
    private Button connectButton;
    private Button copyButton;
    private Button resetButton;
    private Button cancelActivationButton;
    
    // Core và EventBus
    private XiaozhiCore core;
    private EventBus eventBus;
    
    // Service binding
    private XiaozhiConnectionService xiaozhiService;
    private boolean xiaozhiBound = false;
    
    // Event listeners
    private EventBus.EventListener<StateChangedEvent> stateListener;
    private EventBus.EventListener<ConnectionEvent> connectionListener;
    
    private final ServiceConnection xiaozhiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XiaozhiConnectionService.LocalBinder binder =
                (XiaozhiConnectionService.LocalBinder) service;
            xiaozhiService = binder.getService();
            xiaozhiBound = true;
            
            // Setup service listener
            setupServiceListener();
            
            Log.i(TAG, "Xiaozhi service bound");
            checkActivationStatus();
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
        initializeViews();
        
        // Get XiaozhiCore và EventBus
        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        
        // Register event listeners
        registerEventListeners();
        
        // Setup button listeners
        setupButtonListeners();
        
        // Start all services
        startAllServices();
        
        // Bind to connection service
        bindConnectionService();
        
        Log.i(TAG, "MainActivity created");
        Log.i(TAG, "Core state: " + core.getStateSnapshot());
    }
    
    private void initializeViews() {
        statusText = (TextView) findViewById(R.id.statusText);
        pairingCodeText = (TextView) findViewById(R.id.pairingCodeText);
        stateText = (TextView) findViewById(R.id.stateText);
        instructionsText = (TextView) findViewById(R.id.instructionsText);
        connectButton = (Button) findViewById(R.id.connectButton);
        copyButton = (Button) findViewById(R.id.copyButton);
        resetButton = (Button) findViewById(R.id.resetButton);
        
        // New activation UI components (optional - may not exist in layout yet)
        activationCodeText = (TextView) findViewById(R.id.activationCodeText);
        activationProgressText = (TextView) findViewById(R.id.activationProgressText);
        cancelActivationButton = (Button) findViewById(R.id.cancelActivationButton);
    }
    
    /**
     * Setup service listener cho activation callbacks
     */
    private void setupServiceListener() {
        if (xiaozhiService != null) {
            xiaozhiService.setConnectionListener(new XiaozhiConnectionService.ConnectionListener() {
                @Override
                public void onConnected() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("WebSocket ket noi thanh cong");
                        }
                    });
                }
                
                @Override
                public void onDisconnected() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("Ngat ket noi");
                            connectButton.setEnabled(true);
                        }
                    });
                }
                
                @Override
                public void onActivationRequired(final String verificationCode) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showActivationCode(verificationCode);
                        }
                    });
                }
                
                @Override
                public void onActivationProgress(final int attempt, final int maxAttempts) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateActivationProgress(attempt, maxAttempts);
                        }
                    });
                }
                
                @Override
                public void onPairingSuccess() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onConnectionSuccess();
                        }
                    });
                }
                
                @Override
                public void onPairingFailed(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onConnectionFailed(error);
                        }
                    });
                }
                
                @Override
                public void onMessage(String message) {
                    // Handle messages if needed
                }
                
                @Override
                public void onError(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStatus("[ERROR] " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }
    
    private void registerEventListeners() {
        // State change listener
        stateListener = new EventBus.EventListener<StateChangedEvent>() {
            @Override
            public void onEvent(StateChangedEvent event) {
                onStateChanged(event);
            }
        };
        eventBus.register(StateChangedEvent.class, stateListener);
        
        // Connection event listener
        connectionListener = new EventBus.EventListener<ConnectionEvent>() {
            @Override
            public void onEvent(ConnectionEvent event) {
                onConnectionEvent(event);
            }
        };
        eventBus.register(ConnectionEvent.class, connectionListener);
        
        Log.d(TAG, "Event listeners registered");
    }
    
    private void onStateChanged(StateChangedEvent event) {
        Log.d(TAG, "State changed: " + event.oldState + " -> " + event.newState);
        
        // Update state display
        String stateDisplay = "Trang thai: ";
        switch (event.newState) {
            case IDLE:
                stateDisplay += "San sang";
                break;
            case LISTENING:
                stateDisplay += "Dang nghe...";
                break;
            case SPEAKING:
                stateDisplay += "Dang noi...";
                break;
        }
        
        if (stateText != null) {
            stateText.setText(stateDisplay);
        }
    }
    
    private void onConnectionEvent(ConnectionEvent event) {
        if (event.connected) {
            updateStatus("[OK] " + event.message);
            Toast.makeText(this, "Ket noi thanh cong!", Toast.LENGTH_SHORT).show();
            pairingCodeText.setText("Da Ghep Noi");
            connectButton.setEnabled(false);
            if (instructionsText != null) {
                instructionsText.setVisibility(View.GONE);
            }
            if (copyButton != null) {
                copyButton.setVisibility(View.GONE);
            }
        } else {
            updateStatus("[FAIL] " + event.message);
            Toast.makeText(this, "Loi: " + event.message, Toast.LENGTH_SHORT).show();
            connectButton.setEnabled(true);
        }
    }
    
    private void setupButtonListeners() {
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToXiaozhi();
            }
        });
        
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyActivationCode();
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetActivation();
            }
        });
        
        if (cancelActivationButton != null) {
            cancelActivationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelActivation();
                }
            });
        }
    }
    
    private void startAllServices() {
        startService(new Intent(this, VoiceRecognitionService.class));
        startService(new Intent(this, AudioPlaybackService.class));
        startService(new Intent(this, LEDControlService.class));
        startService(new Intent(this, HTTPServerService.class));
    }
    
    private void bindConnectionService() {
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        startService(xiaozhiIntent);
        bindService(xiaozhiIntent, xiaozhiConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * Check activation status (py-xiaozhi method)
     */
    private void checkActivationStatus() {
        if (xiaozhiService != null && xiaozhiService.isActivated()) {
            updateStatus("[OK] Thiet bi da kich hoat - San sang su dung");
            pairingCodeText.setText("[OK] Da Kich Hoat");
            instructionsText.setVisibility(View.GONE);
            copyButton.setVisibility(View.GONE);
            connectButton.setEnabled(false);
            hideActivationUI();
        } else {
            updateStatus("[!] Chua kich hoat - Bam 'Ket Noi' de bat dau");
            pairingCodeText.setText("Chua kich hoat");
            instructionsText.setVisibility(View.VISIBLE);
            if (instructionsText != null) {
                instructionsText.setText("Bam nut 'Ket Noi' de bat dau kich hoat thiet bi");
            }
            copyButton.setVisibility(View.GONE);
            connectButton.setEnabled(true);
            hideActivationUI();
        }
    }
    
    /**
     * Show activation code UI
     */
    private void showActivationCode(String verificationCode) {
        updateStatus("Dang cho kich hoat...");
        
        if (activationCodeText != null) {
            activationCodeText.setText("Ma kich hoat: " + verificationCode);
            activationCodeText.setVisibility(View.VISIBLE);
        } else {
            // Fallback to pairingCodeText
            pairingCodeText.setText("Ma: " + verificationCode);
        }
        
        if (instructionsText != null) {
            instructionsText.setText(
                "Truy cap: https://xiaozhi.me/activate\n" +
                "Nhap ma: " + verificationCode + "\n" +
                "Hoac sao chep ma va dan vao trang web"
            );
            instructionsText.setVisibility(View.VISIBLE);
        }
        
        copyButton.setVisibility(View.VISIBLE);
        connectButton.setEnabled(false);
        
        if (cancelActivationButton != null) {
            cancelActivationButton.setVisibility(View.VISIBLE);
        }
        
        Toast.makeText(this, "Nhap ma vao trang web: " + verificationCode, Toast.LENGTH_LONG).show();
        
        Log.i(TAG, "=== ACTIVATION CODE ===");
        Log.i(TAG, "Verification Code: " + verificationCode);
        Log.i(TAG, "URL: https://xiaozhi.me/activate");
        Log.i(TAG, "======================");
    }
    
    /**
     * Update activation progress
     */
    private void updateActivationProgress(int attempt, int maxAttempts) {
        String progress = "Dang kiem tra... (" + attempt + "/" + maxAttempts + ")";
        
        if (activationProgressText != null) {
            activationProgressText.setText(progress);
            activationProgressText.setVisibility(View.VISIBLE);
        } else {
            updateStatus(progress);
        }
    }
    
    /**
     * Hide activation UI elements
     */
    private void hideActivationUI() {
        if (activationCodeText != null) {
            activationCodeText.setVisibility(View.GONE);
        }
        if (activationProgressText != null) {
            activationProgressText.setVisibility(View.GONE);
        }
        if (cancelActivationButton != null) {
            cancelActivationButton.setVisibility(View.GONE);
        }
    }
    
    /**
     * Handle successful connection
     */
    private void onConnectionSuccess() {
        updateStatus("[OK] Ket noi thanh cong!");
        Toast.makeText(this, "Ket noi thanh cong!", Toast.LENGTH_SHORT).show();
        pairingCodeText.setText("Da Ket Noi");
        connectButton.setEnabled(false);
        instructionsText.setVisibility(View.GONE);
        copyButton.setVisibility(View.GONE);
        hideActivationUI();
    }
    
    /**
     * Handle connection failure
     */
    private void onConnectionFailed(String error) {
        updateStatus("[FAIL] " + error);
        Toast.makeText(this, "Loi: " + error, Toast.LENGTH_SHORT).show();
        connectButton.setEnabled(true);
        hideActivationUI();
    }
    
    /**
     * Copy activation code to clipboard
     */
    private void copyActivationCode() {
        String code = null;
        
        if (activationCodeText != null && activationCodeText.getVisibility() == View.VISIBLE) {
            String text = activationCodeText.getText().toString();
            if (text.contains(":")) {
                code = text.substring(text.indexOf(":") + 1).trim();
            }
        }
        
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, "Khong co ma de sao chep", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Xiaozhi Activation Code", code);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "[OK] Da sao chep ma: " + code, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Activation code copied: " + code);
    }
    
    /**
     * Connect to Xiaozhi (py-xiaozhi method with activation)
     */
    private void connectToXiaozhi() {
        if (!xiaozhiBound) {
            Toast.makeText(this, "Service chua san sang", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("Dang bat dau kich hoat...");
        connectButton.setEnabled(false);
        
        // Connect will check activation and start flow if needed
        xiaozhiService.connect();
        
        Log.i(TAG, "Starting connection/activation...");
    }
    
    /**
     * Cancel activation
     */
    private void cancelActivation() {
        if (xiaozhiService != null) {
            xiaozhiService.cancelActivation();
        }
        
        updateStatus("Da huy kich hoat");
        connectButton.setEnabled(true);
        hideActivationUI();
        
        Toast.makeText(this, "Da huy kich hoat", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Activation cancelled");
    }
    
    /**
     * Reset activation (py-xiaozhi method)
     */
    private void resetActivation() {
        if (xiaozhiService != null) {
            if (xiaozhiService.isConnected()) {
                xiaozhiService.disconnect();
            }
            xiaozhiService.resetActivation();
        }
        
        checkActivationStatus();
        
        Toast.makeText(this, "Da reset - Vui long kich hoat lai", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Activation reset");
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
        // Unregister event listeners
        if (eventBus != null) {
            if (stateListener != null) {
                eventBus.unregister(StateChangedEvent.class, stateListener);
            }
            if (connectionListener != null) {
                eventBus.unregister(ConnectionEvent.class, connectionListener);
            }
        }
        
        // Unbind service
        if (xiaozhiBound) {
            unbindService(xiaozhiConnection);
            xiaozhiBound = false;
        }
        
        super.onDestroy();
        Log.i(TAG, "MainActivity destroyed");
    }
}