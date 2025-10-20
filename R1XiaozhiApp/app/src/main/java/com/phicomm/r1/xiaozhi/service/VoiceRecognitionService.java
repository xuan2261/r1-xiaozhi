package com.phicomm.r1.xiaozhi.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.support.v4.content.ContextCompat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Service thu âm và phát hiện wake word liên tục
 * Khi phát hiện wake word, bắt đầu ghi âm đầy đủ và gửi đến Xiaozhi
 *
 * FIX: Added permission checks to prevent SecurityException crash
 */
public class VoiceRecognitionService extends Service {
    
    private static final String TAG = "VoiceRecognition";
    private static final String CHANNEL_ID = "voice_recognition_channel";
    private static final int NOTIFICATION_ID = 1;
    
    // Audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 2;
    
    // Recording state
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private XiaozhiConfig config;
    
    // Wake word detection
    private boolean isListeningForWakeWord = true;
    private boolean isRecordingCommand = false;
    private ByteArrayOutputStream commandAudioStream;
    
    // Energy-based Voice Activity Detection
    private static final double ENERGY_THRESHOLD = 500.0;
    private static final int SILENCE_FRAMES = 20; // ~0.4 seconds at 50fps
    private int silenceCounter = 0;
    
    private VoiceCallback callback;
    
    public interface VoiceCallback {
        void onWakeWordDetected();
        void onRecordingStarted();
        void onRecordingCompleted(byte[] audioData);
        void onVoiceActivityDetected();
        void onError(String error);
    }
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public VoiceRecognitionService getService() {
            return VoiceRecognitionService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        config = new XiaozhiConfig(this);
        Log.d(TAG, "VoiceRecognitionService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        // FIX #2: Check permission before starting recording
        if (checkRecordAudioPermission()) {
            startRecording();
        } else {
            Log.e(TAG, "=== RECORD_AUDIO PERMISSION DENIED ===");
            Log.e(TAG, "Cannot start recording without permission!");
            Log.e(TAG, "Service will run but recording is disabled.");

            if (callback != null) {
                callback.onError("Khong co quyen ghi am");
            }
        }

        return START_STICKY;
    }

    /**
     * FIX #2: Check RECORD_AUDIO permission before accessing microphone
     * Use ContextCompat for API 22 compatibility
     */
    private boolean checkRecordAudioPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;

        Log.i(TAG, "RECORD_AUDIO permission: " + hasPermission);
        return hasPermission;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public void setCallback(VoiceCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Tạo notification channel cho Android O+
     * Không cần cho API 22
     */
    private void createNotificationChannel() {
        // NotificationChannel chỉ có từ API 26+
        // API 22 không cần tạo channel
    }
    
    /**
     * Tạo notification cho foreground service
     */
    private Notification createNotification() {
        // API 22 chỉ cần Builder đơn giản
        Notification.Builder builder = new Notification.Builder(this);
        
        return builder
            .setContentTitle("Xiaozhi Voice Assistant")
            .setContentText("Đang lắng nghe: " + config.getWakeWord())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build();
    }
    
    /**
     * Bắt đầu thu âm liên tục
     * FIX #2: Enhanced error handling and permission checks
     */
    private void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }

        // FIX #2: Double-check permission before creating AudioRecord
        if (!checkRecordAudioPermission()) {
            Log.e(TAG, "Cannot start recording: No RECORD_AUDIO permission");
            if (callback != null) {
                callback.onError("Khong co quyen ghi am");
            }
            return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR;

        Log.i(TAG, "=== STARTING AUDIO RECORDING ===");
        Log.i(TAG, "Sample rate: " + SAMPLE_RATE);
        Log.i(TAG, "Buffer size: " + bufferSize);

        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "=== AUDIORECORD INITIALIZATION FAILED ===");
                Log.e(TAG, "State: " + audioRecord.getState());
                Log.e(TAG, "Expected: " + AudioRecord.STATE_INITIALIZED);

                if (callback != null) {
                    callback.onError("Khong the khoi tao microphone");
                }

                // Clean up
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                return;
            }

            isRecording = true;
            recordingThread = new Thread(new RecordingRunnable());
            recordingThread.start();

            Log.i(TAG, "=== RECORDING STARTED SUCCESSFULLY ===");
            Log.i(TAG, "Wake word: " + config.getWakeWord());
            Log.i(TAG, "Energy threshold: " + ENERGY_THRESHOLD);

        } catch (SecurityException e) {
            Log.e(TAG, "=== SECURITY EXCEPTION ===", e);
            Log.e(TAG, "No RECORD_AUDIO permission!");

            if (callback != null) {
                callback.onError("Khong co quyen ghi am");
            }

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "=== ILLEGAL ARGUMENT EXCEPTION ===", e);
            Log.e(TAG, "Invalid AudioRecord parameters!");

            if (callback != null) {
                callback.onError("Cau hinh microphone khong hop le");
            }

        } catch (Exception e) {
            Log.e(TAG, "=== UNEXPECTED EXCEPTION ===", e);

            if (callback != null) {
                callback.onError("Loi khong xac dinh: " + e.getMessage());
            }
        }
    }
    
    /**
     * Recording loop - chạy trong background thread
     */
    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            
            short[] buffer = new short[1024];
            audioRecord.startRecording();
            
            Log.d(TAG, "Recording loop started");
            
            while (isRecording) {
                int shortsRead = audioRecord.read(buffer, 0, buffer.length);
                
                if (shortsRead > 0) {
                    processAudioBuffer(buffer, shortsRead);
                } else {
                    Log.w(TAG, "AudioRecord read error: " + shortsRead);
                }
            }
            
            Log.d(TAG, "Recording loop ended");
        }
    }
    
    /**
     * Xử lý audio buffer
     */
    private void processAudioBuffer(short[] buffer, int length) {
        if (isListeningForWakeWord) {
            // Mode 1: Phát hiện wake word
            boolean wakeWordDetected = detectWakeWord(buffer, length);
            
            if (wakeWordDetected) {
                onWakeWordDetected();
            }
        } else if (isRecordingCommand) {
            // Mode 2: Ghi âm command sau khi phát hiện wake word
            recordCommandAudio(buffer, length);
        }
    }
    
    /**
     * Phát hiện wake word đơn giản dựa trên energy và pattern
     * TODO: Tích hợp thư viện wake word detection chuyên dụng như Porcupine
     */
    private boolean detectWakeWord(short[] buffer, int length) {
        double energy = calculateEnergy(buffer, length);
        
        // Simple energy-based detection
        // Trong production nên dùng model ML như Porcupine, Snowboy
        if (energy > ENERGY_THRESHOLD * 3) {
            Log.d(TAG, "High energy detected, possible wake word: " + energy);
            return true;
        }
        
        return false;
    }
    
    /**
     * Tính năng lượng audio để phát hiện voice activity
     */
    private double calculateEnergy(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / length);
    }
    
    /**
     * Xử lý khi phát hiện wake word
     */
    private void onWakeWordDetected() {
        Log.d(TAG, "Wake word detected!");
        
        isListeningForWakeWord = false;
        isRecordingCommand = true;
        silenceCounter = 0;
        
        commandAudioStream = new ByteArrayOutputStream();
        
        if (callback != null) {
            callback.onWakeWordDetected();
            callback.onRecordingStarted();
        }
        
        // Notify LED service
        Intent ledIntent = new Intent(this, LEDControlService.class);
        ledIntent.setAction(LEDControlService.ACTION_SET_LISTENING);
        startService(ledIntent);
    }
    
    /**
     * Ghi âm command sau wake word
     * FIX: Added null check to prevent NullPointerException crash
     */
    private void recordCommandAudio(short[] buffer, int length) {
        // FIX: Null check - prevent crash if stream already closed
        if (commandAudioStream == null) {
            Log.w(TAG, "commandAudioStream is null, skipping recording");
            return;
        }

        double energy = calculateEnergy(buffer, length);

        // Convert short[] to byte[]
        byte[] audioBytes = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            audioBytes[i * 2] = (byte) (buffer[i] & 0xFF);
            audioBytes[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xFF);
        }

        try {
            commandAudioStream.write(audioBytes, 0, audioBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error writing to commandAudioStream", e);
            return;
        }

        // Phát hiện kết thúc câu lệnh (silence detection)
        if (energy < ENERGY_THRESHOLD) {
            silenceCounter++;

            if (silenceCounter >= SILENCE_FRAMES) {
                onCommandRecordingCompleted();
                return; // FIX: Return immediately to prevent double-call
            }
        } else {
            silenceCounter = 0;

            if (callback != null) {
                callback.onVoiceActivityDetected();
            }
        }

        // FIX: Check null again before accessing size (may be null after onCommandRecordingCompleted)
        if (commandAudioStream != null && commandAudioStream.size() > SAMPLE_RATE * 2 * 10) {
            Log.w(TAG, "Recording too long, force stopping");
            onCommandRecordingCompleted();
        }
    }
    
    /**
     * Hoàn thành ghi âm command
     * FIX: Added null check and prevent double-call
     */
    private void onCommandRecordingCompleted() {
        // FIX: Prevent double-call - check if already completed
        if (!isRecordingCommand) {
            Log.w(TAG, "onCommandRecordingCompleted called but not recording, ignoring");
            return;
        }

        // FIX: Null check - prevent crash if stream is null
        if (commandAudioStream == null) {
            Log.e(TAG, "commandAudioStream is null, cannot complete recording");
            isRecordingCommand = false;
            isListeningForWakeWord = true;
            return;
        }

        Log.d(TAG, "Command recording completed");

        // FIX: Set flags FIRST to prevent re-entry
        isRecordingCommand = false;
        isListeningForWakeWord = true;

        byte[] audioData = null;
        try {
            audioData = commandAudioStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error getting audio data from stream", e);
            commandAudioStream = null;
            return;
        }

        // FIX: Close and null the stream immediately
        try {
            commandAudioStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing commandAudioStream", e);
        }
        commandAudioStream = null;

        // Validate audio data
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "No audio data recorded, skipping");
            return;
        }

        Log.i(TAG, "Audio data size: " + audioData.length + " bytes");

        if (callback != null) {
            callback.onRecordingCompleted(audioData);
        }

        // Gửi audio đến XiaozhiConnectionService
        Intent intent = new Intent(this, XiaozhiConnectionService.class);
        intent.setAction("SEND_AUDIO");
        intent.putExtra("audio_data", audioData);
        intent.putExtra("sample_rate", SAMPLE_RATE);
        intent.putExtra("channels", 1);
        startService(intent);

        // Reset LED
        Intent ledIntent = new Intent(this, LEDControlService.class);
        ledIntent.setAction(LEDControlService.ACTION_SET_IDLE);
        startService(ledIntent);
    }
    
    /**
     * Dừng thu âm
     */
    public void stopRecording() {
        isRecording = false;
        
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping recording thread", e);
            }
        }
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord", e);
            }
            audioRecord = null;
        }
        
        Log.d(TAG, "Recording stopped");
    }
    
    /**
     * Pause/Resume listening
     */
    public void setListening(boolean listening) {
        isListeningForWakeWord = listening;
        Log.d(TAG, "Listening: " + listening);
    }
    
    public boolean isListening() {
        return isListeningForWakeWord;
    }
    
    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
        Log.d(TAG, "VoiceRecognitionService destroyed");
    }
}