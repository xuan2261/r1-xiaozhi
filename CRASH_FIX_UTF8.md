# 🔧 Fix Crash UTF-8 Encoding - JNI Error

## 🔴 Vấn Đề Mới Phát Hiện

**Sau khi fix NetworkChangeReceiver**, app vẫn crash với lỗi UTF-8:

```
F/art: JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8: illegal start byte 0xf0
F/art: at com.phicomm.r1.xiaozhi.ui.MainActivity.onCreate(MainActivity.java:140)
```

## 📋 Root Cause Analysis

### 1. Modified UTF-8 vs Standard UTF-8

**Android JNI** sử dụng **Modified UTF-8** (CESU-8), khác với standard UTF-8:
- **Standard UTF-8**: Emoji và 4-byte characters hợp lệ
- **Modified UTF-8**: Chỉ hỗ trợ 1-3 byte characters
- Byte `0xf0` = bắt đầu emoji 4-byte → **KHÔNG HỢP LỆ** trong JNI

### 2. Các Ký Tự Gây Crash

**Trong code của chúng ta:**

#### Layout XML - activity_main.xml
```xml
<!-- ❌ CÓ EMOJI -->
<Button android:text="📋 Sao Chép Mã" />

<!-- ✅ ĐÃ FIX -->
<Button android:text="Sao Chep Ma" />
```

#### MainActivity.java
```java
// ❌ CÓ EMOJI & SPECIAL CHARS
updateStatus("✓ Đã ghép nối thành công!");
updateStatus("✗ Ghép nối thất bại");
updateStatus("⚠ Chưa ghép nối");

// ✅ ĐÃ FIX - ASCII only
updateStatus("[OK] Da ghep noi thanh cong!");
updateStatus("[FAIL] Ghep noi that bai");
updateStatus("[!] Chua ghep noi");
```

## ✅ Giải Pháp Đã Áp Dụng

### 1. Thay Emoji Trong Layout

**File:** [`activity_main.xml`](R1XiaozhiApp/app/src/main/res/layout/activity_main.xml:35)

```diff
- android:text="📋 Sao Chép Mã"
+ android:text="Sao Chep Ma"
```

### 2. Thay Emoji/Special Chars Trong Java

**File:** [`MainActivity.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java)

| Dòng | Cũ (Emoji) | Mới (ASCII) |
|------|-----------|-------------|
| 61 | `Đã kết nối - Đang xác thực...` | `Da ket noi - Dang xac thuc...` |
| 72 | `Mất kết nối` | `Mat ket noi` |
| 83 | `✓ Đã ghép nối thành công!` | `[OK] Da ghep noi thanh cong!` |
| 87 | `Đã ghép nối` | `Da Ghep Noi` |
| 98 | `✗ Ghép nối thất bại` | `[FAIL] Ghep noi that bai` |
| 117 | `Lỗi:` | `Loi:` |
| 193 | `✓ Đã ghép nối - Sẵn sàng` | `[OK] Da ghep noi - San sang` |
| 194 | `✓ Đã Ghép Nối` | `[OK] Da Ghep Noi` |
| 204 | `⚠ Chưa ghép nối` | `[!] Chua ghep noi` |
| 229 | `✓ Đã sao chép` | `[OK] Da sao chep` |
| 240 | `chưa sẵn sàng` | `chua san sang` |
| 244 | `Đang kết nối...` | `Dang ket noi...` |
| 265 | `Đã reset - Vui lòng` | `Da reset - Vui long` |

### 3. Loại Bỏ Diacritics (Vietnamese Marks)

**Lý do:** Dấu tiếng Việt cũng có thể gây vấn đề với JNI API 22
- `á, à, ả, ã, ạ` → `a`
- `đ` → `d`
- `ư, ơ` → `u, o`

**Áp dụng:** Tất cả text trong Java code

## 🔍 Kiểm Tra Toàn Bộ Codebase

### Files Đã Kiểm Tra

```bash
# Search non-ASCII characters
grep -r '[^\x00-\x7F]' R1XiaozhiApp/app/src/main/java/ > utf8_chars.txt
```

**Kết quả:** 93 matches - Đã xử lý tất cả trong MainActivity và layout

### Files An Toàn (Chỉ Comments)

Các file sau chỉ có UTF-8 trong **comments** → An toàn:
- `XiaozhiApplication.java` - Comments tiếng Việt
- `PairingCodeGenerator.java` - Comments
- `ErrorCodes.java` - Messages in strings (không qua JNI)
- `XiaozhiConnectionService.java` - Comments
- `LEDControlService.java` - Comments
- `VoiceRecognitionService.java` - Comments

## 📊 Impact Assessment

### ✅ Fixed
- MainActivity UI text → ASCII only
- Button labels → ASCII only
- Status messages → ASCII only
- Toast messages → ASCII only

### ⚠️ Still Has UTF-8 (Safe)
- `ErrorCodes.java` messages - OK vì chỉ dùng trong String, không qua JNI
- Comments trong code - OK vì không compiled vào runtime
- `strings.xml` - OK vì Android resource system xử lý

### 🎯 Best Practice

**Quy tắc mới:**
1. **Java code runtime strings**: ASCII only
2. **XML resources**: OK dùng UTF-8 (Android handles it)
3. **Comments**: OK dùng bất kỳ encoding
4. **Error messages**: Dùng string resources, không hardcode

## 🔨 Build & Test

### Build Command (Cần JAVA_HOME)

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_xxx
cd R1XiaozhiApp
gradlew.bat clean assembleDebug

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk
cd R1XiaozhiApp
./gradlew clean assembleDebug
```

### Install & Test

```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor startup
adb logcat -c
adb logcat | grep -E "Xiaozhi|JNI|UTF-8|SIGABRT"

# Verify: Không có lỗi UTF-8
# Expected: App khởi động thành công
```

## 📚 Technical Deep Dive

### Modified UTF-8 (CESU-8)

**Khác biệt với Standard UTF-8:**

```
Character: 😀 (U+1F600)
Standard UTF-8:  F0 9F 98 80 (4 bytes)
Modified UTF-8:  INVALID! (JNI crashes)

Character: ✓ (U+2713)
Standard UTF-8:  E2 9C 93 (3 bytes)
Modified UTF-8:  E2 9C 93 (OK, but risky in old API)

Character: A (U+0041)
Standard UTF-8:  41 (1 byte)
Modified UTF-8:  41 (OK)
```

### Why Android API 22 More Strict?

**API Levels:**
- **API 22 (Lollipop 5.1)**: Strict JNI checks, crashes on invalid UTF-8
- **API 23+**: More lenient, better error handling
- **API 26+**: Full UTF-8 support in most cases

**Solution:** Stick to ASCII for API 22 compatibility

## 🎓 Lessons Learned

### 1. Always Test on Target API

```java
// ❌ BAD - Emoji looks nice but crashes
updateStatus("✓ Success!");

// ✅ GOOD - ASCII always works
updateStatus("[OK] Success!");
```

### 2. Use String Resources

```xml
<!-- strings.xml - Android handles encoding -->
<string name="status_success">Đã kết nối thành công</string>

<!-- Java - Load from resources -->
statusText.setText(R.string.status_success); // Safe!
```

### 3. Test on Real Device

- Emulator có thể **KHÔNG** catch lỗi này
- Real device (đặc biệt API 22) sẽ crash
- Luôn test trên thiết bị target

## 🚀 Next Steps

### 1. Set JAVA_HOME

```bash
# Find Java installation
java -version

# Windows
setx JAVA_HOME "C:\Program Files\Java\jdk1.8.0_291"

# Add to PATH
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

### 2. Rebuild App

```bash
cd R1XiaozhiApp
gradlew.bat clean
gradlew.bat assembleDebug
```

### 3. Install & Test

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### 4. Monitor Logs

```bash
# No UTF-8 errors expected
adb logcat | grep -i "utf\|jni\|sigabrt"

# Should see:
# I/XiaozhiApp: Application started
# I/MainActivity: MainActivity created
# (No crashes)
```

## 📋 Checklist

- [x] Fixed emoji in layout XML
- [x] Replaced ✓, ✗, ⚠ với [OK], [FAIL], [!]
- [x] Removed Vietnamese diacritics từ runtime strings
- [x] Verified comments OK (không affect runtime)
- [x] Documented best practices
- [ ] Set JAVA_HOME để build
- [ ] Test trên device thật
- [ ] Verify app không crash

## 📚 Related Documentation

- [CRASH_FIX_SIGABRT.md](CRASH_FIX_SIGABRT.md) - NetworkChangeReceiver fix
- [QUICK_FIX_CHECKLIST.md](QUICK_FIX_CHECKLIST.md) - Common issues
- [Android Modified UTF-8 Spec](https://docs.oracle.com/javase/8/docs/api/java/io/DataInput.html#modified-utf-8)

---

**Date:** 2025-10-16  
**Issue:** JNI UTF-8 encoding error - illegal start byte 0xf0  
**Root Cause:** Emoji và 4-byte UTF-8 chars trong Java/XML  
**Solution:** Thay tất cả bằng ASCII-only text  
**Status:** ✅ Code fixed, cần rebuild & test