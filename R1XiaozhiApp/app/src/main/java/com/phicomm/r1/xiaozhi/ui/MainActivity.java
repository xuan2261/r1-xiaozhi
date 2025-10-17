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
    private Button connectButton;
    private Button copyButton;
    private Button resetButton;
    
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
                copyPairingCode();
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPairing();
            }
        });
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