package com.phicomm.r1.xiaozhi;

import android.app.Application;
import android.util.Log;

import com.jakewharton.timber.log.Timber;

/**
 * Application class - Khởi tạo global configurations
 */
public class XiaozhiApplication extends Application {
    
    private static final String TAG = "XiaozhiApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        
        Log.d(TAG, "Xiaozhi Application started");
    }
}