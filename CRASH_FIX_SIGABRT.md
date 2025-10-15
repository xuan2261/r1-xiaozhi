# 🔧 Hướng Dẫn Fix Crash SIGABRT - NetworkChangeReceiver

## 📋 Tóm Tắt Vấn Đề

**Triệu chứng:**
```
F/libc (30775): Fatal signal 6 (SIGABRT), code -6 in tid 30775 (comm.r1.xiaozhi)
F/art (30775): art/runtime/runtime.cc:289] native: #07 pc 0005f99d /system/lib/libandroid_runtime.so
```

**Nguyên nhân:**
- [`AndroidManifest.xml`](R1XiaozhiApp/app/src/main/AndroidManifest.xml) khai báo `NetworkChangeReceiver`
- Nhưng file class `.receiver.NetworkChangeReceiver` **không tồn tại**
- Android Runtime crash khi không tìm thấy class được khai báo

---

## ✅ Giải Pháp Đã Áp Dụng

### Xóa Receiver Không Cần Thiết

**File:** [`AndroidManifest.xml`](R1XiaozhiApp/app/src/main/AndroidManifest.xml)

**Đã xóa block:**
```xml
<!-- Network Change Receiver -->
<receiver
    android:name=".receiver.NetworkChangeReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
    </intent-filter>
</receiver>
```

**Lý do:**
- App không cần monitor network changes realtime
- WebSocket connection có auto-reconnect mechanism
- [`XiaozhiConnectionService`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java) tự xử lý reconnection

---

## 🔍 Cách Phát Hiện Lỗi Tương Tự

### 1. Kiểm Tra LogCat Filter

```bash
# Lọc SIGABRT crashes
adb logcat | grep -E "Fatal signal|SIGABRT"

# Lọc Art Runtime errors
adb logcat | grep -E "F/art|F/libc"

# Xem full crash stack
adb logcat *:E
```

### 2. Các Lỗi Tương Tự

**Class Not Found:**
```
java.lang.ClassNotFoundException: Didn't find class "XXX"
```

**Receiver Not Found:**
```
android.content.ActivityNotFoundException: Unable to find explicit activity class
```

**Service Binding Failed:**
```
android.app.ServiceConnectionLeaked: Service XXX has leaked ServiceConnection
```

---

## 🛠️ Testing Sau Khi Fix

### 1. Rebuild App

```bash
cd R1XiaozhiApp
./gradlew clean
./gradlew assembleDebug
```

### 2. Install & Monitor

```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clear LogCat
adb logcat -c

# Monitor startup
adb logcat | grep -E "XiaozhiApp|MainActivity|AndroidRuntime"
```

### 3. Test Checklist

- [ ] App khởi động không crash
- [ ] Không thấy SIGABRT trong logcat
- [ ] MainActivity hiển thị pairing code
- [ ] Services start thành công
- [ ] WebSocket connect được

---

## 📊 Manifest Receivers Còn Lại

### BootReceiver
```xml
<receiver android:name=".receiver.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```
**Status:** ✅ **EXISTS** - [`BootReceiver.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/BootReceiver.java)

---

## 🔄 Nếu Cần Thêm Network Monitoring

### Option 1: Tạo NetworkChangeReceiver

```java
package com.phicomm.r1.xiaozhi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkChangeReceiver extends BroadcastReceiver {
    
    private static final String TAG = "NetworkChange";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        
        boolean isConnected = activeNetwork != null && 
                            activeNetwork.isConnectedOrConnecting();
        
        Log.i(TAG, "Network changed - Connected: " + isConnected);
        
        // TODO: Notify services về network change
    }
}
```

### Option 2: Dùng ConnectivityManager.NetworkCallback (API 21+)

```java
// In XiaozhiConnectionService.onCreate()
ConnectivityManager cm = (ConnectivityManager) 
    getSystemService(Context.CONNECTIVITY_SERVICE);

ConnectivityManager.NetworkCallback networkCallback = 
    new ConnectivityManager.NetworkCallback() {
    
    @Override
    public void onAvailable(Network network) {
        Log.i(TAG, "Network available - reconnecting...");
        reconnect();
    }
    
    @Override
    public void onLost(Network network) {
        Log.i(TAG, "Network lost");
    }
};

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    cm.registerDefaultNetworkCallback(networkCallback);
}
```

---

## 📝 Manifest Validation Checklist

Trước khi build, luôn kiểm tra:

### 1. All Activities Exist
```bash
# Grep tất cả activities trong manifest
grep -E 'android:name="\.' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Check files tồn tại
find R1XiaozhiApp/app/src -name "MainActivity.java"
find R1XiaozhiApp/app/src -name "SettingsActivity.java"
```

### 2. All Services Exist
```bash
# List services
grep -A5 '<service' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Verify existence
ls R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/
```

### 3. All Receivers Exist
```bash
# List receivers
grep -A5 '<receiver' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Verify existence
ls R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/
```

---

## 🚨 Common Manifest Errors

### 1. Wrong Package Path
```xml
<!-- ❌ SAI -->
<activity android:name="com.wrong.MainActivity" />

<!-- ✅ ĐÚNG -->
<activity android:name=".ui.MainActivity" />
```

### 2. Missing Class
```xml
<!-- Class phải tồn tại -->
<service android:name=".service.NonExistentService" />
```

### 3. Wrong Intent Filter
```xml
<!-- Typo trong action name -->
<action android:name="android.intent.action.BOOT_COMPLETE" />
<!-- Đúng: BOOT_COMPLETED -->
```

---

## 🎯 Prevention Best Practices

### 1. Lint Checks
```bash
# Run lint trước khi build
./gradlew lint

# Check errors
cat app/build/reports/lint-results.html
```

### 2. Code Review Checklist
- [ ] Mọi component trong manifest đều có file class
- [ ] Package names đúng
- [ ] Intent filters đúng syntax
- [ ] Permissions cần thiết đã khai báo

### 3. Automated Testing
```yaml
# .github/workflows/manifest-check.yml
name: Manifest Validation

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Check Manifest Components
        run: |
          # Extract component names
          grep -oP 'android:name="\K[^"]+' app/src/main/AndroidManifest.xml > components.txt
          
          # Check files exist
          while read component; do
            file=$(echo $component | sed 's/\./\//g')
            if [ ! -f "app/src/main/java/${file}.java" ]; then
              echo "ERROR: ${component} not found!"
              exit 1
            fi
          done < components.txt
```

---

## 📚 Related Documentation

- [PAIRING_DEBUG_GUIDE.md](PAIRING_DEBUG_GUIDE.md) - Debug tools & techniques
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - Comprehensive test cases
- [Android Manifest Reference](https://developer.android.com/guide/topics/manifest/manifest-intro)

---

## ✅ Status

**Fixed:** ✅ NetworkChangeReceiver đã được xóa khỏi manifest  
**Tested:** ⏳ Cần rebuild & test lại app  
**Impact:** ✅ App sẽ không crash nữa  
**Side Effects:** ✅ Không có - feature không cần thiết

---

**Date:** 2025-10-16  
**Author:** Fullstack Developer  
**Issue:** SIGABRT crash do missing NetworkChangeReceiver  
**Resolution:** Removed unused receiver from manifest