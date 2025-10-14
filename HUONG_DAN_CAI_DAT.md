# Hướng dẫn Tích hợp Xiaozhi vào Phicomm R1

## Tổng quan

Dự án này tích hợp thư viện **xiaozhi** vào loa **Phicomm R1**, biến nó thành AI voice assistant thông minh với khả năng:

- ✅ Nhận diện giọng nói và wake word detection
- ✅ Kết nối với Xiaozhi API (Cloud hoặc Self-hosted)
- ✅ Phát âm thanh TTS từ Xiaozhi
- ✅ Điều khiển LED để hiển thị trạng thái
- ✅ Tự động khởi động khi boot
- ✅ Chạy nền 24/7

## Yêu cầu

### Phần cứng
- **Phicomm R1** speaker (với chipset Rockchip RK3229)
- Kết nối ADB qua USB hoặc WiFi
- Root access (để điều khiển LED)

### Phần mềm
- **Android Studio** 2.3.3 hoặc mới hơn
- **Android SDK** API Level 22 (Android 5.1)
- **JDK** 1.7 hoặc 1.8
- **ADB** (Android Debug Bridge)
- **Gradle** (đi kèm với Android Studio)

### Xiaozhi API
- Tài khoản Xiaozhi Cloud (https://xiaozhi.me) HOẶC
- Self-hosted Xiaozhi server (xem docs tại https://stable-learn.com/en/py-xiaozhi-guide/)

## Bước 1: Chuẩn bị môi trường phát triển

### 1.1. Cài đặt Android Studio

```bash
# Download Android Studio từ:
# https://developer.android.com/studio

# Sau khi cài đặt, mở Android Studio và cài các components:
# - Android SDK Platform 22
# - Android SDK Build-Tools 22.0.1
# - Android SDK Platform-Tools
```

### 1.2. Thiết lập ADB

```bash
# Kiểm tra ADB đã cài đặt
adb version

# Nếu chưa có, thêm vào PATH:
# Windows: Thêm C:\Users\[YourUser]\AppData\Local\Android\Sdk\platform-tools vào PATH
# Linux/Mac: export PATH=$PATH:~/Android/Sdk/platform-tools
```

### 1.3. Kết nối R1 qua ADB

```bash
# Bật Developer Mode trên R1 (nếu có thể truy cập settings)
# Hoặc kết nối qua USB và enable ADB

# Kiểm tra kết nối
adb devices

# Nếu kết nối qua WiFi
adb connect 192.168.1.XXX:5555  # Thay XXX bằng IP của R1
```

## Bước 2: Build ứng dụng

### 2.1. Clone hoặc mở project

```bash
# Mở Android Studio
# File > Open > Chọn folder R1XiaozhiApp
```

### 2.2. Sync Gradle

```bash
# Android Studio sẽ tự động sync
# Hoặc chạy thủ công:
cd R1XiaozhiApp
./gradlew clean build  # Linux/Mac
gradlew.bat clean build  # Windows
```

### 2.3. Build APK

```bash
# Build debug APK (cho testing)
./gradlew assembleDebug

# Build release APK (cho production)
./gradlew assembleRelease

# APK sẽ được tạo tại:
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

## Bước 3: Cấu hình Xiaozhi

### 3.1. Chọn mode kết nối

Bạn có 2 lựa chọn:

**Option A: Sử dụng Xiaozhi Cloud (Đơn giản)**
- URL: `wss://xiaozhi.me/websocket`
- Đăng ký tài khoản tại https://xiaozhi.me
- Lấy API key (nếu cần)

**Option B: Self-hosted Xiaozhi Server**
- Cài đặt server theo hướng dẫn: https://stable-learn.com/en/py-xiaozhi-guide/
- URL: `ws://YOUR_SERVER_IP:8080/websocket`

### 3.2. Sửa cấu hình mặc định (Optional)

Mở file [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:25) và sửa:

```java
public static final String DEFAULT_CLOUD_URL = "wss://xiaozhi.me/websocket";
public static final String DEFAULT_SELF_HOSTED_URL = "ws://192.168.1.100:8080/websocket";
public static final String DEFAULT_WAKE_WORD = "小智"; // Hoặc "Xiao Zhi"
```

## Bước 4: Cài đặt lên R1

### 4.1. Disable system apps của R1 (Quan trọng!)

```bash
# Disable các app gốc của Phicomm để tránh xung đột
adb shell pm hide com.phicomm.speaker.player
adb shell pm hide com.phicomm.speaker.device
adb shell pm hide com.phicomm.speaker.airskill
adb shell pm hide com.phicomm.speaker.exceptionreporter

# Kiểm tra đã disable thành công
adb shell pm list packages -d | grep phicomm
```

### 4.2. Cài đặt APK

```bash
# Copy APK lên R1
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/

# Cài đặt
adb shell pm install -t -r /data/local/tmp/app-release.apk

# Hoặc cài trực tiếp (nếu ADB version mới)
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 4.3. Grant permissions

```bash
# Grant quyền ghi âm và các permissions khác
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.READ_EXTERNAL_STORAGE
```

### 4.4. Root R1 để điều khiển LED (Optional nhưng recommended)

```bash
# Kiểm tra root
adb shell su -c "id"

# Nếu chưa root, tham khảo:
# https://github.com/sagan/r1-helper
# https://www.computersolutions.cn/blog/2019/08/hacking-a-phicomm-r1-speaker/

# Grant quyền su cho app
adb shell su -c "pm grant com.phicomm.r1.xiaozhi android.permission.ACCESS_SUPERUSER"
```

## Bước 5: Khởi động và cấu hình

### 5.1. Khởi động app lần đầu

```bash
# Start MainActivity
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Hoặc start service trực tiếp
adb shell am startservice com.phicomm.r1.xiaozhi/.service.VoiceRecognitionService
adb shell am startservice com.phicomm.r1.xiaozhi/.service.XiaozhiConnectionService
```

### 5.2. Cấu hình qua SharedPreferences

```bash
# Set Cloud mode
adb shell "echo 'use_cloud=true' > /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml"

# Hoặc edit trực tiếp file XML:
adb pull /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml
# Edit local file
adb push xiaozhi_config.xml /data/data/com.phicomm.r1.xiaozhi/shared_prefs/
```

### 5.3. Cấu hình tham số

Các tham số có thể cấu hình:

- `use_cloud`: `true` hoặc `false`
- `cloud_url`: URL của Xiaozhi Cloud
- `self_hosted_url`: URL của Self-hosted server
- `api_key`: API key (nếu cần)
- `wake_word`: Wake word (mặc định: "小智")
- `auto_start`: Tự động start khi boot (`true`/`false`)
- `led_enabled`: Bật/tắt LED (`true`/`false`)
- `http_server_port`: Port cho HTTP server (mặc định: 8088)

## Bước 6: Testing

### 6.1. Kiểm tra log

```bash
# Xem log realtime
adb logcat | grep -E "(VoiceRecognition|XiaozhiConnection|AudioPlayback|LEDControl)"

# Hoặc filter theo tag
adb logcat VoiceRecognition:D XiaozhiConnection:D AudioPlayback:D LEDControl:D *:S
```

### 6.2. Test wake word

```bash
# Nói wake word vào mic của R1
# Mặc định: "小智" (Xiao Zhi)

# Kiểm tra log để thấy:
# VoiceRecognition: Wake word detected!
# LEDControl: State: LISTENING
```

### 6.3. Test audio playback

```bash
# Gửi test command
adb shell am startservice \
  -n com.phicomm.r1.xiaozhi/.service.AudioPlaybackService \
  -a com.phicomm.r1.xiaozhi.PLAY_URL \
  --es audio_url "https://example.com/test.mp3"
```

## Bước 7: Auto-start khi boot

### 7.1. Enable auto-start

App đã có [`BootReceiver`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/BootReceiver.java:1) để tự động khởi động.

```bash
# Kiểm tra BootReceiver đã enable
adb shell pm list packages -e | grep xiaozhi

# Test boot receiver
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
```

### 7.2. Disable battery optimization

```bash
# Để app không bị kill
adb shell dumpsys deviceidle whitelist +com.phicomm.r1.xiaozhi
```

## Troubleshooting

### Lỗi: "Cannot connect to Xiaozhi"

**Giải pháp:**
1. Kiểm tra kết nối mạng của R1
2. Ping xiaozhi server: `adb shell ping xiaozhi.me`
3. Kiểm tra URL trong config
4. Thử chuyển sang self-hosted mode

### Lỗi: "No RECORD_AUDIO permission"

**Giải pháp:**
```bash
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO
```

### Lỗi: "LED control not working"

**Giải pháp:**
1. Kiểm tra root access
2. Test LED manually:
```bash
adb shell su -c "echo '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"
```

### App bị crash khi boot

**Giải pháp:**
```bash
# Xem crash log
adb logcat | grep AndroidRuntime

# Disable auto-start tạm thời
adb shell pm disable com.phicomm.r1.xiaozhi/.receiver.BootReceiver
```

### Wake word không hoạt động

**Giải pháp:**
1. Hiện tại đang dùng energy-based detection đơn giản
2. Để improve, tích hợp thư viện chuyên dụng như:
   - **Porcupine** (https://github.com/Picovoice/porcupine)
   - **Snowboy** (https://github.com/Kitt-AI/snowboy)

## Advanced: HTTP API Server

App có built-in HTTP server để remote control:

```bash
# Truy cập từ browser hoặc curl
curl http://192.168.1.XXX:8088/status
curl http://192.168.1.XXX:8088/start
curl http://192.168.1.XXX:8088/stop
curl http://192.168.1.XXX:8088/config
```

## Uninstall

```bash
# Remove app
adb uninstall com.phicomm.r1.xiaozhi

# Re-enable system apps
adb shell pm unhide com.phicomm.speaker.player
adb shell pm unhide com.phicomm.speaker.device
adb shell pm unhide com.phicomm.speaker.airskill
```

## Tài liệu tham khảo

- **Xiaozhi Docs**: https://stable-learn.com/en/py-xiaozhi-guide/
- **Xiaozhi Hardware Guide**: https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/
- **R1 Hacking**: https://github.com/sagan/r1-helper
- **R1 Custom ROM**: https://github.com/sallaixu/R1-APP

## Hỗ trợ

Nếu gặp vấn đề, vui lòng:
1. Kiểm tra logs: `adb logcat | grep Xiaozhi`
2. Xem lại các bước cấu hình
3. Test từng service riêng lẻ

## License

MIT License - Free to use and modify

---

**Chúc bạn thành công! 🎉**