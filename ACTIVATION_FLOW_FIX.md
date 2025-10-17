# 🔧 Activation Flow - Phân Tích và Sửa Lỗi

## ❌ Vấn Đề Hiện Tại

### 1. Android Code Hiện Tại SAI
```java
// DeviceActivator.java - Line 162
String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
```

**Vấn đề**: Tự tạo challenge thay vì nhận từ server!

### 2. Thiếu Bước OTA Config
Android code **KHÔNG** gọi OTA config API trước khi activate → Server không trả activation data!

### 3. Không Hiển Thị Code Đúng
Code 6 số **KHÔNG được client tạo**, mà **server trả về** trong OTA response!

---

## ✅ Flow Đúng (Theo py-xiaozhi)

### Bước 1: SystemInitializer - OTA Config
**File**: `system_initializer.py` (line 161-209)

```python
async def stage_3_ota_config(self):
    """Gọi OTA API để lấy config"""
    
    # GET https://r1.phicomm.com/v1/ota?
    #     sn={serial}&mac={mac}&signature={hmac}&client_id={id}
    
    config_result = await self.ota.fetch_and_update_config()
    response_data = config_result["response_data"]
    
    # Kiểm tra activation data trong response
    if "activation" in response_data:
        logger.info("Phát hiện activation info - thiết bị cần kích hoạt")
        self.activation_data = response_data["activation"]
        # activation_data chứa: {"challenge": "xxx", "code": "123456"}
        self.activation_status["server_activated"] = False
    else:
        logger.info("Không có activation info - thiết bị đã kích hoạt")
        self.activation_status["server_activated"] = True
```

**Response OTA khi chưa activate**:
```json
{
  "websocket": {...},
  "mqtt": {...},
  "activation": {
    "challenge": "server-generated-challenge-abc123",
    "code": "123456",
    "url": "https://r1.phicomm.com/v1/activate",
    "timeout": 300
  }
}
```

### Bước 2: DeviceActivator - Hiển Thị Code
**File**: `device_activator.py` (line 102-112)

```python
def process_activation(self, activation_data: Dict):
    """Xử lý activation với data từ server"""
    
    # Lấy challenge và code TỪ SERVER
    self.challenge = activation_data.get("challenge")
    code = activation_data.get("code")
    
    # Hiển thị code cho user
    self.logger.info(f"Mã kích hoạt: {code}")
    self.logger.info(f"Vui lòng truy cập https://r1.phicomm.com/activate")
    self.logger.info(f"Nhập mã: {code}")
```

### Bước 3: POST Activation Request
**File**: `device_activator.py` (line 159-341)

```python
async def activate(self) -> bool:
    """Gửi activation request với challenge từ server"""
    
    # Tạo HMAC signature với challenge từ server
    signature = self.device_fingerprint.generate_activation_signature(
        self.challenge  # Challenge TỪ SERVER, không tự tạo!
    )
    
    # POST /activate
    payload = {
        "sn": serial_number,
        "mac": mac_address,
        "challenge": self.challenge,  # Từ server
        "signature": signature,
        "client_id": client_id
    }
    
    response = await self.session.post(activation_url, json=payload)
    
    if response.status == 200:
        # Thành công - user đã nhập code
        data = await response.json()
        access_token = data.get("access_token")
        self.device_fingerprint.save_access_token(access_token)
        return True
        
    elif response.status == 202:
        # Đang đợi - user chưa nhập code
        await asyncio.sleep(5)
        continue  # Retry
        
    else:
        # Lỗi
        return False
```

---

## 🔄 Flow Hoàn Chỉnh

```
┌─────────────────────────────────────────────────────────────┐
│ 1. KHỞI ĐỘNG APP                                            │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. KIỂM TRA LOCAL ACTIVATION STATUS                         │
│    - Đọc efuse.json: is_activated?                          │
│    - Đọc access_token từ SharedPreferences                  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. GỌI OTA CONFIG API                                       │
│    GET /v1/ota?sn={sn}&mac={mac}&signature={hmac}          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
            ┌─────────────┴─────────────┐
            │                           │
            ▼                           ▼
  ┌──────────────────┐        ┌──────────────────┐
  │ Response có      │        │ Response KHÔNG   │
  │ "activation"     │        │ có "activation"  │
  └──────────────────┘        └──────────────────┘
            │                           │
            │                           ▼
            │                 ┌──────────────────┐
            │                 │ ĐÃ KÍCH HOẠT     │
            │                 │ → Kết nối WS     │
            │                 └──────────────────┘
            ▼
  ┌──────────────────────────────────────────┐
  │ 4. PARSE ACTIVATION DATA TỪ SERVER       │
  │    {                                      │
  │      "challenge": "abc123...",            │
  │      "code": "123456"                     │
  │    }                                      │
  └──────────────────────────────────────────┘
            │
            ▼
  ┌──────────────────────────────────────────┐
  │ 5. HIỂN THỊ CODE CHO USER                │
  │    "Mã kích hoạt: 123456"                │
  │    "Truy cập: r1.phicomm.com/activate"   │
  └──────────────────────────────────────────┘
            │
            ▼
  ┌──────────────────────────────────────────┐
  │ 6. POST /activate (POLLING LOOP)         │
  │    - Gửi challenge + HMAC signature      │
  │    - Đợi 5s, retry                       │
  └──────────────────────────────────────────┘
            │
            ▼
  ┌─────────────┴─────────────┐
  │                           │
  ▼                           ▼
┌──────────┐           ┌──────────┐
│ HTTP 202 │           │ HTTP 200 │
│ Đang đợi │           │ Thành    │
│ → Retry  │           │ công!    │
└──────────┘           └──────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ 7. LƯU TOKEN     │
                    │    - access_token│
                    │    - Update local│
                    │      is_activated│
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ 8. KẾT NỐI WS    │
                    │    với token     │
                    └──────────────────┘
```

---

## 🛠️ Cần Sửa Trong Android

### 1. Tạo OTA Manager
```java
package com.phicomm.r1.xiaozhi.activation;

public class OTAConfigManager {
    private static final String OTA_URL = "https://r1.phicomm.com/v1/ota";
    
    public static class OTAResponse {
        public WebSocketConfig websocket;
        public ActivationData activation;
        
        public static class WebSocketConfig {
            public String url;
            public String protocol;
        }
        
        public static class ActivationData {
            public String challenge;
            public String code;
            public String url;
            public int timeout;
        }
    }
    
    public interface OTACallback {
        void onSuccess(OTAResponse response);
        void onError(String error);
    }
    
    public void fetchOTAConfig(DeviceFingerprint fingerprint, OTACallback callback) {
        String sn = fingerprint.getSerialNumber();
        String mac = fingerprint.getMacAddress();
        String signature = fingerprint.generateOTASignature();
        String clientId = getClientId();
        
        String url = OTA_URL + "?sn=" + sn + "&mac=" + mac 
                   + "&signature=" + signature + "&client_id=" + clientId;
        
        // HTTP GET request
        // Parse JSON response
        // Callback với OTAResponse
    }
}
```

### 2. Sửa DeviceActivator
```java
public class DeviceActivator {
    private String serverChallenge;  // Từ OTA response
    private String verificationCode;  // Từ OTA response
    
    // BƯỚC 1: Gọi OTA config TRƯỚC
    public void startActivation() {
        OTAConfigManager otaManager = new OTAConfigManager();
        
        otaManager.fetchOTAConfig(deviceFingerprint, new OTACallback() {
            @Override
            public void onSuccess(OTAResponse response) {
                if (response.activation != null) {
                    // Thiết bị chưa kích hoạt
                    serverChallenge = response.activation.challenge;
                    verificationCode = response.activation.code;
                    
                    // Hiển thị code cho user
                    showVerificationCode(verificationCode);
                    
                    // Bắt đầu polling
                    startActivationPolling();
                } else {
                    // Thiết bị đã kích hoạt
                    onActivationSuccess(null);
                }
            }
            
            @Override
            public void onError(String error) {
                onActivationError(error);
            }
        });
    }
    
    // BƯỚC 2: Polling với challenge từ server
    private void startActivationPolling() {
        // Tạo HMAC với serverChallenge (KHÔNG tự tạo!)
        String signature = deviceFingerprint.generateActivationSignature(
            serverChallenge  // Từ server
        );
        
        JSONObject payload = new JSONObject();
        payload.put("sn", deviceFingerprint.getSerialNumber());
        payload.put("mac", deviceFingerprint.getMacAddress());
        payload.put("challenge", serverChallenge);  // Từ server
        payload.put("signature", signature);
        payload.put("client_id", getClientId());
        
        // POST /activate
        // If 202: retry after 5s
        // If 200: save token, done
    }
}
```

### 3. Update MainActivity
```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Khởi động activation flow
        DeviceActivator activator = new DeviceActivator(this);
        activator.startActivation();
    }
    
    private void showVerificationCode(String code) {
        TextView codeView = findViewById(R.id.verification_code);
        codeView.setText("Mã kích hoạt: " + code);
        
        TextView instructionView = findViewById(R.id.instruction);
        instructionView.setText("Vui lòng truy cập r1.phicomm.com/activate và nhập mã");
    }
}
```

---

## 📝 Tóm Tắt Thay Đổi

| Component | Hiện Tại | Cần Sửa |
|-----------|----------|---------|
| **OTA Config** | ❌ Không có | ✅ Thêm OTAConfigManager |
| **Challenge** | ❌ Tự tạo | ✅ Nhận từ server |
| **Code** | ❌ Không hiển thị | ✅ Hiển thị từ server |
| **Flow** | ❌ POST ngay | ✅ GET OTA → Parse → POST |
| **Token** | ❌ Không lưu | ✅ Lưu từ response |

---

## 🎯 Next Steps

1. ✅ **Tạo OTAConfigManager.java**
2. ✅ **Sửa DeviceActivator.java** - thêm OTA call
3. ✅ **Update MainActivity.java** - hiển thị code
4. ✅ **Test flow hoàn chỉnh**
5. ✅ **Document và push code**

---

## 📚 References

- **py-xiaozhi**: `src/core/system_initializer.py` (line 161-209)
- **py-xiaozhi**: `src/activation/device_activator.py` (line 102-341)
- **py-xiaozhi**: `src/core/ota.py` (OTA config fetching)

**Kết luận**: Android code thiếu bước OTA config → không nhận được challenge+code từ server → activation fail!