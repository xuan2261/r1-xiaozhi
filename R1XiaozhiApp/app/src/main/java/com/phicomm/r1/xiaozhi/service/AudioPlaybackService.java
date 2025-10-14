package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Service phát audio từ Xiaozhi (TTS responses)
 * Hỗ trợ phát từ URL hoặc raw audio data
 */
public class AudioPlaybackService extends Service implements 
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener {
    
    private static final String TAG = "AudioPlayback";
    
    public static final String ACTION_PLAY_URL = "com.phicomm.r1.xiaozhi.PLAY_URL";
    public static final String ACTION_PLAY_DATA = "com.phicomm.r1.xiaozhi.PLAY_DATA";
    public static final String ACTION_STOP = "com.phicomm.r1.xiaozhi.STOP";
    public static final String ACTION_PAUSE = "com.phicomm.r1.xiaozhi.PAUSE";
    public static final String ACTION_RESUME = "com.phicomm.r1.xiaozhi.RESUME";
    
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private boolean isPrepared = false;
    
    private PlaybackCallback callback;
    
    public interface PlaybackCallback {
        void onPlaybackStarted();
        void onPlaybackCompleted();
        void onPlaybackError(String error);
    }
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public AudioPlaybackService getService() {
            return AudioPlaybackService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        initMediaPlayer();
        Log.d(TAG, "AudioPlaybackService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            switch (action) {
                case ACTION_PLAY_URL:
                    String audioUrl = intent.getStringExtra("audio_url");
                    if (audioUrl != null) {
                        playFromUrl(audioUrl);
                    }
                    break;
                    
                case ACTION_PLAY_DATA:
                    byte[] audioData = intent.getByteArrayExtra("audio_data");
                    if (audioData != null) {
                        playFromData(audioData);
                    }
                    break;
                    
                case ACTION_STOP:
                    stop();
                    break;
                    
                case ACTION_PAUSE:
                    pause();
                    break;
                    
                case ACTION_RESUME:
                    resume();
                    break;
            }
        }
        
        return START_NOT_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Khởi tạo MediaPlayer
     */
    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            releaseMediaPlayer();
        }
        
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        
        isPrepared = false;
    }
    
    /**
     * Phát audio từ URL
     */
    public void playFromUrl(String url) {
        Log.d(TAG, "Playing from URL: " + url);
        
        try {
            // Request audio focus
            int result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
            
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus not granted");
            }
            
            // Notify LED service
            Intent ledIntent = new Intent(this, LEDControlService.class);
            ledIntent.setAction(LEDControlService.ACTION_SET_SPEAKING);
            startService(ledIntent);
            
            // Stop current playback if any
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            
            initMediaPlayer();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            
        } catch (IOException e) {
            Log.e(TAG, "Error playing from URL", e);
            if (callback != null) {
                callback.onPlaybackError(e.getMessage());
            }
            onPlaybackCompleted();
        }
    }
    
    /**
     * Phát audio từ raw data (PCM)
     */
    public void playFromData(byte[] audioData) {
        Log.d(TAG, "Playing from data: " + audioData.length + " bytes");
        
        try {
            // Save to temp file
            File tempFile = File.createTempFile("xiaozhi_audio_", ".pcm", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(audioData);
            fos.close();
            
            // Play from file
            playFromUrl(tempFile.getAbsolutePath());
            
            // Delete temp file after playback
            tempFile.deleteOnExit();
            
        } catch (IOException e) {
            Log.e(TAG, "Error playing from data", e);
            if (callback != null) {
                callback.onPlaybackError(e.getMessage());
            }
        }
    }
    
    /**
     * Dừng phát
     */
    public void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            Log.d(TAG, "Playback stopped");
        }
        onPlaybackCompleted();
    }
    
    /**
     * Tạm dừng
     */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            Log.d(TAG, "Playback paused");
        }
    }
    
    /**
     * Tiếp tục phát
     */
    public void resume() {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
            Log.d(TAG, "Playback resumed");
        }
    }
    
    /**
     * MediaPlayer callbacks
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        mp.start();
        Log.d(TAG, "Playback started");
        
        if (callback != null) {
            callback.onPlaybackStarted();
        }
    }
    
    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "Playback completed");
        onPlaybackCompleted();
    }
    
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        
        if (callback != null) {
            callback.onPlaybackError("MediaPlayer error: " + what);
        }
        
        onPlaybackCompleted();
        return true;
    }
    
    /**
     * Cleanup sau khi phát xong
     */
    private void onPlaybackCompleted() {
        isPrepared = false;
        
        // Release audio focus
        audioManager.abandonAudioFocus(audioFocusChangeListener);
        
        // Reset LED
        Intent ledIntent = new Intent(this, LEDControlService.class);
        ledIntent.setAction(LEDControlService.ACTION_SET_IDLE);
        startService(ledIntent);
        
        if (callback != null) {
            callback.onPlaybackCompleted();
        }
    }
    
    /**
     * Audio Focus Change Listener
     */
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = 
        new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        // Gained focus
                        if (mediaPlayer != null && !mediaPlayer.isPlaying() && isPrepared) {
                            mediaPlayer.start();
                            mediaPlayer.setVolume(1.0f, 1.0f);
                        }
                        break;
                        
                    case AudioManager.AUDIOFOCUS_LOSS:
                        // Lost focus permanently
                        stop();
                        break;
                        
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        // Lost focus temporarily
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                        }
                        break;
                        
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        // Lost focus temporarily but can duck
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            mediaPlayer.setVolume(0.3f, 0.3f);
                        }
                        break;
                }
            }
        };
    
    /**
     * Release MediaPlayer resources
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer", e);
            }
            mediaPlayer = null;
        }
    }
    
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    
    @Override
    public void onDestroy() {
        releaseMediaPlayer();
        audioManager.abandonAudioFocus(audioFocusChangeListener);
        super.onDestroy();
        Log.d(TAG, "AudioPlaybackService destroyed");
    }
}