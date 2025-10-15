package com.phicomm.r1.xiaozhi;

import android.app.Application;
import android.util.Log;

/**
 * Application class - Khởi tạo global configurations
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
    }
}