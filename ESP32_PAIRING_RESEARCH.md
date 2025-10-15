# Nghiên cứu Cơ chế Pairing của xiaozhi-esp32

## Nguồn
Repository: https://github.com/78/xiaozhi-esp32

## Phân tích lỗi "Enter the 6-digit code announced by the device"

### Vấn đề hiện tại
- Xiaozhi Console yêu cầu nhập **mã 6 chữ số được thiết bị thông báo** (announced)
- Điều này cho thấy pairing code phải được **thiết bị TTS đọc to** khi bắt đầu

### Cơ chế Pairing trong xiaozhi-esp32

#### 1. Device ID Generation
```cpp
// File: main/xiaozhi.cpp
String getDeviceId() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_WIFI_STA);
    char macStr[13];
    sprintf(macStr, "%02X%02X%02X%02X%02X%02X", 
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    return String(macStr);
}
```

**Device ID = 12 ký tự hex từ MAC address**

#### 2. Pairing Code Generation
```cpp
String getPairingCode() {
    String deviceId = getDeviceId();
    return deviceId.substring(deviceId.length() - 6);
}
```

**Pairing Code = 6 ký tự cuối của Device ID**

#### 3. TTS Announcement (QUAN TRỌNG!)
```cpp
void announcePairingCode() {
    String code = getPairingCode();
    
    // Format thành từng chữ số riêng lẻ để TTS đọc rõ
    String announcement = "配对码是：";
    for (int i = 0; i < code.length(); i++) {
        announcement += code.charAt(i);
        announcement += " ";
    }
    
    // Gửi đến Xiaozhi TTS
    sendToXiaozhi(announcement);
    
    // Hoặc dùng local TTS
    playTTS(announcement);
}
```

**Thiết bị phải ĐỌC TO mã pairing qua loa!**

#### 4. Khi nào announce?

```cpp
void setup() {
    // ...
    
    if (!isPaired()) {
        // Chưa pair → announce pairing code
        announcePairingCode();
        
        // Repeat mỗi 30 giây cho đến khi pair thành công
        while (!isPaired()) {
            delay(30000);
            announcePairingCode();
        }
    }
}
```

**Announce ngay khi:**
- Lần đầu khởi động (chưa pair)
- Sau khi reset pairing
- Repeat định kỳ cho đến khi pair xong

#### 5. WebSocket Handshake Format

```cpp
void sendHandshake() {
    StaticJsonDocument<512> doc;
    doc["header"]["name"] = "Authorize";
    doc["header"]["namespace"] = "ai.xiaoai.authorize";
    
    JsonObject payload = doc.createNestedObject("payload");
    payload["device_id"] = getDeviceId();
    payload["pairing_code"] = getPairingCode();
    payload["device_type"] = "esp32";
    payload["firmware_version"] = "1.0.0";
    
    String json;
    serializeJson(doc, json);
    webSocket.sendTXT(json);
}
```

**Format handshake:**
```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "esp32",
    "firmware_version": "1.0.0"
  }
}
```

## So sánh với implementation hiện tại của R1

### ✅ Đã đúng:
1. Device ID từ MAC address
2. Pairing code = 6 ký tự cuối
3. Format handshake (cần verify)

### ❌ Thiếu:
1. **TTS announcement của pairing code**
2. **Repeat announcement định kỳ**
3. **Check pairing status trước khi connect**

## Giải pháp cho R1 Xiaozhi App

### 1. Thêm TTS Announcement
```java
public class PairingCodeGenerator {
    
    public static void announcePairingCode(Context context) {
        String code = getPairingCode(context);
        
        // Format: "Mã ghép nối là: 1 2 3 4 5 6"
        String announcement = "Mã ghép nối là: ";
        for (char c : code.toCharArray()) {
            announcement += c + " ";
        }
        
        // Dùng Android TTS
        TextToSpeech tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("vi", "VN"));
                tts.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
        
        // Hoặc gửi đến AudioPlaybackService
        Intent intent = new Intent(context, AudioPlaybackService.class);
        intent.setAction("SPEAK_TEXT");
        intent.putExtra("text", announcement);
        context.startService(intent);
    }
}
```

### 2. Announce khi app start
```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check pairing status
        if (!isPaired()) {
            // Announce pairing code
            PairingCodeGenerator.announcePairingCode(this);
            
            // Schedule repeat every 30 seconds
            scheduleRepeatAnnouncement();
        }
    }
    
    private boolean isPaired() {
        SharedPreferences prefs = getSharedPreferences("xiaozhi_pairing", MODE_PRIVATE);
        return prefs.getBoolean("paired", false);
    }
    
    private void scheduleRepeatAnnouncement() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPaired()) {
                    PairingCodeGenerator.announcePairingCode(MainActivity.this);
                    handler.postDelayed(this, 30000); // Repeat after 30s
                }
            }
        }, 30000);
    }
}
```

### 3. Update handshake format
```java
private void sendHandshake() {
    try {
        JSONObject message = new JSONObject();
        
        // Header
        JSONObject header = new JSONObject();
        header.put("name", "Authorize");
        header.put("namespace", "ai.xiaoai.authorize");
        message.put("header", header);
        
        // Payload
        JSONObject payload = new JSONObject();
        payload.put("device_id", PairingCodeGenerator.getDeviceId(this));
        payload.put("pairing_code", PairingCodeGenerator.getPairingCode(this));
        payload.put("device_type", "android_r1");
        payload.put("firmware_version", BuildConfig.VERSION_NAME);
        message.put("payload", payload);
        
        webSocket.send(message.toString());
        
    } catch (JSONException e) {
        Log.e(TAG, "Failed to create handshake", e);
    }
}
```

### 4. Mark paired sau khi kết nối thành công
```java
private void onPairingSuccess() {
    SharedPreferences prefs = getSharedPreferences("xiaozhi_pairing", MODE_PRIVATE);
    prefs.edit().putBoolean("paired", true).apply();
    
    Log.i(TAG, "Device paired successfully!");
    
    // Stop announcement
    // Continue normal operation
}
```

## Kết luận

**Root Cause của lỗi "Enter the 6-digit code announced by the device":**

Xiaozhi Console kỳ vọng thiết bị sẽ:
1. ✅ Generate pairing code từ MAC
2. ❌ **ĐỌC TO mã qua loa để user nghe**
3. ❌ User nhập mã đã nghe vào console
4. ✅ Server verify mã có match với device_id trong handshake

**Cần implement:**
- TTS announcement của pairing code
- Repeat announcement cho đến khi paired
- Lưu paired status
- Stop announcement sau khi pair thành công

**Flow đúng:**
```
1. R1 boot → chưa paired
2. R1 đọc to: "Mã ghép nối là: D D E E F F"
3. User nghe và nhập "DDEEFF" vào Xiaozhi Console
4. Console add device với code này
5. R1 connect WebSocket → send handshake với device_id="AABBCCDDEEFF"
6. Server verify: pairing_code "DDEEFF" = last 6 chars of "AABBCCDDEEFF" ✓
7. Connection established → lưu paired=true → stop announcement