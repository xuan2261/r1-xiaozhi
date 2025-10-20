package com.phicomm.r1.xiaozhi.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Service điều khiển LED strip của Phicomm R1
 * Hiển thị trạng thái hoạt động qua màu sắc và animation
 */
public class LEDControlService extends Service {
    
    private static final String TAG = "LEDControl";
    private static final String LED_PATH = "/sys/class/leds/multi_leds0/led_color";
    
    // Actions
    public static final String ACTION_SET_IDLE = "com.phicomm.r1.xiaozhi.LED_IDLE";
    public static final String ACTION_SET_LISTENING = "com.phicomm.r1.xiaozhi.LED_LISTENING";
    public static final String ACTION_SET_THINKING = "com.phicomm.r1.xiaozhi.LED_THINKING";
    public static final String ACTION_SET_SPEAKING = "com.phicomm.r1.xiaozhi.LED_SPEAKING";
    public static final String ACTION_SET_ERROR = "com.phicomm.r1.xiaozhi.LED_ERROR";
    public static final String ACTION_SET_COLOR = "com.phicomm.r1.xiaozhi.LED_COLOR";
    public static final String ACTION_STOP_ANIMATION = "com.phicomm.r1.xiaozhi.LED_STOP";
    
    // LED States
    private static final int STATE_IDLE = 0;
    private static final int STATE_LISTENING = 1;
    private static final int STATE_THINKING = 2;
    private static final int STATE_SPEAKING = 3;
    private static final int STATE_ERROR = 4;
    
    private int currentState = STATE_IDLE;
    private boolean isAnimating = false;
    private Handler animationHandler;
    private Runnable animationRunnable;
    private XiaozhiConfig config;
    private boolean hasRootAccess = false;
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public LEDControlService getService() {
            return LEDControlService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        config = new XiaozhiConfig(this);
        animationHandler = new Handler();
        checkRootAccess();

        if (!hasRootAccess) {
            Log.w(TAG, "=== LED CONTROL DISABLED ===");
            Log.w(TAG, "No root access - LED hardware control unavailable");
            Log.w(TAG, "App will continue without LED feedback");
            Log.w(TAG, "To enable LED: Grant root access to app");
            Log.w(TAG, "===========================");
        } else {
            Log.i(TAG, "LED Control enabled with root access");
        }

        Log.d(TAG, "LEDControlService created (Root: " + hasRootAccess + ")");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            switch (action) {
                case ACTION_SET_IDLE:
                    setIdleState();
                    break;
                    
                case ACTION_SET_LISTENING:
                    setListeningState();
                    break;
                    
                case ACTION_SET_THINKING:
                    setThinkingState();
                    break;
                    
                case ACTION_SET_SPEAKING:
                    setSpeakingState();
                    break;
                    
                case ACTION_SET_ERROR:
                    setErrorState();
                    break;
                    
                case ACTION_SET_COLOR:
                    int color = intent.getIntExtra("color", Color.WHITE);
                    setLEDColor(color);
                    break;
                    
                case ACTION_STOP_ANIMATION:
                    stopAnimation();
                    break;
            }
        }
        
        return START_NOT_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    /**
     * Kiểm tra root access
     */
    private void checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            hasRootAccess = (process.exitValue() == 0);
        } catch (Exception e) {
            hasRootAccess = false;
            Log.w(TAG, "No root access, LED control disabled");
        }
    }
    
    /**
     * Set LED color trực tiếp
     */
    public void setLEDColor(int color) {
        if (!config.isLedEnabled()) {
            return;
        }

        if (!hasRootAccess) {
            // Silently skip LED control if no root
            // Don't spam logs - already warned in onCreate()
            return;
        }
        
        try {
            // Format: "7fff RRGGBB" (7fff là brightness max)
            String colorHex = String.format("7fff %06x", color & 0xFFFFFF);
            
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("echo -n '" + colorHex + "' > " + LED_PATH + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            
            Log.d(TAG, "LED color set to: " + colorHex);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error setting LED color", e);
        }
    }
    
    /**
     * Idle state - màu xanh dương nhạt
     */
    public void setIdleState() {
        stopAnimation();
        currentState = STATE_IDLE;
        setLEDColor(0x0066CC); // Xanh dương nhạt
        Log.d(TAG, "State: IDLE");
    }
    
    /**
     * Listening state - xoay tròn màu xanh lá
     */
    public void setListeningState() {
        stopAnimation();
        currentState = STATE_LISTENING;
        startRotatingAnimation(0x00FF00, 50); // Xanh lá
        Log.d(TAG, "State: LISTENING");
    }
    
    /**
     * Thinking state - pulse màu trắng
     */
    public void setThinkingState() {
        stopAnimation();
        currentState = STATE_THINKING;
        startPulseAnimation(0xFFFFFF, 200); // Trắng
        Log.d(TAG, "State: THINKING");
    }
    
    /**
     * Speaking state - màu xanh cyan
     */
    public void setSpeakingState() {
        stopAnimation();
        currentState = STATE_SPEAKING;
        setLEDColor(0x00FFFF); // Cyan
        Log.d(TAG, "State: SPEAKING");
    }
    
    /**
     * Error state - nhấp nháy đỏ
     */
    public void setErrorState() {
        stopAnimation();
        currentState = STATE_ERROR;
        startBlinkAnimation(0xFF0000, 300); // Đỏ
        Log.d(TAG, "State: ERROR");
    }
    
    /**
     * Animation: Rotating colors (cho listening)
     */
    private void startRotatingAnimation(final int baseColor, final int delayMs) {
        isAnimating = true;
        
        animationRunnable = new Runnable() {
            private int hue = 0;
            
            @Override
            public void run() {
                if (!isAnimating) return;
                
                float[] hsv = new float[3];
                Color.colorToHSV(baseColor, hsv);
                hsv[0] = hue;
                
                int color = Color.HSVToColor(hsv);
                setLEDColor(color);
                
                hue = (hue + 10) % 360;
                animationHandler.postDelayed(this, delayMs);
            }
        };
        
        animationHandler.post(animationRunnable);
    }
    
    /**
     * Animation: Pulse (cho thinking)
     */
    private void startPulseAnimation(final int color, final int delayMs) {
        isAnimating = true;
        
        animationRunnable = new Runnable() {
            private boolean increasing = true;
            private int brightness = 0;
            
            @Override
            public void run() {
                if (!isAnimating) return;
                
                // Adjust brightness
                if (increasing) {
                    brightness += 20;
                    if (brightness >= 255) {
                        brightness = 255;
                        increasing = false;
                    }
                } else {
                    brightness -= 20;
                    if (brightness <= 0) {
                        brightness = 0;
                        increasing = true;
                    }
                }
                
                // Apply brightness to color
                int adjustedColor = adjustColorBrightness(color, brightness / 255.0f);
                setLEDColor(adjustedColor);
                
                animationHandler.postDelayed(this, delayMs);
            }
        };
        
        animationHandler.post(animationRunnable);
    }
    
    /**
     * Animation: Blink (cho error)
     */
    private void startBlinkAnimation(final int color, final int delayMs) {
        isAnimating = true;
        
        animationRunnable = new Runnable() {
            private boolean isOn = true;
            
            @Override
            public void run() {
                if (!isAnimating) return;
                
                if (isOn) {
                    setLEDColor(color);
                } else {
                    setLEDColor(0x000000); // Off
                }
                
                isOn = !isOn;
                animationHandler.postDelayed(this, delayMs);
            }
        };
        
        animationHandler.post(animationRunnable);
    }
    
    /**
     * Stop current animation
     */
    private void stopAnimation() {
        if (animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
            animationRunnable = null;
        }
        isAnimating = false;
    }
    
    /**
     * Adjust color brightness
     */
    private int adjustColorBrightness(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        
        return Color.rgb(
            Math.min(255, Math.max(0, r)),
            Math.min(255, Math.max(0, g)),
            Math.min(255, Math.max(0, b))
        );
    }
    
    public int getCurrentState() {
        return currentState;
    }
    
    public boolean hasRootAccess() {
        return hasRootAccess;
    }
    
    @Override
    public void onDestroy() {
        stopAnimation();
        setLEDColor(0x000000); // Turn off
        super.onDestroy();
        Log.d(TAG, "LEDControlService destroyed");
    }
}