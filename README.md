# Xiaozhi Voice Assistant cho Phicomm R1

Dự án tích hợp thư viện **xiaozhi** vào loa thông minh **Phicomm R1**, biến nó thành AI voice assistant hoàn chỉnh với khả năng nhận diện giọng nói, xử lý AI, và phản hồi TTS.

## 🎯 Tính năng

- ✅ **Wake Word Detection**: Phát hiện từ kích hoạt "小智" (Xiao Zhi)
- ✅ **Voice Recognition**: Thu âm và gửi đến Xiaozhi API
- ✅ **Dual Mode**: Hỗ trợ Cloud và Self-hosted Xiaozhi server
- ✅ **Auto Fallback**: Tự động chuyển từ Cloud sang Self-hosted khi mất kết nối
- ✅ **LED Control**: Hiển thị trạng thái qua LED strip của R1
- ✅ **Audio Playback**: Phát TTS response từ Xiaozhi
- ✅ **Auto Start**: Tự động khởi động khi R1 boot
- ✅ **Background Service**: Chạy nền 24/7

## 📋 Yêu cầu

### Hardware
- Phicomm R1 speaker (Rockchip RK3229)
- Kết nối USB hoặc WiFi ADB
- Root access (optional, cho LED control)

### Software
- Android Studio 2.3.3+
- Android SDK API 22 (Android 5.1)
- JDK 1.7/1.8
- ADB (Android Debug Bridge)

### Xiaozhi API
- Xiaozhi Cloud account (https://xiaozhi.me) HOẶC
- Self-hosted Xiaozhi server

## 🚀 Cài đặt nhanh

### Cách 1: Sử dụng script tự động (Khuyến nghị)

**Linux/Mac:**
```bash
cd scripts
chmod +x install.sh
./install.sh [R1_IP_ADDRESS]
```

**Windows:**
```cmd
cd scripts
install.bat [R1_IP_ADDRESS]
```

### Cách 2: Cài đặt thủ công

Xem hướng dẫn chi tiết trong file [`HUONG_DAN_CAI_DAT.md`](HUONG_DAN_CAI_DAT.md:1)

## 📁 Cấu trúc dự án

```
.
├── R1XiaozhiApp/                    # Android project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/phicomm/r1/xiaozhi/
│   │   │   │   ├── config/
│   │   │   │   │   └── XiaozhiConfig.java         # Cấu hình
│   │   │   │   ├── service/
│   │   │   │   │   ├── VoiceRecognitionService.java    # Thu âm
│   │   │   │   │   ├── XiaozhiConnectionService.java   # Kết nối API
│   │   │   │   │   ├── AudioPlaybackService.java       # Phát âm
│   │   │   │   │   └── LEDControlService.java          # LED
│   │   │   │   ├── receiver/
│   │   │   │   │   └── BootReceiver.java          # Auto-start
│   │   │   │   └── ui/
│   │   │   │       └── MainActivity.java          # Giao diện
│   │   │   ├── res/                               # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
├── scripts/
│   ├── install.sh                   # Script cài đặt (Linux/Mac)
│   └── install.bat                  # Script cài đặt (Windows)
├── HUONG_DAN_CAI_DAT.md            # Hướng dẫn chi tiết
└── README.md                        # File này
```

## ⚙️ Cấu hình

### Xiaozhi Connection Mode

App hỗ trợ 2 chế độ kết nối:

1. **Cloud Mode** (mặc định)
   - URL: `wss://xiaozhi.me/websocket`
   - Đăng ký tại: https://xiaozhi.me
   
2. **Self-hosted Mode**
   - URL: `ws://YOUR_SERVER:8080/websocket`
   - Cài đặt server: https://stable-learn.com/en/py-xiaozhi-guide/

### Cấu hình trong [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:1)

```java
public static final String DEFAULT_CLOUD_URL = "wss://xiaozhi.me/websocket";
public static final String DEFAULT_SELF_HOSTED_URL = "ws://192.168.1.100:8080/websocket";
public static final String DEFAULT_WAKE_WORD = "小智";
```

## 🔧 Build từ source

```bash
# Clone repository
git clone <repository-url>
cd r1xiaozhi

# Build APK
cd R1XiaozhiApp
./gradlew assembleRelease  # Linux/Mac
gradlew.bat assembleRelease  # Windows

# APK sẽ được tạo tại:
# app/build/outputs/apk/release/app-release.apk
```

## 📱 Sử dụng

### Khởi động services

```bash
# Start tất cả services
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Hoặc start từng service riêng
adb shell am startservice com.phicomm.r1.xiaozhi/.service.VoiceRecognitionService
adb shell am startservice com.phicomm.r1.xiaozhi/.service.XiaozhiConnectionService
```

### Test wake word

1. Nói wake word: **"小智"** (Xiao Zhi)
2. LED sẽ chuyển sang màu xanh lá (listening)
3. Nói câu lệnh của bạn
4. LED chuyển màu trắng (thinking)
5. R1 phát response từ Xiaozhi

### Xem logs

```bash
# Realtime logs
adb logcat | grep -E "(VoiceRecognition|XiaozhiConnection|AudioPlayback|LEDControl)"

# Hoặc filter theo tag
adb logcat VoiceRecognition:D XiaozhiConnection:D *:S
```

## 🎨 LED Status Colors

- **Xanh dương nhạt**: Idle (chờ wake word)
- **Xanh lá (xoay)**: Listening (đang nghe)
- **Trắng (pulse)**: Thinking (đang xử lý)
- **Cyan**: Speaking (đang phát âm)
- **Đỏ (nhấp nháy)**: Error

## 🔌 HTTP API

App có built-in HTTP server trên port `8088`:

```bash
# Check status
curl http://192.168.1.XXX:8088/status

# Start services
curl http://192.168.1.XXX:8088/start

# Stop services
curl http://192.168.1.XXX:8088/stop

# Get config
curl http://192.168.1.XXX:8088/config
```

## 🐛 Troubleshooting

### App không kết nối được Xiaozhi

```bash
# Kiểm tra network
adb shell ping xiaozhi.me

# Kiểm tra config
adb shell cat /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml
```

### Wake word không hoạt động

Wake word detection hiện sử dụng energy-based method đơn giản. Để cải thiện, có thể tích hợp:
- **Porcupine**: https://github.com/Picovoice/porcupine
- **Snowboy**: https://github.com/Kitt-AI/snowboy

### LED không hoạt động

```bash
# Kiểm tra root
adb shell su -c "id"

# Test LED manually
adb shell su -c "echo '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"
```

## 📚 Tài liệu tham khảo

- **Xiaozhi Documentation**: https://stable-learn.com/en/py-xiaozhi-guide/
- **Xiaozhi Hardware Guide**: https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/
- **Phicomm R1 Hacking**: https://github.com/sagan/r1-helper
- **R1 Custom Apps**: https://github.com/sallaixu/R1-APP
- **Android Background Services**: https://developer.android.com/develop/background-work/services

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

MIT License - Free to use and modify

## ⚠️ Disclaimer

Dự án này không liên quan chính thức với Phicomm hay Xiaozhi. Sử dụng có trách nhiệm và tuân thủ các điều khoản dịch vụ.

## 🙏 Credits

- **Xiaozhi Team** - AI engine
- **Phicomm R1 Community** - Hardware hacking guides
- Các contributors và testers

---

**Developed with ❤️ for the smart speaker community**

Nếu bạn thấy project hữu ích, hãy star ⭐ repository này!