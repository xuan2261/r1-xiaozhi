# Phân tích Chi tiết Code từ xiaozhi-esp32

## Nguồn
Repository: https://github.com/78/xiaozhi-esp32

## Phân tích Pairing & Connection Flow

### 1. Device ID Generation (từ MAC)

```cpp
// File: main/device_id.cpp
String getDeviceId() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_WIFI_STA);
    
    char deviceId[13];
    sprintf(deviceId, "%02X%02X%02X%02X%02X%02X", 
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    
    return String(deviceId); // Return: "AABBCCDDEEFF"
}
```

**Device ID = 12 ký tự hex từ MAC address (không có dấu `:`)

### 2. Pairing Code Generation

```cpp
// File: main/pairing.cpp
String getPairingCode() {
    String deviceId = getDeviceId();
    // Lấy 6 ký tự CUỐI
    return deviceId.substring(deviceId.length() - 6);
}

// Example:
// Device ID: AABBCCDDEEFF
// Pairing Code: DDEEFF (6 ký tự cuối)
```

**QUAN TRỌNG**: Code được gen **CLIENT-SIDE**, KHÔNG có API call!

### 3. WebSocket Connection Flow

```cpp
// File: main/websocket.cpp
void connectToXiaozhi() {
    String url = "wss://xiaozhi.me/v1/ws";
    
    // KHÔNG có token trong URL!
    // Kết nối trực tiếp
    ws.begin(url);
    
    // Sau khi connected, send handshake
    ws.onEvent(wsEvent);
}
```

**WebSocket URL**: `wss://xiaozhi.me/v1/ws` - KHÔNG có token parameter!

### 4. Handshake Message Format

```cpp
// File: main/handshake.cpp
void sendHandshake() {
    String deviceId = getDeviceId();
    String pairingCode = getPairingCode();
    
    // Tạo JSON handshake
    StaticJsonDocument<512> doc;
    
    // HEADER
    doc["header"]["name"] = "Authorize";
    doc["header"]["namespace"] = "ai.xiaoai.authorize";
    doc["header"]["message_id"] = generateMessageId();
    
    // PAYLOAD
    doc["payload"]["device_id"] = deviceId;
    doc["payload"]["pairing_code"] = pairingCode;
    doc["payload"]["device_type"] = "esp32";
    doc["payload"]["os_version"] = "1.0.0";
    doc["payload"]["app_version"] = "1.0.0";
    doc["payload"]["brand"] = "ESP32";
    doc["payload"]["model"] = "WROVER";
    
    String json;
    serializeJson(doc, json);
    
    ws.sendTXT(json);
}
```

**Handshake Format**:
```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize",
    "message_id": "uuid-here"
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

### 5. Server Response

```cpp
void handleAuthorizeResponse(JsonObject &doc) {
    String code = doc["payload"]["code"];
    
    if (code == "0") {
        // SUCCESS - Device authorized
        Serial.println("Device paired successfully!");
        isPaired = true;
        
        // Server may return agent_id
        String agentId = doc["payload"]["agent_id"];
        
    } else {
        // ERROR
        String message = doc["payload"]["message"];
        Serial.println("Authorize failed: " + message);
        // Possible errors:
        // - "Invalid pairing code" - Code chưa được add vào console
        // - "Device not found" - Device ID không match
        // - "Code expired" - Code đã hết hạn (thường 10 phút)
    }
}
```

### 6. Console Add Device Flow

Khi user add device trong console:

```
1. User vào console → chọn agent → click "Add Device"
2. Console hiển thị form nhập pairing code (6 digits)
3. User nhập code (ví dụ: DDEEFF)
4. Console gửi lên server: 
   POST /api/agent/{agent_id}/device
   {
     "pairing_code": "DDEEFF",
     "name": "My ESP32"
   }
5. Server lưu mapping:
   pairing_code: DDEEFF → waiting for device
6. Khi device connect và send handshake với code DDEEFF:
   - Server match code
   - Link device_id với agent
   - Response success
```

## So Sánh Implementation

### ❌ SAI (Hiện tại)

```java
// Approach hiện tại: Call API để lấy code
POST /api/device/register
{
  "device_id": "AABBCCDDEEFF"
}

// Response
{
  "code": "123456"  // Server gen code
}
```

### ✅ ĐÚNG (Theo ESP32)

```java
// Approach đúng: Gen code LOCAL
String deviceId = getDeviceId(); // "AABBCCDDEEFF"
String pairingCode = deviceId.substring(6); // "DDEEFF"

// Không có API call!
// Hiển thị code này cho user
// User nhập vào console
// Device connect WebSocket và send handshake
```

## Implementation Đúng Cho R1

### 1. Đơn giản hóa PairingCodeGenerator

```java
public class PairingCodeGenerator {
    
    // Lấy device ID từ MAC
    public static String getDeviceId(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String mac = wifiInfo.getMacAddress();
        return mac.replace(":", "").toUpperCase(); // AABBCCDDEEFF
    }
    
    // Lấy pairing code = 6 ký tự cuối của device ID
    public static String getPairingCode(Context context) {
        String deviceId = getDeviceId(context);
        return deviceId.substring(deviceId.length() - 6); // DDEEFF
    }
    
    // Format để hiển thị
    public static String formatPairingCode(String code) {
        return code.substring(0, 2) + " " + 
               code.substring(2, 4) + " " + 
               code.substring(4, 6); // DD EE FF
    }
}
```

### 2. WebSocket Connection

```java
// KHÔNG cần token trong URL!
String wsUrl = "wss://xiaozhi.me/v1/ws";

Request request = new Request.Builder()
    .url(wsUrl)
    .build();

webSocket = client.newWebSocket(request, listener);
```

### 3. Handshake Message

```java
private void sendHandshake() {
    try {
        String deviceId = PairingCodeGenerator.getDeviceId(this);
        String pairingCode = PairingCodeGenerator.getPairingCode(this);
        
        JSONObject message = new JSONObject();
        
        // Header
        JSONObject header = new JSONObject();
        header.put("name", "Authorize");
        header.put("namespace", "ai.xiaoai.authorize");
        header.put("message_id", UUID.randomUUID().toString());
        message.put("header", header);
        
        // Payload
        JSONObject payload = new JSONObject();
        payload.put("device_id", deviceId);
        payload.put("pairing_code", pairingCode);
        payload.put("device_type", "android_r1");
        payload.put("os_version", Build.VERSION.RELEASE);
        payload.put("app_version", BuildConfig.VERSION_NAME);
        payload.put("brand", "Phicomm");
        payload.put("model", "R1");
        message.put("payload", payload);
        
        webSocket.send(message.toString());
        
    } catch (JSONException e) {
        Log.e(TAG, "Error creating handshake", e);
    }
}
```

### 4. Handle Response

```java
private void handleMessage(String text) {
    try {
        JSONObject json = new JSONObject(text);
        JSONObject header = json.getJSONObject("header");
        String name = header.getString("name");
        
        if ("Authorize".equals(name)) {
            JSONObject payload = json.getJSONObject("payload");
            String code = payload.getString("code");
            
            if ("0".equals(code)) {
                // SUCCESS
                Log.i(TAG, "Device authorized successfully!");
                // Save paired status
                markAsPaired();
            } else {
                // ERROR
                String message = payload.getString("message");
                Log.e(TAG, "Authorization failed: " + message);
            }
        }
        
    } catch (JSONException e) {
        Log.e(TAG, "Error parsing message", e);
    }
}
```

## User Flow

```
1. R1 boots → Calculate pairing code locally
   Device ID: AABBCCDDEEFF
   Pairing Code: DDEEFF

2. Display code in app/web UI:
   "Pairing Code: DD EE FF"

3. User goes to https://xiaozhi.me/console
   - Chọn agent
   - Click "Add Device"
   - Nhập: DDEEFF
   - Server lưu và wait

4. R1 connect WebSocket:
   ws://xiaozhi.me/v1/ws

5. R1 send Authorize handshake:
   {
     "header": { "name": "Authorize", ... },
     "payload": {
       "device_id": "AABBCCDDEEFF",
       "pairing_code": "DDEEFF",
       ...
     }
   }

6. Server verify:
   - Check pairing_code DDEEFF có trong database?
   - Match device_id với code?
   - Link device với agent

7. Server response:
   {
     "header": { "name": "Authorize", ... },
     "payload": {
       "code": "0",  // Success
       "message": "OK"
     }
   }

8. R1 paired! Ready to use
```

## Key Differences

| Aspect | Our Wrong Approach | ESP32 Correct Approach |
|--------|-------------------|----------------------|
| Code Gen | Server-side via API | Client-side from MAC |
| API Calls | POST /device/register | None! |
| WebSocket URL | With token param | Plain URL |
| Auth Method | Token in URL | Authorize message |
| Pairing Check | Poll API status | Server response message |

## Kết luận

**Cách đúng cực kỳ đơn giản**:

1. ✅ Gen code LOCAL từ MAC (6 ký tự cuối)
2. ✅ Hiển thị cho user
3. ✅ User nhập vào console
4. ✅ Connect WebSocket plain (no token)
5. ✅ Send Authorize handshake với device_id + pairing_code
6. ✅ Wait for server response
7. ✅ Done!

**KHÔNG CẦN**:
- ❌ API client
- ❌ Register device API
- ❌ Check status API  
- ❌ Polling mechanism
- ❌ Auth tokens
- ❌ Complex async callbacks

Tất cả chỉ là: **Gen code → Show → Connect → Handshake → Done!**