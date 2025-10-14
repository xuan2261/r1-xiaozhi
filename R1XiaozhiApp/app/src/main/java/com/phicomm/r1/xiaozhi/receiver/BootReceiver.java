package com.phicomm.r1.xiaozhi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.phicomm.r1.xiaozhi.config.XiaozhiConfig;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;

/**
 * Receiver để tự động khởi động các service khi R1 boot
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            
            Log.d(TAG, "Boot completed, starting Xiaozhi services");
            
            XiaozhiConfig config = new XiaozhiConfig(context);
            
            // Chỉ tự động start nếu user đã enable
            if (config.isAutoStart()) {
                // Start LED service first
                Intent ledIntent = new Intent(context, LEDControlService.class);
                ledIntent.setAction(LEDControlService.ACTION_SET_IDLE);
                context.startService(ledIntent);
                
                // Start Xiaozhi connection service
                Intent xiaozhiIntent = new Intent(context, XiaozhiConnectionService.class);
                context.startService(xiaozhiIntent);
                
                // Start voice recognition service
                Intent voiceIntent = new Intent(context, VoiceRecognitionService.class);
                context.startService(voiceIntent);
                
                Log.d(TAG, "All services started successfully");
            } else {
                Log.d(TAG, "Auto-start disabled in config");
            }
        }
    }
}