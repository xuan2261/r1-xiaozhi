# WebSocket Connection Fix

## Vấn đề
Sau khi activation thành công, WebSocket connection bị lỗi và không thể kết nối.

## Phân tích

### Current Android Implementation
```java
// XiaozhiConfig.java - Line 12
public static final String WEBSOCKET_URL = "wss://xiaozhi.me/v1/ws";
```

### py-xiaozhi WebSocket URL
Cần kiểm tra từ py-xiaozhi config để đảm bảo URL đúng.

**Khả năng cao**: URL WebSocket phải match với backend server đang dùng cho activation API.

## Nguyên nhân có thể

### 1. **URL không đúng**
- Android: `wss://xiaozhi.me/v1/ws`
- Backend có thể dùng URL khác

### 2. **Token authentication issue**
```java
// XiaozhiConnectionService.java - Line 199
headers.put("Authorization", "Bearer " + accessToken);
```

Token có thể:
- Đã expire
- Format không đúng
- Server không nhận diện được

### 3. **Hello message format**
```java
// XiaozhiConnectionService.java - Line 279-309
{
  "header": {
    "name": "hello",
    "namespace": "ai.xiaoai.common",
    "message_id": "uuid"
  },
  "payload": {
    "device_id": "MAC_ADDRESS",
    "serial_number": "SN-HASH-MAC",
    ...
  }
}
```

Server có thể expect format khác hoặc thêm fields.

### 4. **MAC address format trong hello message**
Sau khi fix MAC format để có dấu hai chấm, cần verify rằng:
- `device_id` trong hello message = MAC với dấu hai chấm
- Server expect format này

## Cần kiểm tra từ py-xiaozhi

### File cần xem
1. **`src/utils/config_manager.py`**
   - WebSocket URL configuration
   - Default server endpoints

2. **`src/core/websocket_client.py`**
   - WebSocket connection logic
   - Header format
   - Authentication method

3. **`src/core/application.py`**
   - Hello message format
   - Connection flow sau activation

## Debugging Steps

### 1. Log WebSocket connection
Thêm logging chi tiết:
```java
Log.i(TAG, "=== WEBSOCKET CONNECTION ===");
Log.i(TAG, "URL: " + XiaozhiConfig.WEBSOCKET_URL);
Log.i(TAG, "Token: " + accessToken.substring(0, 20) + "...");
Log.i(TAG, "Headers: " + headers.toString());
Log.i(TAG, "============================");
```

### 2. Log WebSocket errors
```java
@Override
public void onError(Exception ex) {
    Log.e(TAG, "=== WEBSOCKET ERROR ===");
    Log.e(TAG, "Error class: " + ex.getClass().getName());
    Log.e(TAG, "Error message: " + ex.getMessage());
    ex.printStackTrace();
    Log.e(TAG, "======================");
}
```

### 3. Log server close reason
```java
@Override
public void onClose(int code, String reason, boolean remote) {
    Log.w(TAG, "=== WEBSOCKET CLOSED ===");
    Log.w(TAG, "Code: " + code);
    Log.w(TAG, "Reason: " + reason);
    Log.w(TAG, "Remote: " + remote);
    Log.w(TAG, "========================");
}
```

## Potential Fixes

### Fix 1: Update WebSocket URL
Nếu py-xiaozhi dùng URL khác:
```java
// Thay đổi từ
public static final String WEBSOCKET_URL = "wss://xiaozhi.me/v1/ws";

// Sang (ví dụ)
public static final String WEBSOCKET_URL = "wss://api.tenclass.net/xiaozhi/ws";
```

### Fix 2: Add token refresh
Nếu token expire:
```java
// Trong DeviceActivator.java
public void refreshToken() {
    // Call API để refresh token trước khi connect WebSocket
}
```

### Fix 3: Update hello message format
Nếu server expect format khác:
```java
// Match exactly với py-xiaozhi hello message
payload.put("device_id", deviceId);  // MAC with colons
payload.put("serial_number", serialNumber);
payload.put("client_id", XiaozhiConfig.CLIENT_ID);
// Thêm fields khác nếu cần
```

### Fix 4: Add connection timeout & better error handling
```java
webSocketClient.setConnectionLostTimeout(30); // 30 seconds timeout

// Better retry logic
private void scheduleReconnect(final int errorCode) {
    if (retryCount >= MAX_RETRIES) {
        // Force re-activation nếu token có thể invalid
        if (errorCode == ErrorCodes.AUTHENTICATION_ERROR) {
            deviceActivator.resetActivation();
        }
        return;
    }
    // ... existing retry logic
}
```

## Next Steps

1. ✅ Thêm chi tiết logging để debug
2. ⏳ So sánh WebSocket URL với py-xiaozhi
3. ⏳ Verify token format và expiration
4. ⏳ Check hello message format
5. ⏳ Test với server thật và capture logs

## Reference Files
- [`XiaozhiConnectionService.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java:194) - WebSocket connection
- [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:12) - WebSocket URL
- [`DeviceActivator.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/DeviceActivator.java:1) - Token management