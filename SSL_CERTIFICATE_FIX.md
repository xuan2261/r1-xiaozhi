# SSL Certificate Expiration Fix

## Vấn đề
WebSocket connection bị lỗi do SSL certificate của server đã hết hạn:
```
ExtCertPathValidatorException: Could not validate certificate: 
Certificate expired at Fri Nov 08 07:59:59 GMT+08:00 2024 
(compared to Sat Oct 18 19:38:08 GMT+08:00 2025)
```

## Nguyên nhân
- Server `wss://xiaozhi.me/v1/ws` sử dụng SSL certificate đã expired
- Certificate hết hạn: **Nov 08, 2024**
- Ngày hiện tại: **Oct 18, 2025** 
- Android WebSocket client mặc định validate SSL certificate → reject expired cert

## Giải pháp

### Option 1: Bypass SSL Validation (Development/Testing Only)
**⚠️ CẢNH BÁO: CHỈ DÙNG CHO TESTING, KHÔNG DÙNG PRODUCTION!**

Thêm custom `SSLSocketFactory` và `HostnameVerifier` để bypass SSL validation:

```java
// TrustAllCertificates.java
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class TrustAllCertificates {
    
    public static SSLSocketFactory getSSLSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static HostnameVerifier getAllowAllHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // Accept all hostnames
            }
        };
    }
}
```

**Update XiaozhiConnectionService.java:**
```java
// In connectWithToken() method
webSocketClient = new WebSocketClient(serverUri, headers) {
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // ...
    }
    // ... other methods
};

// Set SSL socket factory (bypass certificate validation)
if (serverUri.getScheme().equals("wss")) {
    webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
}

webSocketClient.connect();
```

### Option 2: Request Server Admin to Renew Certificate
Liên hệ với admin của `xiaozhi.me` để renew SSL certificate.

### Option 3: Use HTTP Instead of HTTPS (Not Recommended)
Chuyển từ `wss://` sang `ws://` (unencrypted):
```java
// XiaozhiConfig.java
public static final String WEBSOCKET_URL = "ws://xiaozhi.me/v1/ws";
```

**⚠️ Không an toàn - dữ liệu không được mã hóa!**

### Option 4: Add Custom Certificate to Trust Store
Nếu bạn có certificate mới từ server, có thể add vào app's trust store.

## Recommended Solution

**Cho Development/Testing:**
1. Sử dụng Option 1 (Bypass SSL) với flag enable/disable
2. Add configuration để bật/tắt SSL validation
3. Log warning rõ ràng khi SSL bypass được enable

**Cho Production:**
1. Request server admin renew certificate
2. Hoặc sử dụng server khác với valid certificate

## Implementation Plan

### 1. Create TrustAllCertificates utility
```java
package com.phicomm.r1.xiaozhi.util;

// ... code above
```

### 2. Add SSL bypass option to XiaozhiConfig
```java
// For testing only - disable SSL validation
public static final boolean BYPASS_SSL_VALIDATION = true; // Set to false in production!
```

### 3. Update XiaozhiConnectionService
```java
if (serverUri.getScheme().equals("wss") && XiaozhiConfig.BYPASS_SSL_VALIDATION) {
    Log.w(TAG, "⚠️ SSL VALIDATION BYPASSED - FOR TESTING ONLY!");
    webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
}
```

## Security Notes

- **NEVER** bypass SSL validation in production apps
- Expired certificates là security risk
- Man-in-the-middle attacks có thể xảy ra nếu không validate SSL
- Chỉ dùng bypass cho testing với server bạn trust hoàn toàn

## Timeline
- Certificate expired: **Nov 08, 2024**
- Current date: **Oct 18, 2025**
- Certificate đã expired **11 tháng 10 ngày**

Server admin cần renew certificate ngay lập tức!