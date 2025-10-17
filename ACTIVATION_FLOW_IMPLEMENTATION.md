# ✅ Activation Flow Implementation - Hoàn Thành

## 🎯 Vấn Đề Đã Giải Quyết

### ❌ Vấn Đề Cũ
Android code **KHÔNG** nhận được mã 6 số từ server vì:
1. Tự tạo challenge thay vì nhận từ server
2. Thiếu bước OTA Config để lấy activation data
3. Flow không đúng với py-xiaozhi

### ✅ Giải Pháp Mới
Đã implement đúng flow theo py-xiaozhi:
1. **GET OTA Config** → Nhận challenge + code từ server
2. **Hiển thị code** cho user
3. **POST Activation** với challenge từ server
4. **Polling** đến khi user nhập code vào website

---

## 📁 Files Đã Tạo/Sửa

### 1. ✅ OTAConfigManager.java (MỚI)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/OTAConfigManager.java`

**Chức năng**:
- Fetch OTA configuration từ server
- Parse activation data (challenge + code)
- Parse WebSocket config
- Based on `py-xiaozhi/src/core/ota.py`

**Key Methods**:
```java
public void fetchOTAConfig(OTACallback callback)
private OTAResponse performOTARequest()
private JSONObject buildPayload(String deviceId)
private OTAResponse parseOTAResponse(String jsonString)
```

**Response Structure**:
```java
OTAResponse {
    WebSocketConfig websocket;
    ActivationData activation {
        String challenge;  // Từ server!
        String code;       // Mã 6 số từ server!
        String url;
        int timeout;
    }
}
```

### 2. ✅ DeviceActivator.java (ĐÃ SỬA)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/DeviceActivator.java`

**Thay đổi chính**:

#### Before (SAI):
```java
// Tự tạo challenge - SAI!
String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
String hmac = fingerprint.generateHmac(challenge);
```

#### After (ĐÚNG):
```java
// STEP 1: Fetch OTA config
otaManager.fetchOTAConfig(callback);

// STEP 2: Parse activation data từ server
serverChallenge = response.activation.challenge;  // Từ server!
verificationCode = response.activation.code;      // Từ server!

// STEP 3: Hiển thị code cho user
notifyVerificationCode(verificationCode);

// STEP 4: Poll với server challenge
String hmac = fingerprint.generateHmac(serverChallenge);
```

**New Fields**:
```java
private String serverChallenge;    // Challenge từ server
private String verificationCode;   // Code 6 số từ server
private final OTAConfigManager otaManager;
```

**New Methods**:
```java
private void handleOTAResponse(OTAResponse response)
private void performActivationPolling()
```

### 3. ✅ MainActivity.java (ĐÃ CÓ SẴN)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java`

**UI Components** (đã có sẵn):
- `activationCodeText` - Hiển thị mã kích hoạt
- `activationProgressText` - Hiển thị tiến trình
- `cancelActivationButton` - Hủy kích hoạt

**Methods**:
- `showActivationCode(String code)` - Hiển thị code từ server
- `updateActivationProgress(int, int)` - Update progress
- `copyActivationCode()` - Copy code to clipboard

---

## 🔄 Flow Hoàn Chỉnh

```
┌──────────────────────────────────────────────────────────┐
│ 1. User Click "Kết Nối"                                  │
│    → MainActivity.connectToXiaozhi()                     │
│    → XiaozhiConnectionService.connect()                  │
│    → DeviceActivator.startActivation()                   │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 2. Fetch OTA Config                                       │
│    → OTAConfigManager.fetchOTAConfig()                   │
│    → GET https://api.tenclass.net/xiaozhi/ota/version   │
│    → Headers: Device-Id, Client-Id, Activation-Version  │
│    → Body: {application: {...}, board: {...}}           │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 3. Server Response                                        │
│    {                                                      │
│      "websocket": {...},                                 │
│      "activation": {                                     │
│        "challenge": "abc123xyz...",  ← TỪ SERVER!       │
│        "code": "123456",             ← TỪ SERVER!       │
│        "url": "https://xiaozhi.me/activate",            │
│        "timeout": 300                                    │
│      }                                                    │
│    }                                                      │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 4. Parse & Display Code                                  │
│    → DeviceActivator.handleOTAResponse()                 │
│    → serverChallenge = response.activation.challenge     │
│    → verificationCode = response.activation.code         │
│    → notifyVerificationCode(verificationCode)            │
│    → MainActivity.showActivationCode("123456")           │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 5. User Nhập Code Vào Website                            │
│    → Truy cập: https://xiaozhi.me/activate              │
│    → Nhập mã: 123456                                     │
│    → Server validate và đánh dấu device activated        │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 6. Android Polling Activation API                        │
│    → DeviceActivator.performActivationPolling()          │
│    → LOOP: POST /activate với server challenge          │
│    → Body: {                                             │
│        "sn": "SN-...",                                   │
│        "mac": "aabbccddee",                              │
│        "challenge": "abc123xyz",  ← TỪ SERVER!          │
│        "signature": hmac(challenge),                     │
│        "client_id": "..."                                │
│      }                                                    │
└──────────────────────────────────────────────────────────┘
                        ↓
            ┌───────────┴───────────┐
            │                       │
            ▼                       ▼
  ┌──────────────────┐    ┌──────────────────┐
  │ HTTP 202         │    │ HTTP 200         │
  │ Đang đợi...      │    │ Success!         │
  │ → Sleep 5s       │    │ → Get token      │
  │ → Retry          │    │ → Save token     │
  └──────────────────┘    │ → Mark activated │
            ↑             │ → Connect WS     │
            │             └──────────────────┘
            │                       │
            └───────────────────────┘
                  (Max 60 retries)
```

---

## 📊 So Sánh Before/After

| Aspect | Before (SAI) | After (ĐÚNG) |
|--------|--------------|--------------|
| **OTA Config** | ❌ Không có | ✅ OTAConfigManager |
| **Challenge** | ❌ Tự tạo `xiaozhi-activation-{timestamp}` | ✅ Nhận từ server |
| **Verification Code** | ❌ Không hiển thị | ✅ Hiển thị từ server response |
| **Flow** | ❌ POST ngay without challenge | ✅ GET OTA → Parse → POST với server challenge |
| **HMAC** | ❌ HMAC của self-generated challenge | ✅ HMAC của server challenge |
| **Token Storage** | ❌ Không lưu | ✅ Lưu access_token |

---

## 🔍 Code Comparison

### Challenge Generation

#### Before ❌:
```java
// DeviceActivator.java - Line 167
String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
String hmac = fingerprint.generateHmac(challenge);
// POST /activate với challenge tự tạo → Server reject!
```

#### After ✅:
```java
// Step 1: Get challenge from server
otaManager.fetchOTAConfig(new OTACallback() {
    @Override
    public void onSuccess(OTAResponse response) {
        if (response.activation != null) {
            // Challenge từ server!
            serverChallenge = response.activation.challenge;
            verificationCode = response.activation.code;
            
            // Hiển thị code cho user
            notifyVerificationCode(verificationCode);
            
            // Polling với server challenge
            performActivationPolling();
        }
    }
});

// Step 2: Use server challenge
String hmac = fingerprint.generateHmac(serverChallenge);
// POST /activate với server challenge → Server accept!
```

---

## 📝 API Endpoints

### 1. OTA Config API
```
GET https://api.tenclass.net/xiaozhi/ota/version?device_id={mac}&client_id={id}

Headers:
- Content-Type: application/json
- Device-Id: {mac_address}
- Client-Id: {uuid}
- Activation-Version: 1.0.0
- User-Agent: android/xiaozhi-android-1.0.0
- Accept-Language: zh-CN

Body:
{
  "application": {
    "version": "1.0.0",
    "elf_sha256": "{hmac_key}"
  },
  "board": {
    "type": "android",
    "name": "xiaozhi-android",
    "ip": "192.168.1.100",
    "mac": "{mac_address}"
  }
}

Response (Device NOT activated):
{
  "websocket": {
    "url": "wss://xiaozhi.me/v1/ws",
    "token": null,
    "protocol": "v1"
  },
  "activation": {
    "challenge": "server-generated-challenge-abc123",
    "code": "123456",
    "url": "https://xiaozhi.me/activate",
    "timeout": 300
  }
}

Response (Device activated):
{
  "websocket": {
    "url": "wss://xiaozhi.me/v1/ws",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "protocol": "v1"
  }
  // NO "activation" field
}
```

### 2. Activation API
```
POST https://api.tenclass.net/xiaozhi/ota/activate

Headers:
- Content-Type: application/json
- Device-Id: {mac_address}
- Client-Id: {uuid}
- Activation-Version: 2

Body:
{
  "Payload": {
    "algorithm": "hmac-sha256",
    "serial_number": "SN-XXXX-aabbccddee",
    "challenge": "{challenge_from_server}",  ← Từ OTA response!
    "hmac": "{hmac_sha256(challenge)}"
  }
}

Response 202 (Waiting):
{
  "code": "123456",
  "message": "Please enter verification code"
}

Response 200 (Success):
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 86400
}
```

---

## 🧪 Testing Guide

### 1. Kiểm Tra OTA Config
```bash
# Check OTA response
adb logcat | grep "OTAConfigManager"

# Should see:
# I/OTAConfigManager: Fetching OTA config from: https://...
# I/OTAConfigManager: WebSocket config received: wss://...
# I/OTAConfigManager: Activation data received - Device needs activation
# I/OTAConfigManager: Verification code: 123456
```

### 2. Kiểm Tra Activation Flow
```bash
# Check activation process
adb logcat | grep "DeviceActivator"

# Should see:
# I/DeviceActivator: Starting activation - Fetching OTA config...
# I/DeviceActivator: Activation required - Challenge received from server
# I/DeviceActivator: Verification code: 123456
# I/DeviceActivator: Starting activation polling with server challenge
# I/DeviceActivator: Waiting for user to enter verification code on website...
# ...
# I/DeviceActivator: Activation successful!
```

### 3. Kiểm Tra UI
```bash
# Should see on screen:
# - Ma kich hoat: 123456
# - Truy cap: https://xiaozhi.me/activate
# - Nhap ma: 123456
# - Copy button visible
# - Progress: Dang kiem tra... (1/60)
```

---

## 🚀 Next Steps

### Để Test:
1. ✅ Code đã hoàn chỉnh
2. ✅ Flow đã đúng theo py-xiaozhi
3. ⏳ Cần server hỗ trợ OTA API endpoint
4. ⏳ Test với server thật

### Để Deploy:
1. Build APK
2. Install trên thiết bị
3. Test activation flow
4. Verify WebSocket connection sau khi activate

---

## 📚 References

### py-xiaozhi Source Files
- `src/core/system_initializer.py` (line 161-209) - OTA config flow
- `src/core/ota.py` (line 120-249) - OTA implementation
- `src/activation/device_activator.py` (line 102-341) - Activation logic
- `src/utils/device_fingerprint.py` - Device identity

### Android Files
- `OTAConfigManager.java` - OTA config fetching (NEW)
- `DeviceActivator.java` - Activation with OTA flow (UPDATED)
- `DeviceFingerprint.java` - Device identity (existing)
- `MainActivity.java` - UI display (existing)

---

## ✅ Checklist

- [x] Tạo OTAConfigManager.java
- [x] Sửa DeviceActivator.java - thêm OTA step
- [x] Sửa DeviceActivator.java - sử dụng server challenge
- [x] Verify MainActivity.java có UI sẵn
- [x] Tạo document ACTIVATION_FLOW_FIX.md
- [x] Tạo document ACTIVATION_FLOW_IMPLEMENTATION.md
- [ ] Test với server thật
- [ ] Push code lên GitHub
- [ ] Update README với activation instructions

---

## 🎉 Kết Luận

**Android code đã được sửa để match hoàn toàn với py-xiaozhi activation flow!**

Key improvements:
1. ✅ Thêm OTA Config step
2. ✅ Nhận challenge + code từ server
3. ✅ Hiển thị code cho user
4. ✅ Poll với server challenge (không tự tạo)
5. ✅ Lưu access_token khi thành công

**Ready to test with real server!** 🚀