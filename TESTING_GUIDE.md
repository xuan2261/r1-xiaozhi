# Hướng Dẫn Test App Xiaozhi trên Phicomm R1

## Tổng Quan Project

**Repository**: https://github.com/xuan2261/r1-xiaozhi  
**Latest Commit**: `7e81b7d` - UX improvements with copy button  
**Build Status**: ⏳ Đang build trên GitHub Actions

### Các Commits Quan Trọng

1. **`9abb9cd`** - ESP32-based pairing với error handling
2. **`4c52fcf`** - findViewById type cast fix (API 22)
3. **`7e81b7d`** - UX improvements (Copy button + instructions)

## Chuẩn Bị

### 1. Download APK

Sau khi GitHub Actions build xong, download từ:
```
https://github.com/xuan2261/r1-xiaozhi/actions
→ Chọn workflow run mới nhất
→ Download "app-debug" artifact
→ Unzip để lấy app-debug.apk
```

### 2. Cài Đặt Trên R1

**Qua ADB**:
```bash
# Connect R1 qua USB hoặc WiFi
adb devices

# Install APK
adb install -r app-debug.apk

# Launch app
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

**Qua USB**:
1. Copy APK vào USB drive
2. Cắm USB vào R1
3. Dùng file manager install APK

### 3. Permissions

App cần các permissions sau (auto-grant trong code):
- `INTERNET` - Kết nối WebSocket
- `ACCESS_WIFI_STATE` - Lấy MAC address
- `ACCESS_NETWORK_STATE` - Check connectivity
- `RECORD_AUDIO` - Voice recognition
- `WRITE_EXTERNAL_STORAGE` - Lưu audio cache

## Test Cases

### Test 1: Pairing Flow (Happy Path) ✅

**Mục tiêu**: Verify pairing thành công với đúng flow

**Steps**:
1. Launch app lần đầu
2. Observe pairing code hiển thị (VD: "DD EE FF")
3. Click "📋 Sao Chép Mã"
4. Verify toast "✓ Đã sao chép mã: DDEEFF"
5. Mở browser → `https://console.xiaozhi.ai`
6. Login → Chọn agent → "Add Device"
7. Paste code "DDEEFF" → Submit
8. Wait for "Device added successfully"
9. Quay lại app → Click "Connect"
10. Wait for status "✓ Đã ghép nối thành công!"

**Expected**:
- ✅ Code hiển thị đúng format (6 ký tự uppercase)
- ✅ Copy hoạt động
- ✅ Console accept code
- ✅ WebSocket connect thành công
- ✅ Authorize handshake success (code=0)
- ✅ UI update: "Đã ghép nối"
- ✅ Instructions ẩn đi
- ✅ Connect button disabled

**Logs to check**:
```bash
adb logcat | grep -E "MainActivity|PairingCode|XiaozhiConnection"
```

Expect:
```
=== PAIRING CODE DEBUG ===
Device ID: AABBCCDDEEFF
Pairing Code: DDEEFF
=========================
WebSocket connected
Sending Authorize handshake: {...}
Pairing SUCCESS!
Device marked as paired
```

### Test 2: Wrong Flow (Connect Trước) ❌

**Mục tiêu**: Verify error handling khi user connect trước khi add code

**Steps**:
1. Launch app
2. Observe pairing code
3. **KHÔNG** add vào console
4. Click "Connect" ngay lập tức
5. Observe status

**Expected**:
- ❌ Server reject với error code
- ✅ App hiển thị: "✗ Ghép nối thất bại: Mã xác thực không hợp lệ"
- ✅ Retry logic kick in (nếu error retryable)
- ✅ Connect button re-enabled sau fail

**Logs**:
```
Pairing FAILED: code=xxx (Invalid pairing code)
```

### Test 3: Reset Pairing 🔄

**Mục tiêu**: Verify reset hoạt động đúng

**Steps**:
1. Sau khi paired thành công (Test 1)
2. Click "Reset Pairing"
3. Observe UI changes

**Expected**:
- ✅ WebSocket disconnect
- ✅ Pairing status reset
- ✅ Pairing code hiển thị lại
- ✅ Instructions visible lại
- ✅ Copy button visible lại
- ✅ Connect button enabled
- ✅ Toast: "Đã reset - Vui lòng ghép nối lại"

### Test 4: Network Issues 🌐

**Mục tiêu**: Verify retry logic hoạt động

**Steps**:
1. Disable WiFi trên R1
2. Click "Connect"
3. Observe behavior

**Expected**:
- ❌ Connection fails
- ✅ Retry #1 sau 2s
- ✅ Retry #2 sau 4s
- ✅ Retry #3 sau 8s
- ❌ Give up sau 3 retries
- ✅ Error message: "Máy chủ không phản hồi"

**Logs**:
```
WebSocket error: ...
Scheduling reconnect #1 in 2000ms
Retrying connection...
Max retries reached. Giving up.
```

### Test 5: Auto-Reconnect 🔌

**Mục tiêu**: Verify auto-reconnect khi paired

**Steps**:
1. Sau khi paired (Test 1)
2. Disable WiFi 10s
3. Enable WiFi
4. Observe behavior

**Expected**:
- ⚠ Status: "Mất kết nối"
- 🔄 Auto retry kick in
- ✅ Reconnect thành công
- ✅ Status: "Đã kết nối - Đang xác thực..."
- ✅ Auto send Authorize handshake
- ✅ Status: "✓ Đã ghép nối thành công!"

### Test 6: Device ID Consistency 🔑

**Mục tiêu**: Verify device ID stable across reboots

**Steps**:
1. Note pairing code lần 1 (VD: DDEEFF)
2. Force stop app: `adb shell am force-stop com.phicomm.r1.xiaozhi`
3. Relaunch app
4. Note pairing code lần 2

**Expected**:
- ✅ Code giống nhau cả 2 lần (cached in SharedPreferences)
- ✅ Device ID stable

**Logs**:
```
Device ID: AABBCCDDEEFF  // Lần 1
Device ID: AABBCCDDEEFF  // Lần 2 - SAME!
```

### Test 7: Voice Recognition Integration 🎤

**Mục tiêu**: Verify voice service hoạt động

**Steps**:
1. Sau khi paired
2. Say wake word (nếu có)
3. Hoặc test manual trigger

**Expected**:
- ✅ VoiceRecognitionService active
- ✅ Audio capture hoạt động
- ✅ Send voice data qua WebSocket
- ✅ Receive response từ Xiaozhi
- ✅ AudioPlaybackService play response

**Note**: Test này cần thiết bị thật có microphone

### Test 8: HTTP Server 🌐

**Mục tiêu**: Verify HTTP server expose data

**Steps**:
1. App running
2. Get R1 IP: `adb shell ip addr show wlan0`
3. Trên PC cùng network: `curl http://[R1_IP]:8088/status`

**Expected**:
```json
{
  "status": "paired",
  "device_id": "AABBCCDDEEFF",
  "pairing_code": "DDEEFF",
  "connected": true
}
```

### Test 9: LED Control 💡

**Mục tiêu**: Verify LED service (nếu R1 có LED)

**Expected**:
- 🔴 RED: Not paired
- 🟢 GREEN: Paired successfully
- 🔵 BLUE: Listening
- 🟡 YELLOW: Processing

**Note**: Phụ thuộc vào hardware R1

### Test 10: Persistence Qua Reboot 🔄

**Mục tiêu**: Verify app tự start sau reboot

**Steps**:
1. Paired thành công
2. Reboot R1: `adb reboot`
3. Wait boot xong
4. Check app status

**Expected**:
- ✅ BootReceiver trigger
- ✅ Services auto-start
- ✅ Auto reconnect WebSocket
- ✅ Pairing status preserved

## Debug Tools

### LogCat Filters

**Tất cả logs**:
```bash
adb logcat | grep -E "Xiaozhi|Pairing|MainActivity"
```

**Chỉ errors**:
```bash
adb logcat *:E | grep Xiaozhi
```

**Connection events**:
```bash
adb logcat | grep "XiaozhiConnection"
```

**Pairing debug**:
```bash
adb logcat | grep "PAIRING CODE DEBUG"
```

### Clear App Data

Reset hoàn toàn app:
```bash
adb shell pm clear com.phicomm.r1.xiaozhi
```

### Check Services Running

```bash
adb shell dumpsys activity services | grep xiaozhi
```

### Network Traffic

Monitor WebSocket:
```bash
adb shell tcpdump -i wlan0 -w /sdcard/capture.pcap
# Analyze với Wireshark
```

## Expected Build Artifacts

Sau khi GitHub Actions build xong, kiểm tra:

1. **APK Size**: ~2-3 MB
2. **Min SDK**: 22 (Android 5.1)
3. **Target SDK**: 22
4. **Permissions**: 5 permissions declared
5. **Services**: 5 background services
6. **Activities**: 2 (MainActivity, SettingsActivity)

## Known Issues & Workarounds

### Issue 1: MAC Address 02:00:00:00:00:00

**Triệu chứng**: Fake MAC trên một số thiết bị

**Workaround**: Fallback sang Android ID (already implemented)

**Verify**:
```bash
adb logcat | grep "Fake/invalid MAC detected"
```

### Issue 2: WebSocket Connection Refused

**Triệu chứng**: Cannot connect to xiaozhi.me

**Check**:
```bash
# Test từ R1
adb shell ping xiaozhi.me
adb shell curl -I https://xiaozhi.me
```

**Workaround**: Check firewall, proxy settings

### Issue 3: Pairing Code Expired

**Triệu chứng**: Server reject với "Code expired"

**Root cause**: Code chỉ valid 10 phút

**Workaround**: Generate code mới (reset pairing)

## Performance Benchmarks

**Expected metrics**:
- Cold start: <2s
- Pairing code generation: <100ms
- WebSocket connect: <1s
- Authorize handshake: <500ms
- Total pairing time: <5s

**Monitor với**:
```bash
adb shell dumpsys gfxinfo com.phicomm.r1.xiaozhi
```

## Security Checklist

- [x] No hardcoded credentials
- [x] HTTPS/WSS only
- [x] Permissions justified
- [x] No plaintext storage
- [x] Proper error messages (no info leakage)

## Next Steps

Sau khi test xong:

1. **Report bugs**: Tạo GitHub Issues với logs đầy đủ
2. **Performance tuning**: Nếu có bottlenecks
3. **Feature requests**: Theo user feedback
4. **Documentation**: Update README với real-world findings

## Support

**Issues**: https://github.com/xuan2261/r1-xiaozhi/issues  
**Docs**: Xem `README.md`, `HUONG_DAN_CAI_DAT.md`  
**Debug**: Xem `PAIRING_DEBUG_GUIDE.md`

---

**Happy Testing! 🚀**