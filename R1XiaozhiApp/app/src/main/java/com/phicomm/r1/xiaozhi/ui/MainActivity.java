package com.phicomm.r1.xiaozhi.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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
 *
 * FIX: Added runtime permission handling for RECORD_AUDIO to prevent crash
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    // Permission request codes
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private static final int REQUEST_STORAGE_PERMISSIONS = 2;

    // Permission state
    private boolean permissionsGranted = false;

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

        // FIX #1: Check and request permissions BEFORE starting services
        if (checkRequiredPermissions()) {
            permissionsGranted = true;
            initializeServices();
        } else {
            permissionsGranted = false;
            requestRequiredPermissions();
        }

        Log.i(TAG, "MainActivity created");
        Log.i(TAG, "Core state: " + core.getStateSnapshot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity onResume() called");

        // FIX: Re-bind to service if not already bound
        // This handles case where MainActivity is recreated (e.g., after being destroyed by launcher)
        if (!xiaozhiBound && permissionsGranted) {
            Log.i(TAG, "Service not bound - re-binding...");
            bindConnectionService();
        }
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
    
    /**
     * FIX #1: Check required permissions before starting services
     */
    private boolean checkRequiredPermissions() {
        // Check RECORD_AUDIO permission (critical for VoiceRecognitionService)
        // Use ContextCompat for API 22 compatibility
        boolean hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;

        // Check storage permissions (for wake word models and audio files)
        boolean hasWriteStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
        boolean hasReadStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;

        Log.i(TAG, "=== PERMISSION CHECK ===");
        Log.i(TAG, "RECORD_AUDIO: " + hasRecordAudio);
        Log.i(TAG, "WRITE_EXTERNAL_STORAGE: " + hasWriteStorage);
        Log.i(TAG, "READ_EXTERNAL_STORAGE: " + hasReadStorage);
        Log.i(TAG, "=======================");

        return hasRecordAudio && hasWriteStorage && hasReadStorage;
    }

    /**
     * FIX #1: Request required permissions
     */
    private void requestRequiredPermissions() {
        Log.i(TAG, "Requesting runtime permissions...");

        // Show explanation to user
        Toast.makeText(this,
            "App can ghi am de nhan dien giong noi. Vui long cap quyen!",
            Toast.LENGTH_LONG).show();

        // Request RECORD_AUDIO permission first (most critical)
        // Use ActivityCompat for API 22 compatibility
        ActivityCompat.requestPermissions(
            this,
            new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            },
            REQUEST_RECORD_AUDIO_PERMISSION
        );
    }

    /**
     * FIX #1: Handle permission request result
     * Note: This method is called by ActivityCompat for API 22 compatibility
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // No super call needed for API 22 (method doesn't exist in Activity base class)

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            boolean allGranted = true;

            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                Log.i(TAG, "Permission " + permissions[i] + ": " + granted);

                if (!granted) {
                    allGranted = false;
                }
            }

            if (allGranted) {
                Log.i(TAG, "=== ALL PERMISSIONS GRANTED ===");
                permissionsGranted = true;
                Toast.makeText(this, "[OK] Da cap quyen - Khoi dong dich vu...", Toast.LENGTH_SHORT).show();

                // Now safe to start services
                initializeServices();
            } else {
                Log.e(TAG, "=== PERMISSIONS DENIED ===");
                permissionsGranted = false;

                // Show error message
                Toast.makeText(this,
                    "[LOI] Khong co quyen ghi am! App se khong hoat dong.",
                    Toast.LENGTH_LONG).show();

                // Update UI to show error state
                updateStatus("[LOI] Khong co quyen ghi am");
                pairingCodeText.setText("[LOI] Thieu Quyen");

                // Disable connect button
                connectButton.setEnabled(false);
            }
        }
    }

    /**
     * FIX #1: Initialize services only after permissions are granted
     */
    private void initializeServices() {
        Log.i(TAG, "=== INITIALIZING SERVICES ===");

        // Start all services
        startAllServices();

        // Bind to connection service
        bindConnectionService();

        Log.i(TAG, "=== SERVICES INITIALIZED ===");
    }

    private void startAllServices() {
        Log.i(TAG, "Starting VoiceRecognitionService...");
        startService(new Intent(this, VoiceRecognitionService.class));

        Log.i(TAG, "Starting AudioPlaybackService...");
        startService(new Intent(this, AudioPlaybackService.class));

        Log.i(TAG, "Starting LEDControlService...");
        startService(new Intent(this, LEDControlService.class));

        Log.i(TAG, "Starting HTTPServerService...");
        startService(new Intent(this, HTTPServerService.class));
    }

    private void bindConnectionService() {
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        startService(xiaozhiIntent);
        bindService(xiaozhiIntent, xiaozhiConnection, Context.BIND_AUTO_CREATE);
    }
    
    /**
     * Check activation status (py-xiaozhi method)
     * FIX #2: Auto-connect if already activated
     */
    private void checkActivationStatus() {
        if (xiaozhiService != null && xiaozhiService.isActivated()) {
            // Device is activated - check connection status
            if (xiaozhiService.isConnected()) {
                // Already connected
                updateStatus("[OK] Da kich hoat va ket noi thanh cong");
                pairingCodeText.setText("[OK] Da Ket Noi");
                Log.i(TAG, "Device activated and connected");
            } else {
                // Activated but not connected - auto-connect
                updateStatus("[OK] Da kich hoat - Dang ket noi...");
                pairingCodeText.setText("[OK] Da Kich Hoat");

                Log.i(TAG, "=== AUTO-CONNECT ON RESTART ===");
                Log.i(TAG, "Device is activated but not connected");
                Log.i(TAG, "Starting auto-connect...");

                // FIX #2: Auto-connect WebSocket
                xiaozhiService.connect();

                Log.i(TAG, "===============================");
            }

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
        Log.i(TAG, "MainActivity onDestroy() called");

        // Unregister event listeners
        if (eventBus != null) {
            if (stateListener != null) {
                eventBus.unregister(StateChangedEvent.class, stateListener);
            }
            if (connectionListener != null) {
                eventBus.unregister(ConnectionEvent.class, connectionListener);
            }
        }

        // FIX: MUST unbind service to prevent ServiceConnectionLeaked error
        // Service will continue running because:
        // 1. stopWithTask="false" in AndroidManifest
        // 2. START_STICKY in onStartCommand()
        // 3. Service was started with startService() (not just bound)
        if (xiaozhiBound) {
            Log.i(TAG, "Unbinding service - service will continue running in background");
            try {
                unbindService(xiaozhiConnection);
                xiaozhiBound = false;
                Log.i(TAG, "Service unbound successfully");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unbinding service: " + e.getMessage());
            }
        }

        super.onDestroy();
        Log.i(TAG, "MainActivity destroyed");
    }
}