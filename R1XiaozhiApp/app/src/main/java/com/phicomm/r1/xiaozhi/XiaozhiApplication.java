package com.phicomm.r1.xiaozhi;

import android.app.Application;
import android.util.Log;

import com.phicomm.r1.xiaozhi.core.XiaozhiCore;

/**
 * Application class - Khởi tạo XiaozhiCore và global configurations
 */
public class XiaozhiApplication extends Application {
    
    private static final String TAG = "XiaozhiApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "===========================================");
        Log.i(TAG, "Xiaozhi Application started");
        Log.i(TAG, "Package: " + getPackageName());
        Log.i(TAG, "===========================================");
        
        // Initialize XiaozhiCore với application context
        XiaozhiCore core = XiaozhiCore.getInstance();
        core.initialize(this);
        
        Log.i(TAG, "XiaozhiCore initialized");
        Log.i(TAG, "Initial state: " + core.getStateSnapshot());
        Log.i(TAG, "===========================================");
    }
    
    @Override
    public void onTerminate() {
        // Cleanup XiaozhiCore khi app terminate
        Log.i(TAG, "Application terminating...");
        XiaozhiCore.getInstance().shutdown();
        super.onTerminate();
    }
}