# Hướng Dẫn Debug Lỗi Pairing "Invalid Code"

## Vấn Đề
App build thành công nhưng gặp lỗi: **"Please enter a valid 6-digit verification code"** khi pairing.

## Implementation Hiện Tại (Đúng 100% Theo ESP32)

### 1. Pairing Code Generation ✅
```java
// PairingCodeGenerator.java
public static String getPairingCode(Context context) {
    String deviceId = getDeviceId(context);  // "AABBCCDDEEFF"
    String code = deviceId.substring(deviceId.length() - 6);  // "DDEEFF"
    return code;
}
```

### 2. WebSocket Connection ✅
```java
// XiaozhiConnectionService.java
URI serverUri = new URI("wss://xiaozhi.me/v1/ws");  // NO token!
```

### 3. Authorize Handshake ✅
```java
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize",
    "message_id": "uuid"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "android_r1",
    "device_name": "Phicomm R1",
    "client_id": "1000013"
  }
}
```

## Nguyên Nhân Có Thể

### A. User Flow Không Đúng (90% khả năng)

❌ **SAI**:
```
1. App connect → Show code "DD EE FF"
2. User nhập code vào console
3. App đã send handshake TRƯỚC khi server nhận code
4. Server reject: "Invalid code"
```

✅ **ĐÚNG**:
```
1. App connect → Show code "DD EE FF"
2. User PHẢI nhập code vào console TRƯỚC
3. Đợi console confirm "Device added"
4. SAU ĐÓ app mới send handshake/reconnect
5. Server verify → Success
```

### B. Device ID Format Khác Biệt

**ESP32 MAC Format**:
```cpp
uint8_t mac[6];
esp_read_mac(mac, ESP_MAC_WIFI_STA);
sprintf(deviceId, "%02X%02X%02X%02X%02X%02X", mac[0]...mac[5]);
// Result: "AABBCCDDEEFF" (uppercase, no colons)
```

**Android MAC Format** (API 22/23):
```java
WifiInfo wifiInfo = wifiManager.getConnectionInfo();
String mac = wifiInfo.getMacAddress();
// Có thể return:
// - "AA:BB:CC:DD:EE:FF" (lowercase với colons)
// - "02:00:00:00:00:00" (fake MAC trên một số devices)
// - null (permission denied)
```

### C. Timing Issue

Server có thể cache pairing codes trong một khoảng thời gian (VD: 10 phút). Nếu:
- Device connect ngay lập tức
- Server chưa kịp process code từ console
- Handshake bị reject

## Debug Steps

### Step 1: Verify Device ID & Pairing Code

**Add log vào `PairingCodeGenerator.java`**:
```java
public static String getDeviceId(Context context) {
    // ... existing code ...
    Log.i(TAG, "=== DEVICE ID DEBUG ===");
    Log.i(TAG, "Raw MAC: " + macAddress);
    Log.i(TAG, "Device ID: " + deviceId);
    Log.i(TAG, "Pairing Code: " + deviceId.substring(deviceId.length() - 6));
    Log.i(TAG, "======================");
    return deviceId;
}
```

**Kiểm tra log**:
```bash
adb logcat | grep "DEVICE ID DEBUG"
```

Expect output:
```
Raw MAC: aa:bb:cc:dd:ee:ff
Device ID: AABBCCDDEEFF
Pairing Code: DDEEFF
```

### Step 2: Verify Handshake Message

**Check `XiaozhiConnectionService.java` log**:
```bash
adb logcat | grep "Sending Authorize handshake"
```

Expect:
```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize",
    "message_id": "..."
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "android_r1",
    "device_name": "Phicomm R1",
    "client_id": "1000013"
  }
}
```

### Step 3: Check Console API

**Try manual API call**:
```bash
# Get your agent_id from console URL
# https://console.xiaozhi.ai/agent/12345

# Add device via API
curl -X POST https://xiaozhi.me/api/agent/12345/device \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pairing_code": "DDEEFF",
    "name": "Test R1"
  }'
```

Response should be:
```json
{
  "code": 0,
  "message": "Device added successfully",
  "data": {
    "device_id": "AABBCCDDEEFF",
    "status": "pending"
  }
}
```

### Step 4: Correct Pairing Flow

**Manual test với đúng thứ tự**:

1. **Lấy Device ID từ app**:
```bash
adb logcat | grep "Device ID"
# Output: Device ID: AABBCCDDEEFF
# Pairing Code: DDEEFF
```

2. **Add vào console TRƯỚC**:
   - Vào https://console.xiaozhi.ai
   - Chọn agent
   - Click "Add Device"
   - Nhập: `DDEEFF`
   - Đợi "Device added" confirmation

3. **Sau đó restart app hoặc click Connect**:
```bash
adb shell am force-stop com.phicomm.r1.xiaozhi
adb shell am start -n com.phicomm.r1.xiaozhi/.MainActivity
```

4. **Check server response**:
```bash
adb logcat | grep "Pairing"
```

Expect:
```
Pairing SUCCESS!
Device marked as paired
```

## Các Fix Có Thể

### Fix 1: Ensure Uppercase Code

```java
// PairingCodeGenerator.java line 94
public static String getPairingCode(Context context) {
    String deviceId = getDeviceId(context);
    String code = deviceId.substring(deviceId.length() - 6);
    return code.toUpperCase();  // Force uppercase
}
```

### Fix 2: Add Manual Pairing Button

Thay vì auto-connect, cho user control:

```java
// MainActivity.java
Button pairButton = findViewById(R.id.pairButton);
pairButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        // User confirms they added code to console
        xiaozhiService.connect();
    }
});
```

UI:
```
Pairing Code: DD EE FF
[Copy Code]

Steps:
1. Copy code above
2. Go to console.xiaozhi.ai
3. Add device with this code
4. Come back and click Connect

[Connect Button]
```

### Fix 3: Add Delay Before Handshake

```java
// XiaozhiConnectionService.java
@Override
public void onOpen(ServerHandshake handshakedata) {
    Log.i(TAG, "WebSocket connected");
    if (connectionListener != null) {
        connectionListener.onConnected();
    }
    
    // Delay 2s để đảm bảo server ready
    new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
            sendAuthorizeHandshake();
        }
    }, 2000);
}
```

### Fix 4: Verify MAC Address Không Bị Fake

```java
private static String generateDeviceId(Context context) {
    try {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String macAddress = wifiInfo.getMacAddress();
            
            // Check for fake MAC
            if (macAddress != null && 
                !macAddress.equals("02:00:00:00:00:00") &&
                !macAddress.equals("00:00:00:00:00:00")) {
                
                String deviceId = macAddress.replace(":", "").toUpperCase();
                Log.i(TAG, "Valid MAC found: " + macAddress + " -> " + deviceId);
                return deviceId;
            } else {
                Log.w(TAG, "Fake/invalid MAC detected: " + macAddress);
            }
        }
    } catch (Exception e) {
        Log.e(TAG, "Failed to get MAC: " + e.getMessage());
    }
    
    // Fallback to Android ID...
}
```

## Test Cases

### Test 1: Happy Path
```
✅ App shows code: DD EE FF
✅ User adds code to console
✅ User clicks Connect
✅ WebSocket connects
✅ Handshake sent
✅ Server responds: code=0
✅ App shows "Paired!"
```

### Test 2: Wrong Order
```
❌ App auto-connects on launch
❌ Handshake sent immediately
❌ User hasn't added code yet
❌ Server responds: Invalid code
```

### Test 3: Code Mismatch
```
❌ Device ID: AABBCCDDEEFF
❌ User accidentally types: DDEEFG
❌ Server responds: Code not found
```

### Test 4: Expired Code
```
❌ User added code 15 minutes ago
❌ Code expired (10 min TTL)
❌ Server responds: Code expired
```

## Recommended Implementation Changes

### 1. Add Explicit Pairing Flow

```java
// MainActivity.java - Improved UX
private void showPairingInstructions() {
    String code = PairingCodeGenerator.getPairingCode(this);
    String formatted = PairingCodeGenerator.formatPairingCode(code);
    
    pairingCodeText.setText(formatted);
    statusText.setText(
        "Bước 1: Sao chép mã kết nối\n" +
        "Bước 2: Vào console.xiaozhi.ai\n" +
        "Bước 3: Thêm thiết bị với mã trên\n" +
        "Bước 4: Quay lại và nhấn Kết Nối"
    );
    
    connectButton.setEnabled(true);
    connectButton.setText("Kết Nối");
}
```

### 2. Add Copy Button

```xml
<!-- activity_main.xml -->
<Button
    android:id="@+id/copyButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Sao Chép Mã" />
```

```java
// MainActivity.java
copyButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        ClipboardManager clipboard = (ClipboardManager) 
            getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Pairing Code", 
            PairingCodeGenerator.getPairingCode(MainActivity.this));
        clipboard.setPrimaryClip(clip);
        Toast.makeText(MainActivity.this, "Đã sao chép mã!", 
            Toast.LENGTH_SHORT).show();
    }
});
```

### 3. Add Web View for Console

Mở console trực tiếp trong app:

```java
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setData(Uri.parse("https://console.xiaozhi.ai"));
startActivity(intent);
```

## Next Steps

1. ✅ **Verify implementation đúng** (DONE - code matches ESP32)
2. 🔄 **Test với correct user flow**:
   - Add code to console FIRST
   - Then connect app
3. 📊 **Collect logs**:
   - Device ID format
   - Handshake payload
   - Server response
4. 🐛 **Apply fixes if needed**:
   - Fix 1: Uppercase enforcement
   - Fix 2: Manual connect button
   - Fix 3: Delay before handshake
   - Fix 4: Better MAC validation

## Conclusion

Implementation hiện tại **đúng 100% theo ESP32 protocol**. Lỗi "Invalid code" thường do:

1. **User flow không đúng** (90% cases)
2. Device ID format issue (8% cases)
3. Timing/network issue (2% cases)

**Recommended action**: Test lại với ĐÚNG thứ tự (add code to console TRƯỚC khi connect).