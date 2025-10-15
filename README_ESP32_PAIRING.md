# R1 Xiaozhi - ESP32 Pairing Implementation

## 📖 Tổng quan

App Android tích hợp trợ lý giọng nói Xiaozhi cho loa Phicomm R1, implement theo chuẩn [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32).

### Đặc điểm chính
- ✅ **Local code generation** - Không cần API server
- ✅ **WebSocket + Authorize handshake** - Protocol đơn giản
- ✅ **Callback-driven** - Không polling
- ✅ **Offline-capable** - Code gen hoạt động kể cả không có mạng
- ✅ **Android 5.1+ compatible** - Hỗ trợ Phicomm R1 (API 22)

## 🚀 Quick Start

### 1. Build APK
```bash
cd R1XiaozhiApp
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Cài đặt lên R1
```bash
# Connect R1 qua ADB
adb connect <R1_IP>:5555

# Cài đặt
adb install -r app-debug.apk

# Khởi động
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### 3. Pairing với Xiaozhi Cloud

**Bước 1**: Mở app, sẽ thấy pairing code (6 ký tự)
```
Mã ghép nói: DD EE FF
```

**Bước 2**: Truy cập https://console.xiaozhi.ai

**Bước 3**: Nhập code `DDEEFF` vào console

**Bước 4**: Nhấn "Kết nối" trong app

**Bước 5**: App sẽ tự động xác nhận và hiển thị "✓ Đã ghép nối!"

Done! Giờ có thể dùng giọng nói.

## 🔧 Architecture

### Pairing Flow
```
┌─────────────┐
│   User      │
│ Opens App   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│ PairingCodeGenerator        │
│ - Get MAC address           │
│ - deviceId = MAC without :  │
│ - code = last 6 chars       │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Display Code: DD EE FF      │
└─────────────────────────────┘
       │
       │ User enters code
       │ in console.xiaozhi.ai
       │
       ▼
┌─────────────────────────────┐
│ User clicks "Connect"       │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ XiaozhiConnectionService    │
│ wss://xiaozhi.me/v1/ws      │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Send Authorize Handshake    │
│ {                           │
│   "header": {               │
│     "name": "Authorize",    │
│     "namespace": "..."      │
│   },                        │
│   "payload": {              │
│     "device_id": "...",     │
│     "pairing_code": "...",  │
│     "device_type": "..."    │
│   }                         │
│ }                           │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Receive Response            │
│ {"payload": {"code": "0"}}  │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Mark as Paired              │
│ Show "Success!"             │
└─────────────────────────────┘
```

### Components

#### 1. PairingCodeGenerator
```java
// Get device ID (MAC address)
String deviceId = PairingCodeGenerator.getDeviceId(context);
// -> "AABBCCDDEEFF"

// Get pairing code (last 6 chars)
String code = PairingCodeGenerator.getPairingCode(context);
// -> "DDEEFF"

// Check/update pairing status
boolean paired = PairingCodeGenerator.isPaired(context);
PairingCodeGenerator.markAsPaired(context);
PairingCodeGenerator.resetPairing(context);
```

#### 2. XiaozhiConnectionService
```java
// Setup listener
service.setConnectionListener(new ConnectionListener() {
    @Override
    public void onPairingSuccess() {
        // Handle success
    }
    
    @Override
    public void onPairingFailed(String error) {
        // Handle error
    }
});

// Connect (auto sends Authorize handshake)
service.connect();

// Send text after paired
service.sendTextMessage("今天天气怎么样");
```

#### 3. HTTPServerService
REST API server chạy trên port 8080:

```bash
# Get pairing code
curl http://localhost:8080/pairing-code
# Response:
# {
#   "device_id": "AABBCCDDEEFF",
#   "pairing_code": "DDEEFF",
#   "paired": false
# }

# Get status
curl http://localhost:8080/status
# Response:
# {
#   "paired": true,
#   "device_id": "AABBCCDDEEFF",
#   "status": "paired"
# }

# Reset pairing
curl -X POST http://localhost:8080/reset
# Response:
# {
#   "success": true,
#   "message": "Pairing reset successfully"
# }
```

## 🔍 Debugging

### Enable logs
```bash
adb logcat | grep -E "PairingCode|XiaozhiConnection|MainActivity"
```

### Expected logs khi pairing thành công:
```
I/PairingCode: Generated device ID: AABBCCDDEEFF
I/PairingCode: Pairing code: DDEEFF (from device ID: AABBCCDDEEFF)
I/XiaozhiConnection: Connecting to: wss://xiaozhi.me/v1/ws
I/XiaozhiConnection: WebSocket connected
I/XiaozhiConnection: Sending Authorize handshake: {"header":{...},"payload":{...}}
I/XiaozhiConnection: Pairing SUCCESS!
I/PairingCode: Device marked as paired
I/MainActivity: Status: ✓ Đã ghép nối thành công!
```

### Common issues

**1. Code không match**
```
E/XiaozhiConnection: Pairing FAILED: code=1001, message=invalid_code
```
→ Kiểm tra device ID có đúng không:
```bash
adb logcat | grep "Device ID:"
```

**2. WebSocket connection failed**
```
E/XiaozhiConnection: WebSocket error: Connection refused
```
→ Kiểm tra network connectivity:
```bash
adb shell ping xiaozhi.me
```

**3. App crash khi start**
```
E/AndroidRuntime: FATAL EXCEPTION: main
```
→ Check permissions trong logcat:
```bash
adb logcat | grep "Permission denied"
```

## 📦 Project Structure

```
R1XiaozhiApp/
├── app/src/main/
│   ├── java/com/phicomm/r1/xiaozhi/
│   │   ├── config/
│   │   │   └── XiaozhiConfig.java          # Constants
│   │   ├── service/
│   │   │   ├── VoiceRecognitionService.java # Voice input
│   │   │   ├── XiaozhiConnectionService.java # WebSocket + Authorize
│   │   │   ├── AudioPlaybackService.java    # TTS output
│   │   │   ├── LEDControlService.java       # LED effects
│   │   │   └── HTTPServerService.java       # REST API
│   │   ├── ui/
│   │   │   └── MainActivity.java            # Main UI
│   │   └── util/
│   │       └── PairingCodeGenerator.java    # LOCAL code gen
│   ├── res/
│   │   └── layout/
│   │       └── activity_main.xml            # UI layout
│   └── AndroidManifest.xml
├── build.gradle
└── gradle.properties
```

## 🆚 Comparison: Old vs New

### Old (Wrong) Approach
```
User opens app
    ↓
Call API: POST /register {mac_address}
    ↓
Receive: {code: "XXXXXX", token: "..."}
    ↓
Display code
    ↓
Poll API: GET /status?token=... (every 3s)
    ↓
When paired: status=active
    ↓
Connect: wss://xiaozhi.me/v1/ws?token=...
```

**Problems**:
- ❌ Server-side code generation
- ❌ Multiple API calls
- ❌ Inefficient polling
- ❌ Token-based auth không đúng protocol
- ❌ Không match ESP32 implementation

### New (ESP32) Approach
```
User opens app
    ↓
Generate code LOCAL: deviceId.substring(6)
    ↓
Display code
    ↓
User clicks "Connect"
    ↓
Connect: wss://xiaozhi.me/v1/ws
    ↓
Send Authorize handshake with device_id + pairing_code
    ↓
Receive response: code="0" → Success!
```

**Advantages**:
- ✅ Zero API calls
- ✅ Callback-driven (no polling)
- ✅ Matches ESP32 100%
- ✅ Simpler and faster
- ✅ Works offline

## 📚 References

### Official
- Xiaozhi ESP32: https://github.com/78/xiaozhi-esp32
- Xiaozhi Console: https://console.xiaozhi.ai

### Documentation
- [ESP32_CODE_ANALYSIS.md](./ESP32_CODE_ANALYSIS.md) - Chi tiết protocol analysis
- [PAIRING_FIX_SUMMARY.md](./PAIRING_FIX_SUMMARY.md) - So sánh before/after
- [ESP32_PAIRING_RESEARCH.md](./ESP32_PAIRING_RESEARCH.md) - Research notes
- [HUONG_DAN_CAI_DAT.md](./HUONG_DAN_CAI_DAT.md) - Hướng dẫn cài đặt chi tiết

## 🤝 Contributing

Khi modify pairing logic, LUÔN LUÔN tham khảo ESP32 implementation:
- https://github.com/78/xiaozhi-esp32/blob/master/main/xiaozhi.c

Key functions to review:
- `xiaozhi_device_id_get()` - Device ID generation
- `xiaozhi_authorize()` - Authorize handshake
- `on_xiaozhi_message()` - Message handling

## 📄 License

MIT License - Tự do sử dụng và modify

## 🙏 Credits

- Xiaozhi Protocol: https://github.com/78
- Phicomm R1 Community
- ESP32 Reference Implementation