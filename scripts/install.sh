#!/bin/bash

# Script tự động cài đặt Xiaozhi lên Phicomm R1
# Sử dụng: ./install.sh [IP_ADDRESS]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PACKAGE_NAME="com.phicomm.r1.xiaozhi"
APK_PATH="../R1XiaozhiApp/app/build/outputs/apk/release/app-release.apk"
R1_IP="${1:-}"

echo -e "${GREEN}=== Xiaozhi R1 Installer ===${NC}\n"

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if ADB is installed
if ! command -v adb &> /dev/null; then
    print_error "ADB not found. Please install Android SDK Platform Tools."
    exit 1
fi

print_info "ADB found: $(adb version | head -1)"

# Connect to R1 if IP provided
if [ -n "$R1_IP" ]; then
    print_info "Connecting to R1 at $R1_IP:5555..."
    adb connect "$R1_IP:5555"
    sleep 2
fi

# Check device connection
if ! adb devices | grep -q "device$"; then
    print_error "No device connected. Please connect R1 via USB or WiFi."
    print_info "For WiFi: adb connect <R1_IP>:5555"
    exit 1
fi

print_info "Device connected: $(adb devices | grep device$ | awk '{print $1}')"

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    print_error "APK not found at: $APK_PATH"
    print_info "Please build the APK first: ./gradlew assembleRelease"
    exit 1
fi

print_info "APK found: $APK_PATH"

# Step 1: Disable Phicomm system apps
print_info "\n=== Step 1: Disabling Phicomm system apps ==="
PHICOMM_APPS=(
    "com.phicomm.speaker.player"
    "com.phicomm.speaker.device"
    "com.phicomm.speaker.airskill"
    "com.phicomm.speaker.exceptionreporter"
)

for app in "${PHICOMM_APPS[@]}"; do
    print_info "Disabling $app..."
    adb shell pm hide "$app" 2>/dev/null || print_warn "Could not disable $app (might not exist)"
done

# Step 2: Uninstall old version if exists
print_info "\n=== Step 2: Checking for old installation ==="
if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    print_info "Old version found. Uninstalling..."
    adb uninstall "$PACKAGE_NAME" || print_warn "Could not uninstall old version"
else
    print_info "No old version found."
fi

# Step 3: Install APK
print_info "\n=== Step 3: Installing Xiaozhi APK ==="
print_info "Pushing APK to device..."
adb push "$APK_PATH" /data/local/tmp/xiaozhi.apk

print_info "Installing APK..."
adb shell pm install -t -r /data/local/tmp/xiaozhi.apk

if [ $? -eq 0 ]; then
    print_info "APK installed successfully!"
else
    print_error "Failed to install APK"
    exit 1
fi

# Step 4: Grant permissions
print_info "\n=== Step 4: Granting permissions ==="
PERMISSIONS=(
    "android.permission.RECORD_AUDIO"
    "android.permission.WRITE_EXTERNAL_STORAGE"
    "android.permission.READ_EXTERNAL_STORAGE"
    "android.permission.ACCESS_NETWORK_STATE"
    "android.permission.INTERNET"
)

for perm in "${PERMISSIONS[@]}"; do
    print_info "Granting $perm..."
    adb shell pm grant "$PACKAGE_NAME" "$perm" 2>/dev/null || print_warn "Could not grant $perm"
done

# Step 5: Check root access
print_info "\n=== Step 5: Checking root access (for LED control) ==="
if adb shell su -c "id" 2>/dev/null | grep -q "uid=0"; then
    print_info "Root access available! LED control will work."
else
    print_warn "No root access. LED control will be disabled."
    print_info "To enable LED: Flash custom ROM or root your R1"
fi

# Step 6: Start services
print_info "\n=== Step 6: Starting Xiaozhi services ==="
print_info "Starting MainActivity..."
adb shell am start -n "$PACKAGE_NAME/.ui.MainActivity"

sleep 2

print_info "Starting background services..."
adb shell am startservice "$PACKAGE_NAME/.service.XiaozhiConnectionService"
adb shell am startservice "$PACKAGE_NAME/.service.VoiceRecognitionService"
adb shell am startservice "$PACKAGE_NAME/.service.LEDControlService"

# Step 7: Configure auto-start
print_info "\n=== Step 7: Configuring auto-start ==="
adb shell pm enable "$PACKAGE_NAME/.receiver.BootReceiver"
print_info "Auto-start enabled"

# Step 8: Disable battery optimization
print_info "\n=== Step 8: Disabling battery optimization ==="
adb shell dumpsys deviceidle whitelist +"$PACKAGE_NAME" 2>/dev/null || print_warn "Could not disable battery optimization"

# Cleanup
print_info "\n=== Cleanup ==="
adb shell rm /data/local/tmp/xiaozhi.apk
print_info "Temporary files removed"

# Final status
print_info "\n${GREEN}=== Installation Complete! ===${NC}"
echo ""
print_info "App Status:"
adb shell dumpsys package "$PACKAGE_NAME" | grep -E "(versionName|versionCode)" | head -2

echo ""
print_info "Running Services:"
adb shell dumpsys activity services | grep "$PACKAGE_NAME" | grep -v "ProcessRecord" | head -5

echo ""
print_info "${GREEN}Next Steps:${NC}"
echo "1. Configure Xiaozhi endpoint (Cloud or Self-hosted)"
echo "2. Test wake word: Say '小智' (Xiao Zhi)"
echo "3. Check logs: adb logcat | grep Xiaozhi"
echo "4. Access web interface: http://<R1_IP>:8088"
echo ""
print_info "Enjoy your Xiaozhi-powered R1! 🎉"