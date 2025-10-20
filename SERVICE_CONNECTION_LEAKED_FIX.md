# Service Connection Leaked Fix - MainActivity Lifecycle Issue

## 📋 EXECUTIVE SUMMARY

**Date**: 2025-10-20  
**Severity**: ❌ CRITICAL  
**Status**: ✅ FIXED

ServiceConnectionLeaked error ngăn cản activation flow hoàn thành do MainActivity không unbind service properly khi destroyed.

---

## 🔍 ROOT CAUSE ANALYSIS

### **Logcat Evidence**

```
D/MainActivity( 2636): Status: Dang bat dau kich hoat...
I/MainActivity( 2636): Starting connection/activation...
I/MainActivity( 2636): MainActivity onDestroy() called
I/MainActivity( 2636): Service is bound but NOT unbinding - keeping service alive
I/MainActivity( 2636): Service will continue running in background
I/MainActivity( 2636): MainActivity destroyed
E/ActivityThread( 2636): Activity com.phicomm.r1.xiaozhi.ui.MainActivity has leaked ServiceConnection com.phicomm.r1.xiaozhi.ui.MainActivity$1@275c1f that was originally bound here
E/ActivityThread( 2636):        at com.phicomm.r1.xiaozhi.ui.MainActivity.bindConnectionService(MainActivity.java:452)
E/ActivityThread( 2636):        at com.phicomm.r1.xiaozhi.ui.MainActivity.initializeServices(MainActivity.java:430)
E/ActivityThread( 2636):        at com.phicomm.r1.xiaozhi.ui.MainActivity.onCreate(MainActivity.java:121)
```

### **3 VẤN ĐỀ CHÍNH**

---

## 🐛 PROBLEM #1: ServiceConnectionLeaked ❌ CRITICAL

### **Root Cause**

**Code Location**: `MainActivity.java` (Line 694)

**Before Fix**:
```java
@Override
protected void onDestroy() {
    // FIX: Do NOT unbind service when MainActivity is destroyed
    if (xiaozhiBound) {
        Log.i(TAG, "Service is bound but NOT unbinding - keeping service alive");
        // Do NOT call unbindService() - let service stay alive
        // unbindService(xiaozhiConnection);  // ❌ COMMENTED OUT!
        xiaozhiBound = false;
    }
    super.onDestroy();
}
```

**Why This Is Wrong**:
1. ❌ Service connection MUST be unbound when Activity destroyed
2. ❌ Not unbinding = Memory leak
3. ❌ Android system detects leaked connection → Error
4. ❌ Leaked connection prevents proper cleanup

**Execution Flow**:
```
1. onCreate() → bindService(xiaozhiConnection) ✅
2. Service bound → xiaozhiBound = true ✅
3. MainActivity destroyed (by launcher) ❌
4. onDestroy() → unbindService() SKIPPED ❌
5. Android detects leaked connection ❌
6. ServiceConnectionLeaked error thrown ❌
```

**Misconception**:
- Previous fix (commit 642c90d) thought: "Don't unbind = service stays alive"
- **WRONG!** Service stays alive because:
  - `stopWithTask="false"` in AndroidManifest
  - `START_STICKY` in onStartCommand()
  - Service started with `startService()` (not just bound)

**Correct Understanding**:
- Binding is for **communication** (get service instance)
- Starting is for **lifecycle** (keep service running)
- MUST unbind when Activity destroyed (cleanup)
- Service continues running because it was started

---

## 🐛 PROBLEM #2: MainActivity Destroyed by Launcher ❌ HIGH

### **Root Cause**

**Evidence from Logcat**:
```
I/ActivityStackSupervisor(  494): START u0 {act=android.intent.action.MAIN 
cat=[android.intent.category.HOME] flg=0x10000000 
cmp=com.phicomm.speaker.launcher/.MainActivity} from uid 0 on display 0

I/ActivityManagerService(  494): Start proc 713:com.phicomm.speaker.launcher/u0a6 
for activity com.phicomm.speaker.launcher/.MainActivity
```

**Execution Flow**:
```
1. User opens R1 Xiaozhi app
   → com.phicomm.r1.xiaozhi.ui.MainActivity starts ✅

2. MainActivity.onCreate()
   → initializeServices() ✅
   → bindConnectionService() ✅
   → Start activation... ✅

3. System detects HOME category
   → Phicomm Launcher has priority ❌
   → System starts com.phicomm.speaker.launcher/.MainActivity ❌

4. R1 Xiaozhi MainActivity destroyed
   → onDestroy() called ❌
   → Activation interrupted ❌
```

**Why Launcher Takes Over**:
- R1 device has custom launcher: `com.phicomm.speaker.launcher`
- Launcher has `android.intent.category.HOME`
- System prioritizes launcher when:
  - User presses Home button
  - Device boots
  - Memory pressure (system kills background apps)

**Impact**:
- ❌ MainActivity destroyed before activation completes
- ❌ Service listener not setup (setupServiceListener() interrupted)
- ❌ Activation callbacks lost
- ❌ No WebSocket connection established

---

## 🐛 PROBLEM #3: No Re-binding on Activity Recreate ❌ HIGH

### **Root Cause**

**Missing Code**: No `onResume()` to re-bind service

**Scenario**:
```
1. MainActivity created → bindService() ✅
2. MainActivity destroyed by launcher → unbindService() ❌ (was commented out)
3. User opens app again → MainActivity recreated ✅
4. onCreate() checks xiaozhiBound flag ❌
5. Flag is false (new activity instance) ❌
6. But initializeServices() only called if permissions not granted ❌
7. Service NOT re-bound ❌
```

**Impact**:
- ❌ Service running but MainActivity not connected
- ❌ No UI updates
- ❌ No activation callbacks
- ❌ User sees stale UI

---

## 🔧 FIXES IMPLEMENTED

### **Fix #1: Unbind Service Properly** ✅

**File**: `MainActivity.java` (Line 674-706)

**After Fix**:
```java
@Override
protected void onDestroy() {
    Log.i(TAG, "MainActivity onDestroy() called");

    // Unregister event listeners
    if (eventBus != null) {
        if (stateListener != null) {
            eventBus.unregister(StateChangedEvent.class, stateListener);
        }
        if (connectionListener != null) {
            eventBus.unregister(ConnectionEvent.class, connectionListener);
        }
    }

    // FIX: MUST unbind service to prevent ServiceConnectionLeaked error
    // Service will continue running because:
    // 1. stopWithTask="false" in AndroidManifest
    // 2. START_STICKY in onStartCommand()
    // 3. Service was started with startService() (not just bound)
    if (xiaozhiBound) {
        Log.i(TAG, "Unbinding service - service will continue running in background");
        try {
            unbindService(xiaozhiConnection);
            xiaozhiBound = false;
            Log.i(TAG, "Service unbound successfully");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error unbinding service: " + e.getMessage());
        }
    }

    super.onDestroy();
    Log.i(TAG, "MainActivity destroyed");
}
```

**Changes**:
1. ✅ Uncommented `unbindService(xiaozhiConnection)`
2. ✅ Added try-catch for IllegalArgumentException
3. ✅ Added detailed logging
4. ✅ Updated comments to explain why service continues running

**Effect**:
- ✅ No more ServiceConnectionLeaked error
- ✅ Proper cleanup when Activity destroyed
- ✅ Service continues running (stopWithTask=false + START_STICKY)

---

### **Fix #2: Re-bind Service on Activity Resume** ✅

**File**: `MainActivity.java` (Line 127-144)

**New Method**:
```java
@Override
protected void onResume() {
    super.onResume();
    Log.i(TAG, "MainActivity onResume() called");
    
    // FIX: Re-bind to service if not already bound
    // This handles case where MainActivity is recreated (e.g., after being destroyed by launcher)
    if (!xiaozhiBound && permissionsGranted) {
        Log.i(TAG, "Service not bound - re-binding...");
        bindConnectionService();
    }
}
```

**Changes**:
1. ✅ Added onResume() lifecycle method
2. ✅ Check if service is bound
3. ✅ Re-bind if not bound and permissions granted
4. ✅ Handles MainActivity recreation scenario

**Effect**:
- ✅ Service re-bound when MainActivity recreated
- ✅ UI updates resume
- ✅ Activation callbacks work
- ✅ User sees correct status

---

## 📊 EXECUTION FLOW COMPARISON

### **Before Fix** ❌

```
1. MainActivity.onCreate()
   → bindService() ✅

2. Service bound
   → xiaozhiBound = true ✅

3. Launcher starts
   → MainActivity.onDestroy() ❌
   → unbindService() SKIPPED ❌
   → ServiceConnectionLeaked error ❌

4. User opens app again
   → MainActivity.onCreate() ✅
   → initializeServices() SKIPPED (permissions already granted) ❌
   → Service NOT re-bound ❌
   → No UI updates ❌
```

### **After Fix** ✅

```
1. MainActivity.onCreate()
   → bindService() ✅

2. Service bound
   → xiaozhiBound = true ✅

3. Launcher starts
   → MainActivity.onDestroy() ✅
   → unbindService() called ✅
   → xiaozhiBound = false ✅
   → Service continues running (stopWithTask=false) ✅

4. User opens app again
   → MainActivity.onCreate() ✅
   → MainActivity.onResume() ✅
   → Check xiaozhiBound = false ✅
   → bindService() called ✅
   → Service re-bound ✅
   → UI updates resume ✅
```

---

## 📝 SUMMARY

| Issue | Severity | Root Cause | Status |
|-------|----------|------------|--------|
| **ServiceConnectionLeaked** | ❌ CRITICAL | unbindService() commented out | ✅ FIXED |
| **MainActivity Destroyed** | ⚠️ HIGH | Launcher takes priority | ✅ HANDLED |
| **No Re-binding** | ⚠️ HIGH | Missing onResume() | ✅ FIXED |

---

## 🚀 FILES MODIFIED

1. **MainActivity.java**
   - Fixed onDestroy() to unbind service properly
   - Added onResume() to re-bind service when recreated
   - Added try-catch for unbind errors
   - Updated comments

---

## 🧪 EXPECTED BEHAVIOR AFTER FIX

### **Scenario 1: Normal Flow**

```
1. User opens app
   → MainActivity.onCreate() → bindService() ✅

2. Service bound
   → setupServiceListener() ✅
   → Start activation ✅

3. Activation completes
   → WebSocket connected ✅
   → UI updated ✅
```

### **Scenario 2: Launcher Interruption**

```
1. User opens app
   → MainActivity.onCreate() → bindService() ✅

2. Launcher starts
   → MainActivity.onDestroy() → unbindService() ✅
   → Service continues running ✅

3. User opens app again
   → MainActivity.onResume() → bindService() ✅
   → Service re-bound ✅
   → UI updated with current status ✅
```

### **Scenario 3: Activation During Interruption**

```
1. User starts activation
   → Activation process running in service ✅

2. Launcher starts
   → MainActivity destroyed ✅
   → Service continues activation ✅

3. Activation completes
   → WebSocket connected ✅
   → Token saved ✅

4. User opens app again
   → MainActivity.onResume() → bindService() ✅
   → checkActivationStatus() ✅
   → UI shows "Connected" ✅
```

---

**Last Updated**: 2025-10-20  
**Fixed By**: Augment Agent  
**Commit**: Pending

