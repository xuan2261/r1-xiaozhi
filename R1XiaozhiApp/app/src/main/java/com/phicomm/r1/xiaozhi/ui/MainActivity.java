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

import com.phicomm.r1.xiaozhi.R;
import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.service.LEDControlService;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;

/**
 * Main Activity - Giao diện chính
 * Do R1 không có màn hình, activity này chủ yếu để debug và cấu hình
 */
public class MainActivity extends Activity {
    
    private static final String TAG = "MainActivity";
    
    private XiaozhiConfig config;
    private TextView statusText;
    private TextView connectionStatusText;
    private Button startButton;
    private Button stopButton;
    private Button settingsButton;
    
    // Service bindings
    private XiaozhiConnectionService xiaozhiService;
    private VoiceRecognitionService voiceService;
    private LEDControlService ledService;
    
    private boolean xiaozhiServiceBound = false;
    private boolean voiceServiceBound = false;
    private boolean ledServiceBound = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        config = new XiaozhiConfig(this);
        
        initViews();
        bindServices();
        updateUI();
        
        Log.d(TAG, "MainActivity created");
    }
    
    private void initViews() {
        statusText = (TextView) findViewById(R.id.status_text);
        connectionStatusText = (TextView) findViewById(R.id.connection_status_text);
        startButton = (Button) findViewById(R.id.start_button);
        stopButton = (Button) findViewById(R.id.stop_button);
        settingsButton = (Button) findViewById(R.id.settings_button);
        
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startServices();
            }
        });
        
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServices();
            }
        });
        
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }
    
    private void bindServices() {
        // Bind to XiaozhiConnectionService
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        bindService(xiaozhiIntent, xiaozhiConnection, Context.BIND_AUTO_CREATE);
        
        // Bind to VoiceRecognitionService
        Intent voiceIntent = new Intent(this, VoiceRecognitionService.class);
        bindService(voiceIntent, voiceConnection, Context.BIND_AUTO_CREATE);
        
        // Bind to LEDControlService
        Intent ledIntent = new Intent(this, LEDControlService.class);
        bindService(ledIntent, ledConnection, Context.BIND_AUTO_CREATE);
    }
    
    private ServiceConnection xiaozhiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XiaozhiConnectionService.LocalBinder binder = 
                (XiaozhiConnectionService.LocalBinder) service;
            xiaozhiService = binder.getService();
            xiaozhiServiceBound = true;
            
            xiaozhiService.setCallback(new XiaozhiConnectionService.ConnectionCallback() {
                @Override
                public void onConnected(final boolean isCloud) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatusText.setText("Đã kết nối: " +
                                (isCloud ? "Cloud" : "Self-hosted"));
                        }
                    });
                }
                
                @Override
                public void onDisconnected() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatusText.setText("Mất kết nối");
                        }
                    });
                }
                
                @Override
                public void onMessageReceived(String message) {
                    Log.d(TAG, "Message: " + message);
                }
                
                @Override
                public void onAudioReceived(String audioUrl) {
                    Log.d(TAG, "Audio: " + audioUrl);
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error: " + error);
                }
            });
            
            updateUI();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            xiaozhiServiceBound = false;
        }
    };
    
    private ServiceConnection voiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VoiceRecognitionService.LocalBinder binder = 
                (VoiceRecognitionService.LocalBinder) service;
            voiceService = binder.getService();
            voiceServiceBound = true;
            
            voiceService.setCallback(new VoiceRecognitionService.VoiceCallback() {
                @Override
                public void onWakeWordDetected() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Wake word detected!");
                        }
                    });
                }
                
                @Override
                public void onRecordingStarted() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Đang ghi âm...");
                        }
                    });
                }
                
                @Override
                public void onRecordingCompleted(byte[] audioData) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Đã gửi đến Xiaozhi");
                        }
                    });
                }
                
                @Override
                public void onVoiceActivityDetected() {
                    // Voice activity detected
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Voice error: " + error);
                }
            });
            
            updateUI();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            voiceServiceBound = false;
        }
    };
    
    private ServiceConnection ledConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LEDControlService.LocalBinder binder = 
                (LEDControlService.LocalBinder) service;
            ledService = binder.getService();
            ledServiceBound = true;
            updateUI();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            ledServiceBound = false;
        }
    };
    
    private void startServices() {
        // Start all services
        Intent ledIntent = new Intent(this, LEDControlService.class);
        ledIntent.setAction(LEDControlService.ACTION_SET_IDLE);
        startService(ledIntent);
        
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        startService(xiaozhiIntent);
        
        Intent voiceIntent = new Intent(this, VoiceRecognitionService.class);
        startService(voiceIntent);
        
        statusText.setText("Đang khởi động...");
        Log.d(TAG, "Services started");
    }
    
    private void stopServices() {
        // Stop all services
        stopService(new Intent(this, VoiceRecognitionService.class));
        stopService(new Intent(this, XiaozhiConnectionService.class));
        stopService(new Intent(this, LEDControlService.class));
        
        statusText.setText("Đã dừng");
        connectionStatusText.setText("Không kết nối");
        Log.d(TAG, "Services stopped");
    }
    
    private void updateUI() {
        if (voiceServiceBound && voiceService != null) {
            if (voiceService.isListening()) {
                statusText.setText("Đang lắng nghe: " + config.getWakeWord());
            } else {
                statusText.setText("Đã tạm dừng");
            }
        }
        
        if (xiaozhiServiceBound && xiaozhiService != null) {
            if (xiaozhiService.isConnected()) {
                String mode = xiaozhiService.isUsingCloud() ? "Cloud" : "Self-hosted";
                connectionStatusText.setText("Đã kết nối: " + mode);
            } else {
                connectionStatusText.setText("Đang kết nối...");
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
    
    @Override
    protected void onDestroy() {
        // Unbind services
        if (xiaozhiServiceBound) {
            unbindService(xiaozhiConnection);
        }
        if (voiceServiceBound) {
            unbindService(voiceConnection);
        }
        if (ledServiceBound) {
            unbindService(ledConnection);
        }
        
        super.onDestroy();
        Log.d(TAG, "MainActivity destroyed");
    }
}