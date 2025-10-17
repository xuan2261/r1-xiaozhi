# Hướng Dẫn Push Code Lên GitHub

## 📋 Tổng Quan

Sau khi hoàn thành việc áp dụng kiến trúc py-xiaozhi, bạn cần push code lên GitHub để kích hoạt GitHub Actions tự động build APK.

## 🔍 Kiểm Tra Trước Khi Push

### 1. Xem trạng thái Git
```bash
git status
```

Bạn sẽ thấy các files mới và đã sửa:
```
New files:
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/XiaozhiCore.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/EventBus.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/DeviceState.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/ListeningMode.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/StateChangedEvent.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/ConnectionEvent.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/MessageReceivedEvent.java
- PY_XIAOZHI_ANALYSIS.md
- IMPLEMENTATION_GUIDE.md
- PY_XIAOZHI_IMPLEMENTATION_SUMMARY.md
- GITHUB_PUSH_GUIDE.md

Modified files:
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/XiaozhiApplication.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java
- R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java
- R1XiaozhiApp/app/src/main/res/layout/activity_main.xml
- .github/workflows/test.yml
```

### 2. Kiểm tra .gitignore
```bash
cat .gitignore
```

Đảm bảo các file build artifacts không được commit:
```
# Android
*.apk
*.ap_
*.dex
*.class
local.properties
.gradle/
build/
captures/
.externalNativeBuild/
.cxx/

# IDE
.idea/
*.iml
.vscode/
*.swp
*.swo
*~
```

## 🚀 Push Code Lên GitHub

### Phương Án 1: Push Tất Cả Changes (Khuyến Nghị)

```bash
# 1. Add tất cả files mới và sửa đổi
git add .

# 2. Commit với message mô tả rõ ràng
git commit -m "feat: Apply py-xiaozhi architecture

- Implement XiaozhiCore singleton with state management
- Add EventBus for event-driven architecture
- Create DeviceState and ListeningMode enums
- Add connection methods (start/stop/abort listening)
- Implement keep_listening logic from py-xiaozhi
- Refactor XiaozhiConnectionService with Core integration
- Update MainActivity with event-driven UI
- Add GitHub Actions workflow for APK build
- Add comprehensive documentation

Based on: https://github.com/huangjunsen0406/py-xiaozhi"

# 3. Push lên GitHub
git push origin main
```

### Phương Án 2: Push Từng Nhóm Files

#### Step 1: Core Architecture
```bash
git add R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/core/
git add R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/events/
git commit -m "feat: Add core architecture and event system

- XiaozhiCore: Centralized state management singleton
- EventBus: Thread-safe event broadcasting
- DeviceState & ListeningMode enums
- Event classes for state, connection, and messages"
git push origin main
```

#### Step 2: Service Refactoring
```bash
git add R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/XiaozhiApplication.java
git add R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java
git commit -m "refactor: Integrate Core and EventBus in services

- Initialize XiaozhiCore in Application
- Add sendStartListening, sendStopListening, sendAbortSpeaking
- Implement keep_listening logic in TTS handling
- Replace callbacks with EventBus"
git push origin main
```

#### Step 3: UI Updates
```bash
git add R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java
git add R1XiaozhiApp/app/src/main/res/layout/activity_main.xml
git commit -m "refactor: Event-driven UI with automatic updates

- Subscribe to StateChangedEvent
- Update UI on main thread via EventBus
- Add state display TextView"
git push origin main
```

#### Step 4: CI/CD & Documentation
```bash
git add .github/workflows/test.yml
git add PY_XIAOZHI_ANALYSIS.md
git add IMPLEMENTATION_GUIDE.md
git add PY_XIAOZHI_IMPLEMENTATION_SUMMARY.md
git add GITHUB_PUSH_GUIDE.md
git commit -m "docs: Add CI/CD and comprehensive documentation

- Update GitHub Actions for APK builds
- Add py-xiaozhi analysis and implementation guides
- Document architecture comparison and improvements"
git push origin main
```

## 📊 Sau Khi Push

### 1. Kiểm Tra GitHub Actions

Truy cập: `https://github.com/YOUR_USERNAME/r1xiaozhi/actions`

Bạn sẽ thấy workflow "Code Quality & Tests" đang chạy với các jobs:
- ✅ Lint Check
- ✅ Unit Tests  
- ✅ Code Analysis
- ✅ Build APK

### 2. Xem Build Progress

Click vào workflow run để xem chi tiết:
```
Jobs:
├── lint (2-3 phút)
├── test (3-5 phút)
├── code-analysis (1-2 phút)
└── build (5-10 phút)
    ├── Build Debug APK ✓
    └── Build Release APK ✓
```

### 3. Download APK

Khi build thành công, scroll xuống "Artifacts" section:
- 📦 **xiaozhi-debug-apk** (retain 30 days)
- 📦 **xiaozhi-release-apk** (retain 90 days)

Click để download và test trên thiết bị.

## ⚠️ Troubleshooting

### Lỗi: Permission Denied
```bash
# Solution: Set executable permission
chmod +x R1XiaozhiApp/gradlew
git add R1XiaozhiApp/gradlew
git commit -m "fix: Make gradlew executable"
git push origin main
```

### Lỗi: Build Failed - Missing Dependencies
```bash
# Check build.gradle
cat R1XiaozhiApp/app/build.gradle

# Ensure all dependencies are correct
# Re-run workflow sau khi fix
```

### Lỗi: Git Push Rejected
```bash
# Pull latest changes first
git pull origin main --rebase

# Resolve conflicts if any
git add .
git rebase --continue

# Push again
git push origin main
```

### Lỗi: Lint Errors
```bash
# Run lint locally first
cd R1XiaozhiApp
./gradlew lintDebug

# View report
open app/build/reports/lint-results-debug.html

# Fix issues and commit
```

## 🔧 Local Testing Trước Khi Push

### 1. Build Locally
```bash
cd R1XiaozhiApp

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Tests
```bash
# Unit tests
./gradlew testDebugUnitTest

# View report
open app/build/reports/tests/testDebugUnitTest/index.html
```

### 3. Install on Device
```bash
# List connected devices
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or if already installed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Testing on Device

### 1. Chuẩn Bị
- Phicomm R1 device connected
- USB debugging enabled
- Android SDK installed

### 2. Install và Test
```bash
# Install
adb install -r R1XiaozhiApp/app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep XiaozhiCore

# Test connection
# 1. Open app
# 2. Connect to Xiaozhi server
# 3. Test voice recognition
# 4. Verify state changes
```

### 3. Verify Implementation
- ✅ State changes logged correctly
- ✅ EventBus posts events
- ✅ UI updates automatically
- ✅ Keep listening works
- ✅ Connection methods functional

## 📚 Documentation Links

- [PY_XIAOZHI_ANALYSIS.md](PY_XIAOZHI_ANALYSIS.md) - Chi tiết phân tích py-xiaozhi
- [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) - Hướng dẫn implementation
- [PY_XIAOZHI_IMPLEMENTATION_SUMMARY.md](PY_XIAOZHI_IMPLEMENTATION_SUMMARY.md) - Tổng kết
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - Hướng dẫn testing

## ✅ Checklist Trước Push

- [ ] Kiểm tra git status
- [ ] Verify .gitignore
- [ ] Build thành công locally
- [ ] Tests pass
- [ ] Lint không có lỗi
- [ ] Review changes
- [ ] Write clear commit message
- [ ] Push lên GitHub
- [ ] Verify GitHub Actions
- [ ] Download và test APK

## 🎯 Expected Results

Sau khi push và build thành công:
1. ✅ Workflow "Code Quality & Tests" status: PASSED
2. ✅ APK artifacts available for download
3. ✅ No lint errors or warnings
4. ✅ All tests passing
5. ✅ App installs and runs on device
6. ✅ py-xiaozhi features working correctly

---

**Next**: Sau khi push, theo dõi GitHub Actions và download APK để test!