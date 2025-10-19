# MAC Address Format Fix

## Vấn đề
Khi gọi OTA API, server trả về lỗi **HTTP 400: "Invalid MAC address"**

## Nguyên nhân
**Format MAC address không khớp giữa Android và py-xiaozhi:**

### py-xiaozhi (ĐÚNG)
```python
# device_fingerprint.py - Line 91-94
def _normalize_mac_address(self, mac_address: str) -> str:
    clean_mac = re.sub(r'[^a-fA-F0-9]', '', mac_address)
    formatted_mac = ":".join(clean_mac[i : i + 2] for i in range(0, 12, 2))
    return formatted_mac.lower()  # ✅ "aa:bb:cc:dd:ee:ff"
```

### Android trước khi fix (SAI)
```java
// DeviceFingerprint.java - Line 107
private String retrieveMacAddress() {
    // ...
    return macAddress.replace(":", "").toLowerCase();  // ❌ "aabbccddeeff"
}
```

### Server OTA API yêu cầu
- **Format**: `"aa:bb:cc:dd:ee:ff"` (lowercase với dấu hai chấm)
- **Validation**: Reject nếu không đúng format → HTTP 400

## Giải pháp

### 1. Thêm phương thức chuẩn hóa MAC address
```java
/**
 * Normalize MAC address to format: aa:bb:cc:dd:ee:ff
 * Matches py-xiaozhi device_fingerprint.py line 91-94
 */
private String normalizeMacAddress(String mac) {
    if (mac == null || mac.isEmpty()) {
        return "00:00:00:00:00:00";
    }
    
    // Remove all non-hex characters
    String clean = mac.replaceAll("[^a-fA-F0-9]", "");
    
    if (clean.length() != 12) {
        return "00:00:00:00:00:00";
    }
    
    return formatMacAddress(clean);
}

/**
 * Format 12-character hex string to MAC format with colons
 * Example: "aabbccddeeff" -> "aa:bb:cc:dd:ee:ff"
 */
private String formatMacAddress(String cleanMac) {
    StringBuilder formatted = new StringBuilder();
    for (int i = 0; i < 12; i += 2) {
        if (i > 0) {
            formatted.append(":");
        }
        formatted.append(cleanMac.substring(i, i + 2));
    }
    return formatted.toString().toLowerCase();
}
```

### 2. Cập nhật retrieveMacAddress()
```java
private String retrieveMacAddress() {
    try {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);  // ✅ Fixed typo
        
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String macAddress = wifiInfo.getMacAddress();
            
            if (macAddress != null && !macAddress.equals("02:00:00:00:00:00")) {
                return normalizeMacAddress(macAddress);  // ✅ Return with colons
            }
        }
    } catch (Exception e) {
        // ...
    }
    return "00:00:00:00:00:00";
}
```

### 3. Cập nhật generateSerialNumber()
```java
public String generateSerialNumber() {
    String macAddress = getDeviceId();  // Already normalized with colons
    
    // Remove colons for serial number (match py-xiaozhi line 226)
    String macClean = macAddress.toLowerCase().replace(":", "");
    
    // Generate short hash from MAC + Android ID
    String androidId = Settings.Secure.getString(
        context.getContentResolver(), 
        Settings.Secure.ANDROID_ID
    );
    String combined = macClean + (androidId != null ? androidId : "");
    String hash = generateHash(combined);
    String shortHash = hash.substring(0, 8);
    
    return String.format("SN-%s-%s", shortHash, macClean);
}
```

## Kết quả

### Trước khi fix
```
MAC address gửi: "aabbccddeeff"
Server response: HTTP 400 {"error": "Invalid MAC address"}
```

### Sau khi fix
```
MAC address gửi: "aa:bb:cc:dd:ee:ff"
Server response: HTTP 200 + OTA config data
```

## Commit
- **Hash**: `71bb1fc`
- **Message**: "Fix: Correct MAC address format to match py-xiaozhi"
- **Files changed**: `DeviceFingerprint.java`

## Testing
Để test fix này:
1. Build và cài đặt APK
2. Chạy activation flow
3. Kiểm tra log xem MAC address có format đúng không
4. Verify server nhận được MAC với format `aa:bb:cc:dd:ee:ff`

## Reference
- **py-xiaozhi**: `src/core/device_fingerprint.py` line 70-94, 201-229
- **Android**: `DeviceFingerprint.java` line 96-175
- **OTA API**: `https://api.tenclass.net/xiaozhi/ota/`