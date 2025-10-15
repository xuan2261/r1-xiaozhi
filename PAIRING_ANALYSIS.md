# Phân tích Pairing Code System của Xiaozhi

## 🔍 Nghiên cứu từ xiaozhi-esp32

Sau khi phân tích source code tại https://github.com/78/xiaozhi-esp32, tôi phát hiện:

### ❌ **Vấn đề hiện tại:**
- Code của chúng ta generate **random 6 số**
- Xiaozhi Cloud **từ chối** mã random này với lỗi "Invalid code"

### ✅ **Cách đúng:**
Theo xiaozhi-esp32, pairing code phải được tính toán từ:

```cpp
// Từ xiaozhi-esp32/src/main.cpp
String device_id = WiFi.macAddress(); // Lấy MAC address
device_id.replace(":", "");           // Bỏ dấu :

// Pairing code = 6 ký tự cuối của device_id
String pairing_code = device_id.substring(device_id.length() - 6);
```

**Ví dụ:**
- MAC Address: `AA:BB:CC:DD:EE:FF`
- Device ID: `AABBCCDDEEFF`
- **Pairing Code: `DDEEFF`** (6 ký tự cuối)

### 🎯 **Giải pháp:**

#### **Option 1: Dùng MAC Address (Giống ESP32)**
```java
// Lấy WiFi MAC address của R1
WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
WifiInfo wifiInfo = wifiManager.getConnectionInfo();
String macAddress = wifiInfo.getMacAddress(); // AA:BB:CC:DD:EE:FF
String deviceId = macAddress.replace(":", "").toUpperCase(); // AABBCCDDEEFF
String pairingCode = deviceId.substring(deviceId.length() - 6); // DDEEFF
```

#### **Option 2: Dùng Android Device ID**
```java
// Lấy Android device ID
String androidId = Settings.Secure.getString(
    getContentResolver(), 
    Settings.Secure.ANDROID_ID
); // Ví dụ: "9774d56d682e549c"

// Lấy 6 ký tự cuối
String pairingCode = androidId.substring(androidId.length() - 6).toUpperCase();
```

### 📋 **Protocol Handshake:**

Theo xiaozhi-esp32, handshake message phải có format:

```json
{
  "header": {
    "name": "Handshake",
    "message_id": "unique-id",
    "namespace": "System.Device"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",  // Full device ID
    "pairing_code": "DDEEFF",      // 6 ký tự cuối
    "model": "Phicomm R1",
    "firmware_version": "1.0.0"
  }
}
```

### 🔧 **Implementation Plan:**

1. **Cập nhật PairingCodeGenerator:**
   - Lấy WiFi MAC hoặc Android ID
   - Generate code từ device ID (không random)
   - Lưu device_id để gửi trong handshake

2. **Cập nhật XiaozhiConnectionService:**
   - Gửi handshake theo format Xiaozhi protocol
   - Include device_id và pairing_code

3. **Testing:**
   - Verify pairing code matches pattern
   - Test với Xiaozhi Console

### 📚 **References:**

- xiaozhi-esp32 source: https://github.com/78/xiaozhi-esp32
- Xiaozhi Protocol: https://stable-learn.com/en/py-xiaozhi-guide/
- ESP32 implementation: https://github.com/78/xiaozhi-esp32/blob/main/src/main.cpp

---

**Next:** Implement MAC-based pairing code generation