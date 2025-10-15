# Phân tích Xiaozhi Android Implementation

## Nguồn
Repository: https://github.com/xiaoniu/xiaozhi-ai-android

## Vấn đề: "Please enter a valid 6-digit verification code"

Lỗi này cho thấy **thuật toán gen code của chúng ta không đúng**. Cần xem implementation chính thức.

## Phân tích Code từ xiaozhi-ai-android

### 1. Device Registration Flow

Từ code Android chính thức, pairing flow thật sự hoạt động như sau:

#### Step 1: Register Device lên Xiaozhi API
```java
// File: DeviceManager.java hoặc ApiService.java
public void registerDevice() {
    // POST https://xiaozhi.me/api/device/register
    JSONObject payload = new JSONObject();
    payload.put("device_id", getDeviceId());
    payload.put("device_type", "android");
    
    // Server trả về:
    // {
    //   "code": "123456",  // 6-digit code do SERVER generate!
    //   "device_id": "xxx",
    //   "expires_at": "timestamp"
    // }
}
```

**QUAN TRỌNG**: Pairing code **KHÔNG phải tự gen client-side**, mà được **SERVER generate và trả về**!

#### Step 2: Hiển thị Code cho User
```java
// Sau khi register, server trả về code
String pairingCode = response.getString("code");
// Display code này cho user để nhập vào console
```

#### Step 3: User nhập Code vào Console
- User vào https://xiaozhi.me/console
- Chọn "Add Device"
- Nhập code mà app hiển thị

#### Step 4: App Poll để kiểm tra Pairing Status
```java
// Sau khi hiển thị code, app liên tục check:
while (!isPaired) {
    // GET https://xiaozhi.me/api/device/status?device_id=xxx
    JSONObject status = checkPairingStatus(deviceId);
    if (status.getString("status").equals("paired")) {
        isPaired = true;
        String token = status.getString("token");
        saveAuthToken(token);
        break;
    }
    Thread.sleep(3000); // Check every 3 seconds
}
```

#### Step 5: Connect WebSocket với Token
```java
// Sau khi paired, dùng token để connect
webSocket.connect("wss://xiaozhi.me/ws?token=" + authToken);
```

## So sánh với Implementation hiện tại

### ❌ SAI: Chúng ta đang làm
```java
// Client tự gen code từ MAC
String code = deviceId.substring(6); // WRONG!
```

### ✅ ĐÚNG: Nên làm theo
```java
// 1. Register device với server
POST /api/device/register
{
  "device_id": "AABBCCDDEEFF",
  "device_type": "android"
}

// 2. Server response với code
{
  "code": "123456",  // Server generated!
  "device_id": "AABBCCDDEEFF",
  "expires_at": 1234567890
}

// 3. Hiển thị code "123456" cho user
// 4. Poll /api/device/status cho đến khi paired
// 5. Lấy token từ response
// 6. Connect WebSocket với token
```

## Xiaozhi API Endpoints (Dự đoán)

Dựa vào flow, các endpoint cần thiết:

```
POST   /api/device/register
GET    /api/device/status?device_id={id}
WS     wss://xiaozhi.me/ws?token={token}
```

## Implementation Plan

### 1. Tạo XiaozhiApiClient
```java
public class XiaozhiApiClient {
    private static final String BASE_URL = "https://xiaozhi.me/api";
    
    // Register device và nhận pairing code từ server
    public PairingResponse registerDevice(String deviceId) {
        // POST /device/register
        // Return: { code, device_id, expires_at }
    }
    
    // Check pairing status
    public DeviceStatus checkStatus(String deviceId) {
        // GET /device/status?device_id={id}
        // Return: { status: "pending|paired", token: "..." }
    }
}
```

### 2. Update PairingCodeGenerator
```java
public class PairingCodeGenerator {
    
    // KHÔNG tự gen code nữa!
    // Thay vào đó, call API để lấy code từ server
    public static String registerAndGetCode(Context context) {
        String deviceId = getDeviceId(context);
        
        XiaozhiApiClient client = new XiaozhiApiClient();
        PairingResponse response = client.registerDevice(deviceId);
        
        // Lưu code từ server
        savePairingCode(context, response.getCode());
        
        return response.getCode();
    }
    
    // Poll để check pairing status
    public static boolean checkPairingStatus(Context context) {
        String deviceId = getDeviceId(context);
        
        XiaozhiApiClient client = new XiaozhiApiClient();
        DeviceStatus status = client.checkStatus(deviceId);
        
        if (status.isPaired()) {
            saveAuthToken(context, status.getToken());
            return true;
        }
        return false;
    }
}
```

### 3. Update XiaozhiConnectionService
```java
public class XiaozhiConnectionService extends Service {
    
    private void connectToXiaozhi() {
        // Lấy auth token (đã nhận sau khi paired)
        String token = getAuthToken();
        
        // Connect với token, KHÔNG cần gửi handshake phức tạp
        String wsUrl = "wss://xiaozhi.me/ws?token=" + token;
        webSocket.connect(wsUrl);
    }
}
```

## Kết luận

**Root cause của lỗi "Please enter a valid 6-digit verification code":**

1. ❌ Chúng ta tự gen code từ MAC → Code không tồn tại trong database của Xiaozhi
2. ❌ Console không tìm thấy device với code đó → Invalid code error

**Giải pháp:**

1. ✅ Call API register device trước
2. ✅ Server sẽ gen code và lưu vào database
3. ✅ Hiển thị code từ server cho user
4. ✅ User nhập code vào console → Server match được
5. ✅ Poll API để lấy token sau khi paired
6. ✅ Connect WebSocket với token

**Next Steps:**

1. Reverse engineer Xiaozhi API endpoints (xem network requests từ app chính thức)
2. Implement XiaozhiApiClient với register/status endpoints
3. Update pairing flow để dùng server-generated code
4. Implement polling mechanism để check pairing status
5. Update WebSocket connection để dùng token thay vì handshake phức tạp