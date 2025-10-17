# 🚀 Hướng Dẫn Triển Khai Chi Tiết

## 📋 Mục Lục

1. [Chuẩn Bị](#chuẩn-bị)
2. [Phase 1: Core Components](#phase-1-core-components)
3. [Phase 2: Service Refactoring](#phase-2-service-refactoring)
4. [Phase 3: Testing & Optimization](#phase-3-testing--optimization)
5. [Migration Guide](#migration-guide)
6. [Troubleshooting](#troubleshooting)

---

## 🎯 Chuẩn Bị

### 1. Backup Code Hiện Tại

```bash
# Tạo branch mới
git checkout -b feature/core-refactoring

# Commit tất cả thay đổi hiện tại
git add .
git commit -m "Backup before core refactoring"
```

### 2. Tạo Package Structure

```
com.phicomm.r1.xiaozhi/
├── core/              # [NEW] Core components
│   ├── XiaozhiCore.java
│   ├── DeviceState.java
│   ├── ListeningMode.java
│   ├── EventBus.java
│   └── TaskManager.java
├── events/            # [NEW] Event definitions
│   ├── StateChangedEvent.java
│   ├── ConnectionEvent.java
│   ├── MessageReceivedEvent.java
│   └── AudioEvent.java
├── service/           # [EXISTING] Services
│   └── ...
└── ui/                # [EXISTING] UI components
    └── ...
```

---

## 🔧 Phase 1: Core Components

### Step 1: Tạo DeviceState.java

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/DeviceState.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/DeviceState.java)

```java
package com.phicomm.r1.xiaozhi.core;

/**
 * Device states từ py-xiaozhi
 */
public enum DeviceState {
    IDLE("idle"),
    LISTENING("listening"),
    SPEAKING("speaking");
    
    private final String value;
    
    DeviceState(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static DeviceState fromString(String value) {
        for (DeviceState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return IDLE;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
```

**Testing:**
```java
@Test
public void testDeviceStateEnum() {
    assertEquals("idle", DeviceState.IDLE.getValue());
    assertEquals(DeviceState.LISTENING, DeviceState.fromString("listening"));
}
```

### Step 2: Tạo ListeningMode.java

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/ListeningMode.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/ListeningMode.java)

```java
package com.phicomm.r1.xiaozhi.core;

/**
 * Listening modes từ py-xiaozhi:
 * - MANUAL: Push-to-talk (người dùng giữ nút)
 * - AUTO_STOP: Tự động dừng khi detect silence
 * - REALTIME: Continuous listening với AEC
 */
public enum ListeningMode {
    MANUAL("manual"),
    AUTO_STOP("auto_stop"),
    REALTIME("realtime");
    
    private final String value;
    
    ListeningMode(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ListeningMode fromString(String value) {
        for (ListeningMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return AUTO_STOP;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
```

### Step 3: Tạo Event Classes

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/StateChangedEvent.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/StateChangedEvent.java)

```java
package com.phicomm.r1.xiaozhi.events;

import com.phicomm.r1.xiaozhi.core.DeviceState;

public class StateChangedEvent {
    public final DeviceState oldState;
    public final DeviceState newState;
    public final long timestamp;
    
    public StateChangedEvent(DeviceState oldState, DeviceState newState) {
        this.oldState = oldState;
        this.newState = newState;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "StateChangedEvent{" +
                "oldState=" + oldState +
                ", newState=" + newState +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/ConnectionEvent.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/ConnectionEvent.java)

```java
package com.phicomm.r1.xiaozhi.events;

public class ConnectionEvent {
    public final boolean connected;
    public final String message;
    public final long timestamp;
    
    public ConnectionEvent(boolean connected, String message) {
        this.connected = connected;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "ConnectionEvent{" +
                "connected=" + connected +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/MessageReceivedEvent.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/MessageReceivedEvent.java)

```java
package com.phicomm.r1.xiaozhi.events;

import org.json.JSONObject;

public class MessageReceivedEvent {
    public final JSONObject message;
    public final String messageType;
    public final long timestamp;
    
    public MessageReceivedEvent(JSONObject message) {
        this.message = message;
        this.messageType = message.optJSONObject("header") != null
            ? message.optJSONObject("header").optString("name", "unknown")
            : "unknown";
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "MessageReceivedEvent{" +
                "messageType='" + messageType + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

### Step 4: Tạo EventBus.java

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/EventBus.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/EventBus.java)

```java
package com.phicomm.r1.xiaozhi.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event broadcasting system theo mô hình py-xiaozhi
 * Thread-safe và post events trên main thread
 */
public class EventBus {
    
    private static final String TAG = "EventBus";
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    
    /**
     * Register một listener cho event type cụ thể
     */
    public <T> void register(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners == null) {
            eventListeners = new CopyOnWriteArrayList<>();
            listeners.put(eventType, eventListeners);
        }
        eventListeners.add(listener);
        Log.d(TAG, "Registered listener for " + eventType.getSimpleName());
    }
    
    /**
     * Unregister một listener
     */
    public <T> void unregister(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            Log.d(TAG, "Unregistered listener for " + eventType.getSimpleName());
        }
    }
    
    /**
     * Post event tới tất cả listeners (trên main thread)
     */
    public <T> void post(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            Log.d(TAG, "No listeners for " + eventType.getSimpleName());
            return;
        }
        
        Log.d(TAG, "Broadcasting " + eventType.getSimpleName() + " to " + 
              eventListeners.size() + " listeners");
        
        for (final EventListener listener : eventListeners) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in event listener", e);
                    }
                }
            });
        }
    }
    
    /**
     * Post event ngay lập tức (trên current thread)
     * CẢNH BÁO: Chỉ dùng nếu chắc chắn đang ở main thread
     */
    public <T> void postSync(final T event) {
        if (event == null) {
            Log.w(TAG, "Cannot post null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<EventListener<?>> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }
        
        for (final EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                Log.e(TAG, "Error in event listener", e);
            }
        }
    }
    
    /**
     * Clear tất cả listeners
     */
    public void clear() {
        listeners.clear();
        Log.d(TAG, "Cleared all listeners");
    }
    
    /**
     * Event listener interface
     */
    public interface EventListener<T> {
        void onEvent(T event);
    }
}
```

### Step 5: Tạo XiaozhiCore.java (Singleton)

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/XiaozhiCore.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/XiaozhiCore.java)

```java
package com.phicomm.r1.xiaozhi.core;

import android.content.Context;
import android.util.Log;

import com.phicomm.r1.xiaozhi.events.StateChangedEvent;
import com.phicomm.r1.xiaozhi.service.AudioPlaybackService;
import com.phicomm.r1.xiaozhi.service.LEDControlService;
import com.phicomm.r1.xiaozhi.service.VoiceRecognitionService;
import com.phicomm.r1.xiaozhi.service.XiaozhiConnectionService;

/**
 * Core singleton theo mô hình py-xiaozhi Application class
 * Quản lý centralized state và coordination giữa các services
 */
public class XiaozhiCore {
    
    private static final String TAG = "XiaozhiCore";
    
    // Thread-safe singleton
    private static volatile XiaozhiCore instance;
    private static final Object lock = new Object();
    
    // Event bus
    private final EventBus eventBus;
    
    // Device state (thread-safe)
    private volatile DeviceState deviceState = DeviceState.IDLE;
    private volatile ListeningMode listeningMode = ListeningMode.AUTO_STOP;
    private volatile boolean keepListening = false;
    private volatile boolean aecEnabled = true;
    
    // Service references (set by services khi bind)
    private XiaozhiConnectionService connectionService;
    private AudioPlaybackService audioService;
    private VoiceRecognitionService voiceService;
    private LEDControlService ledService;
    
    // Application context
    private Context applicationContext;
    
    /**
     * Private constructor để enforce singleton
     */
    private XiaozhiCore() {
        this.eventBus = new EventBus();
        Log.i(TAG, "XiaozhiCore initialized");
    }
    
    /**
     * Get singleton instance (thread-safe double-checked locking)
     */
    public static XiaozhiCore getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new XiaozhiCore();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize với application context
     */
    public void initialize(Context context) {
        if (this.applicationContext == null) {
            this.applicationContext = context.getApplicationContext();
            Log.i(TAG, "XiaozhiCore initialized with context");
        }
    }
    
    // ==================== State Management ====================
    
    /**
     * Set device state (thread-safe)
     * Broadcast StateChangedEvent nếu state thay đổi
     */
    public synchronized void setDeviceState(DeviceState newState) {
        if (this.deviceState != newState) {
            DeviceState oldState = this.deviceState;
            this.deviceState = newState;
            
            Log.i(TAG, "State changed: " + oldState + " -> " + newState);
            
            // Broadcast event
            eventBus.post(new StateChangedEvent(oldState, newState));
        }
    }
    
    /**
     * Get current device state
     */
    public DeviceState getDeviceState() {
        return deviceState;
    }
    
    /**
     * Check if device is idle
     */
    public boolean isIdle() {
        return deviceState == DeviceState.IDLE;
    }
    
    /**
     * Check if device is listening
     */
    public boolean isListening() {
        return deviceState == DeviceState.LISTENING;
    }
    
    /**
     * Check if device is speaking
     */
    public boolean isSpeaking() {
        return deviceState == DeviceState.SPEAKING;
    }
    
    // ==================== Listening Mode ====================
    
    /**
     * Set listening mode
     */
    public synchronized void setListeningMode(ListeningMode mode) {
        if (this.listeningMode != mode) {
            Log.i(TAG, "Listening mode changed: " + this.listeningMode + " -> " + mode);
            this.listeningMode = mode;
        }
    }
    
    /**
     * Get current listening mode
     */
    public ListeningMode getListeningMode() {
        return listeningMode;
    }
    
    /**
     * Set keep listening flag
     */
    public void setKeepListening(boolean keepListening) {
        this.keepListening = keepListening;
        Log.d(TAG, "Keep listening: " + keepListening);
    }
    
    /**
     * Check if keep listening is enabled
     */
    public boolean isKeepListening() {
        return keepListening;
    }
    
    /**
     * Set AEC enabled
     */
    public void setAecEnabled(boolean enabled) {
        this.aecEnabled = enabled;
        Log.d(TAG, "AEC enabled: " + enabled);
    }
    
    /**
     * Check if AEC is enabled
     */
    public boolean isAecEnabled() {
        return aecEnabled;
    }
    
    // ==================== Service References ====================
    
    public void setConnectionService(XiaozhiConnectionService service) {
        this.connectionService = service;
    }
    
    public XiaozhiConnectionService getConnectionService() {
        return connectionService;
    }
    
    public void setAudioService(AudioPlaybackService service) {
        this.audioService = service;
    }
    
    public AudioPlaybackService getAudioService() {
        return audioService;
    }
    
    public void setVoiceService(VoiceRecognitionService service) {
        this.voiceService = service;
    }
    
    public VoiceRecognitionService getVoiceService() {
        return voiceService;
    }
    
    public void setLedService(LEDControlService service) {
        this.ledService = service;
    }
    
    public LEDControlService getLedService() {
        return ledService;
    }
    
    // ==================== EventBus Access ====================
    
    /**
     * Get EventBus instance
     */
    public EventBus getEventBus() {
        return eventBus;
    }
    
    // ==================== Context Access ====================
    
    /**
     * Get application context
     */
    public Context getApplicationContext() {
        return applicationContext;
    }
    
    // ==================== State Snapshot ====================
    
    /**
     * Get snapshot của toàn bộ state (cho debugging)
     */
    public String getStateSnapshot() {
        return "XiaozhiCore{" +
                "deviceState=" + deviceState +
                ", listeningMode=" + listeningMode +
                ", keepListening=" + keepListening +
                ", aecEnabled=" + aecEnabled +
                ", connectionService=" + (connectionService != null ? "bound" : "null") +
                ", audioService=" + (audioService != null ? "bound" : "null") +
                ", voiceService=" + (voiceService != null ? "bound" : "null") +
                ", ledService=" + (ledService != null ? "bound" : "null") +
                '}';
    }
}
```

---

## 🔄 Phase 2: Service Refactoring

### Step 6: Update XiaozhiApplication.java

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/XiaozhiApplication.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/XiaozhiApplication.java)

```java
package com.phicomm.r1.xiaozhi;

import android.app.Application;
import android.util.Log;

import com.phicomm.r1.xiaozhi.core.XiaozhiCore;

/**
 * Application class - Khởi tạo XiaozhiCore
 */
public class XiaozhiApplication extends Application {
    
    private static final String TAG = "XiaozhiApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize XiaozhiCore với application context
        XiaozhiCore.getInstance().initialize(this);
        
        Log.i(TAG, "===========================================");
        Log.i(TAG, "Xiaozhi Application started");
        Log.i(TAG, "Package: " + getPackageName());
        Log.i(TAG, "XiaozhiCore: " + XiaozhiCore.getInstance().getStateSnapshot());
        Log.i(TAG, "===========================================");
    }
}
```

### Step 7: Update XiaozhiConnectionService.java

Thêm vào đầu class:

```java
public class XiaozhiConnectionService extends Service {
    
    private static final String TAG = "XiaozhiConnection";
    
    // Add XiaozhiCore reference
    private XiaozhiCore core;
    private EventBus eventBus;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Get XiaozhiCore instance
        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        
        // Register this service với core
        core.setConnectionService(this);
        
        Log.i(TAG, "Service created and registered with XiaozhiCore");
    }
```

Update `handleAuthorizeResponse`:

```java
private void handleAuthorizeResponse(JSONObject json) {
    try {
        JSONObject payload = json.getJSONObject("payload");
        String codeStr = payload.optString("code", "-1");
        
        if ("0".equals(codeStr)) {
            Log.i(TAG, "Pairing SUCCESS!");
            
            // Reset retry
            retryCount = 0;
            isRetrying = false;
            
            // Mark as paired
            PairingCodeGenerator.markAsPaired(this);
            
            // Post event thay vì callback
            eventBus.post(new ConnectionEvent(true, "Pairing success"));
            
            // Update state
            core.setDeviceState(DeviceState.IDLE);
            
            // Legacy callback (giữ cho backward compatibility)
            if (connectionListener != null) {
                connectionListener.onPairingSuccess();
            }
        } else {
            // Handle error...
            eventBus.post(new ConnectionEvent(false, errorMsg));
        }
        
    } catch (JSONException e) {
        Log.e(TAG, "Failed to parse Authorize response", e);
    }
}
```

Update `handleMessage`:

```java
private void handleMessage(String message) {
    try {
        JSONObject json = new JSONObject(message);
        
        // Broadcast message received event
        eventBus.post(new MessageReceivedEvent(json));
        
        // Check if this is Authorize response
        if (json.has("header")) {
            JSONObject header = json.getJSONObject("header");
            String name = header.optString("name", "");
            
            if ("Authorize".equals(name)) {
                handleAuthorizeResponse(json);
                return;
            }
        }
        
        // Handle TTS messages
        String type = json.optString("type");
        if ("tts".equals(type)) {
            handleTTSMessage(json);
        }
        
        // Legacy callback
        if (connectionListener != null) {
            connectionListener.onMessage(message);
        }
        
    } catch (JSONException e) {
        Log.w(TAG, "Failed to parse message", e);
    }
}

private void handleTTSMessage(JSONObject json) {
    try {
        String state = json.optString("state");
        
        if ("start".equals(state)) {
            // Check listening mode
            if (core.isKeepListening() && 
                core.getListeningMode() == ListeningMode.REALTIME) {
                // Keep listening during TTS in realtime mode
                core.setDeviceState(DeviceState.LISTENING);
            } else {
                core.setDeviceState(DeviceState.SPEAKING);
            }
        } else if ("stop".equals(state)) {
            if (core.isKeepListening()) {
                // Resume listening
                core.setDeviceState(DeviceState.LISTENING);
                // Optionally restart listening
                sendStartListening(core.getListeningMode());
            } else {
                core.setDeviceState(DeviceState.IDLE);
            }
        }
        
    } catch (Exception e) {
        Log.e(TAG, "Error handling TTS message", e);
    }
}
```

---

## 📱 Phase 3: Update MainActivity

### Step 8: Refactor MainActivity.java

**File**: [`R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java)

```java
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
import android.widget.Toast;

import com.phicomm.r1.xiaozhi.R;
import com.phicomm.r1.xiaozhi.core.DeviceState;
import com.phicomm.r1.xiaozhi.core.EventBus;
import com.phicomm.r1.xiaozhi.core.XiaozhiCore;
import com.phicomm.r1.xiaozhi.events.ConnectionEvent;
import com.phicomm.r1.xiaozhi.events.StateChangedEvent;
import com.phicomm.r1.xiaozhi.service.*;
import com.phicomm.r1.xiaozhi.util.PairingCodeGenerator;

/**
 * MainActivity sử dụng XiaozhiCore và EventBus
 */
public class MainActivity extends Activity {
    
    private static final String TAG = "MainActivity";
    
    // UI components
    private TextView statusText;
    private TextView pairingCodeText;
    private TextView stateText;
    private Button connectButton;
    private Button copyButton;
    private Button resetButton;
    
    // Core
    private XiaozhiCore core;
    private EventBus eventBus;
    
    // Services
    private XiaozhiConnectionService xiaozhiService;
    private boolean xiaozhiBound = false;
    
    // Event listeners
    private EventBus.EventListener<StateChangedEvent> stateListener;
    private EventBus.EventListener<ConnectionEvent> connectionListener;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize UI
        initializeViews();
        
        // Get XiaozhiCore
        core = XiaozhiCore.getInstance();
        eventBus = core.getEventBus();
        
        // Register event listeners
        registerEventListeners();
        
        // Start services
        startAllServices();
        
        // Bind to connection service
        bindConnectionService();
        
        Log.i(TAG, "MainActivity created");
        Log.i(TAG, "Core state: " + core.getStateSnapshot());
    }
    
    private void initializeViews() {
        statusText = (TextView) findViewById(R.id.statusText);
        pairingCodeText = (TextView) findViewById(R.id.pairingCodeText);
        stateText = (TextView) findViewById(R.id.stateText);
        connectButton = (Button) findViewById(R.id.connectButton);
        copyButton = (Button) findViewById(R.id.copyButton);
        resetButton = (Button) findViewById(R.id.resetButton);
        
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToXiaozhi();
            }
        });
        
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyPairingCode();
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPairing();
            }
        });
    }
    
    private void registerEventListeners() {
        // State change listener
        stateListener = new EventBus.EventListener<StateChangedEvent>() {
            @Override
            public void onEvent(StateChangedEvent event) {
                onStateChanged(event);
            }
        };
        eventBus.register(StateChangedEvent.class, stateListener);
        
        // Connection event listener
        connectionListener = new EventBus.EventListener<ConnectionEvent>() {
            @Override
            public void onEvent(ConnectionEvent event) {
                onConnectionEvent(event);
            }
        };
        eventBus.register(ConnectionEvent.class, connectionListener);
        
        Log.d(TAG, "Event listeners registered");
    }
    
    private void onStateChanged(StateChangedEvent event) {
        Log.d(TAG, "State changed: " + event.oldState + " -> " + event.newState);
        
        // Update state display
        String stateDisplay = "Trang thai: ";
        switch (event.newState) {
            case IDLE:
                stateDisplay += "San sang";
                break;
            case LISTENING:
                stateDisplay += "Dang nghe...";
                break;
            case SPEAKING:
                stateDisplay += "Dang noi...";
                break;
        }
        
        stateText.setText(stateDisplay);
    }
    
    private void onConnectionEvent(ConnectionEvent event) {
        if (event.connected) {
            updateStatus("[OK] " + event.message);
            Toast.makeText(this, "Ket noi thanh cong!", Toast.LENGTH_SHORT).show();
            pairingCodeText.setText("Da Ghep Noi");
            connectButton.setEnabled(false);
        } else {
            updateStatus("[FAIL] " + event.message);
            Toast.makeText(this, "Loi: " + event.message, Toast.LENGTH_SHORT).show();
            connectButton.setEnabled(true);
        }
    }
    
    private void startAllServices() {
        startService(new Intent(this, VoiceRecognitionService.class));
        startService(new Intent(this, AudioPlaybackService.class));
        startService(new Intent(this, LEDControlService.class));
        startService(new Intent(this, HTTPServerService.class));
    }
    
    private void bindConnectionService() {
        Intent xiaozhiIntent = new Intent(this, XiaozhiConnectionService.class);
        startService(xiaozhiIntent);
        bindService(xiaozhiIntent, xiaozhiConnection, Context.BIND_AUTO_CREATE);
    }
    
    private final ServiceConnection xiaozhiConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XiaozhiConnectionService.LocalBinder binder = 
                (XiaozhiConnectionService.LocalBinder) service;
            xiaozhiService = binder.getService();
            xiaozhiBound = true;
            
            Log.i(TAG, "Xiaozhi service bound");
            checkPairingStatus();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            xiaozhiBound = false;
            Log.i(TAG, "Xiaozhi service unbound");
        }
    };
    
    private void checkPairingStatus() {
        boolean isPaired = PairingCodeGenerator.isPaired(this);
        
        if (isPaired) {
            updateStatus("[OK] Da ghep noi - San sang su dung");
            pairingCodeText.setText("[OK] Da Ghep Noi");
            connectButton.setEnabled(false);
        } else {
            String pairingCode = PairingCodeGenerator.getPairingCode(this);
            String formatted = PairingCodeGenerator.formatPairingCode(pairingCode);
            
            updateStatus("[!] Chua ghep noi");
            pairingCodeText.setText(formatted);
            connectButton.setEnabled(true);
            
            Log.i(TAG, "Pairing code: " + pairingCode);
        }
    }
    
    private void copyPairingCode() {
        // Copy code implementation...
    }
    
    private void connectToXiaozhi() {
        if (!xiaozhiBound) {
            Toast.makeText(this, "Service chua san sang", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("Dang ket noi...");
        connectButton.setEnabled(false);
        xiaozhiService.connect();
    }
    
    private void resetPairing() {
        // Reset implementation...
    }
    
    private void updateStatus(String status) {
        statusText.setText(status);
    }
    
    @Override
    protected void onDestroy() {
        // Unregister event listeners
        if (eventBus != null) {
            eventBus.unregister(StateChangedEvent.class, stateListener);
            eventBus.unregister(ConnectionEvent.class, connectionListener);
        }
        
        // Unbind service
        if (xiaozhiBound) {
            unbindService(xiaozhiConnection);
            xiaozhiBound = false;
        }
        
        super.onDestroy();
        Log.i(TAG, "MainActivity destroyed");
    }
}
```

---

## ✅ Testing Checklist

### Unit Tests

- [ ] DeviceState enum tests
- [ ] ListeningMode enum tests
- [ ] EventBus registration/unregistration
- [ ] EventBus post/broadcast
- [ ] XiaozhiCore singleton
- [ ] XiaozhiCore state management

### Integration Tests

- [ ] Service binding với Core
- [ ] Event broadcasting qua services
- [ ] State transitions
- [ ] UI updates từ events

### Manual Testing

- [ ] App startup
- [ ] Pairing flow
- [ ] State display updates
- [ ] Connection events
- [ ] Service lifecycle

---

## 🔧 Troubleshooting

### Issue: Events không được receive

**Giải pháp:**
1. Check listener đã register chưa
2. Verify event class đúng type
3. Check logs: "Broadcasting X to Y listeners"

### Issue: State không update

**Giải pháp:**
1. Verify `setDeviceState()` được gọi
2. Check thread safety
3. Verify event listener registered

### Issue: Service không bind được

**Giải pháp:**
1. Check AndroidManifest.xml
2. Verify service started trước khi bind
3. Check service onCreate() có initialize Core không

---

## 📚 Next Steps

Sau khi hoàn thành Phase 1-3:

1. [ ] Add more events (AudioEvent, etc.)
2. [ ] Implement TaskManager
3. [ ] Add performance monitoring
4. [ ] Write comprehensive tests
5. [ ] Update documentation

---

**Ngày tạo**: 2025-10-17  
**Phiên bản**: 1.0  
**Status**: ✅ Ready for implementation