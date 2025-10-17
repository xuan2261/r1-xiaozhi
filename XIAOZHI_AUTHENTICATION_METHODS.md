# Xiaozhi Authentication Methods - So Sánh 3 Phương Pháp

## 📋 Tổng Quan

Server Xiaozhi hỗ trợ **3 phương pháp authentication** khác nhau tùy theo client type và setup.

---

## 🔐 Method 1: ESP32 Authorize Handshake

### Nguồn
- Repository: https://github.com/78/xiaozhi-esp32
- File: `main/xiaozhi.c`

### Flow
```
1. Connect WebSocket: wss://xiaozhi.me/v1/ws
2. Send Authorize handshake
3. Wait for response
4. Ready to use
```

### Message Format
```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize",
    "message_id": "uuid"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "esp32",
    "os_version": "1.0.0",
    "app_version": "1.0.0",
    "brand": "ESP32",
    "model": "WROVER"
  }
}
```

### Pairing Flow
```
1. User goes to xiaozhi.me/console
2. Add device, enter pairing code: DDEEFF
3. Server saves mapping: code → waiting
4. ESP32 connects and sends handshake
5. Server matches code → Success
```

### ✅ Ưu điểm
- Đơn giản, không cần API
- Gen code local từ MAC
- Phù hợp với embedded devices

### ❌ Nhược điểm
- User phải nhập code thủ công
- Code có thể expire
- Không có session management

---

## 🔐 Method 2: py-xiaozhi Device Activation

### Nguồn
- Repository local: F:/PHICOMM_R1/xiaozhi/py-xiaozhi
- Files: `device_activator.py`, `device_fingerprint.py`, `websocket_protocol.py`

### Flow
```
1. Generate serial number from MAC
2. Register device (POST /activate with HMAC challenge)
3. Wait for user to enter verification code
4. Poll until activated
5. Get access token
6. Connect WebSocket with token in headers
7. Send hello message
8. Ready to use
```

### Device Registration
```python
# Generate identity
serial_number = "SN-HASH-MAC"
hmac_key = hash(hardware_info)

# Activate
POST https://api.tenclass.net/xiaozhi/ota/activate
Headers:
  - Activation-Version: 2
  - Device-Id: MAC_ADDRESS
  - Client-Id: UUID
  - Content-Type: application/json

Body:
{
  "Payload": {
    "algorithm": "hmac-sha256",
    "serial_number": "SN-...",
    "challenge": "server_challenge",
    "hmac": "calculated_hmac"
  }
}
```

### WebSocket Connection
```python
# Connect with headers
ws = websockets.connect(
    uri="wss://xiaozhi.me/v1/ws",
    additional_headers={
        "Authorization": "Bearer {access_token}",
        "Protocol-Version": "1",
        "Device-Id": "{mac_address}",
        "Client-Id": "{uuid}"
    }
)

# Send hello (NOT Authorize)
{
  "type": "hello",
  "version": 1,
  "features": {"mcp": true},
  "transport": "websocket",
  "audio_params": {
    "format": "opus",
    "sample_rate": 16000,
    "channels": 1,
    "frame_duration": 20
  }
}
```

### ✅ Ưu điểm
- Secure với HMAC
- Có session/token management
- Voice announcement của verification code
- Retry logic với polling

### ❌ Nhược điểm
- Phức tạp hơn
- Cần API endpoints
- Cần storage cho credentials

---

## 🔐 Method 3: Self-hosted Simple Auth

### Flow
```
1. Connect to self-hosted server
2. May not need authentication
3. Or use simple token
```

---

## 🎯 R1 Android Current Implementation

### Đang dùng: ESP32 Method (Modified)
```java
// XiaozhiConnectionService.java
POST ws://xiaozhi.me/v1/ws

// Send Authorize
{
  "header": {...},
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "android",      // ✓ Fixed
    "os_version": "5.1.1",          // ✓ Added
    "app_version": "1.0.0",         // ✓ Added
    "brand": "Phicomm",             // ✓ Added
    "model": "R1"                   // ✓ Added
  }
}
```

---

## 🔍 Vấn Đề Hiện Tại

### Symptom
"Mã 6 số không được" - pairing code không hoạt động

### Possible Causes

#### 1. Server Yêu Cầu py-xiaozhi Method
Server `xiaozhi.me` có thể đã upgrade và chỉ chấp nhận:
- Device activation flow
- Authorization header với bearer token
- **KHÔNG chấp nhận** ESP32 Authorize handshake nữa

#### 2. Thiếu Trường Bắt Buộc
Nếu vẫn support ESP32 method, có thể thiếu:
- `client_id` trong payload?
- Headers không đúng?

#### 3. Server Config
- Server có multiple authentication modes
- Cần check endpoint/version

---

## ✅ Giải Pháp Đề Xuất

### Option A: Giữ ESP32 Method (Quick Fix)

**If server vẫn support ESP32 method:**

1. ✅ Đã fix format (device_type, added fields)
2. ❓ Thử add `client_id` vào payload:

```java
payload.put("client_id", XiaozhiConfig.CLIENT_ID); // "1000013"
```

3. ❓ Check headers có đủ không:

```java
// WebSocket headers
headers.put("Protocol-Version", "1");
headers.put("Device-Id", deviceId);
headers.put("Client-Id", clientId);
```

### Option B: Implement py-xiaozhi Method (Recommended)

**Full implementation như py-xiaozhi:**

#### Step 1: Create DeviceActivator
```java
public class DeviceActivator {
    public ActivationResult activate(Context context) {
        // 1. Generate serial number
        // 2. POST /activate
        // 3. Display verification code
        // 4. Poll until activated
        // 5. Save access token
        // 6. Return token
    }
}
```

#### Step 2: Update XiaozhiConnectionService
```java
public void connect() {
    // 1. Check if activated
    if (!isActivated()) {
        // Trigger activation flow
        startActivation();
        return;
    }
    
    // 2. Get access token
    String token = getAccessToken();
    
    // 3. Connect with headers
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.put("Protocol-Version", "1");
    headers.put("Device-Id", deviceId);
    headers.put("Client-Id", clientId);
    
    webSocket.connect(url, headers);
    
    // 4. Send hello (NOT Authorize)
    sendHello();
}

private void sendHello() {
    JSONObject hello = new JSONObject();
    hello.put("type", "hello");
    hello.put("version", 1);
    hello.put("features", new JSONObject().put("mcp", true));
    hello.put("transport", "websocket");
    // ... audio_params
    
    webSocket.send(hello.toString());
}
```

### Option C: Support Both Methods

```java
public enum AuthMethod {
    ESP32_AUTHORIZE,
    PY_XIAOZHI_ACTIVATION
}

public void connect(AuthMethod method) {
    switch (method) {
        case ESP32_AUTHORIZE:
            connectWithAuthorize();
            break;
        case PY_XIAOZHI_ACTIVATION:
            connectWithActivation();
            break;
    }
}
```

---

## 🧪 Testing Strategy

### 1. Test Current ESP32 Method
```bash
# Check if Authorize still works
adb logcat | grep "Authorize"
```

Expected:
- ✅ Send Authorize handshake
- ✅ Receive response with code="0"
- ❌ Receive error "Invalid pairing code" or "Method not supported"

### 2. Test py-xiaozhi Method
Implement activation flow và test:
```bash
# Should display verification code
adb logcat | grep "verification"
```

### 3. Network Inspection
```bash
# Capture WebSocket traffic
adb shell "tcpdump -i any -s 0 -w /sdcard/websocket.pcap port 443"
```

---

## 📊 So Sánh Methods

| Feature | ESP32 | py-xiaozhi | Self-hosted |
|---------|-------|------------|-------------|
| Complexity | ⭐ Low | ⭐⭐⭐ High | ⭐⭐ Medium |
| Security | ⭐⭐ Basic | ⭐⭐⭐ HMAC | ⭐ Variable |
| Setup | Manual code entry | Voice announcement | Auto/None |
| Session | No | Yes (token) | Variable |
| Server Support | ✅ Older | ✅ Current | ✅ Custom |
| Best For | IoT devices | Desktop/Mobile | Development |

---

## 🎯 Recommendation

### Immediate (Quick Test):
1. Thêm `client_id` vào Authorize payload
2. Test xem có work không

### Short-term (If ESP32 không work):
1. Implement py-xiaozhi activation flow
2. Support token-based authentication
3. Add verification code display

### Long-term:
1. Support both methods
2. Auto-detect server requirements
3. Fallback giữa methods

---

## 📝 Code Examples

### Quick Fix: Add client_id
```java
// XiaozhiConnectionService.java
private void sendAuthorizeHandshake() {
    // ... existing code ...
    
    payload.put("device_id", deviceId);
    payload.put("pairing_code", pairingCode);
    payload.put("device_type", "android");
    payload.put("os_version", Build.VERSION.RELEASE);
    payload.put("app_version", "1.0.0");
    payload.put("brand", "Phicomm");
    payload.put("model", "R1");
    payload.put("client_id", XiaozhiConfig.CLIENT_ID); // ← ADD THIS
    
    // ... rest of code ...
}
```

### Full py-xiaozhi Implementation
See: `PY_XIAOZHI_ACTIVATION_IMPLEMENTATION.md` (to be created)

---

## 🔗 References

- ESP32 Code: https://github.com/78/xiaozhi-esp32
- py-xiaozhi: F:/PHICOMM_R1/xiaozhi/py-xiaozhi
- Current Android: [XiaozhiConnectionService.java](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java)

---

**Status**: 🔍 Investigating
**Next Action**: Test quick fix với `client_id`
**Created**: 2025-10-17