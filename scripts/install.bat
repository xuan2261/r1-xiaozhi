@echo off
REM Script tự động cài đặt Xiaozhi lên Phicomm R1 (Windows)
REM Sử dụng: install.bat [IP_ADDRESS]

setlocal enabledelayedexpansion

echo === Xiaozhi R1 Installer (Windows) ===
echo.

set PACKAGE_NAME=com.phicomm.r1.xiaozhi
set APK_PATH=..\R1XiaozhiApp\app\build\outputs\apk\release\app-release.apk
set R1_IP=%1

REM Check if ADB is installed
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] ADB not found. Please install Android SDK Platform Tools.
    exit /b 1
)

echo [INFO] ADB found
adb version | findstr /C:"Android Debug Bridge"

REM Connect to R1 if IP provided
if not "%R1_IP%"=="" (
    echo [INFO] Connecting to R1 at %R1_IP%:5555...
    adb connect %R1_IP%:5555
    timeout /t 2 /nobreak >nul
)

REM Check device connection
adb devices | findstr /C:"device" >nul
if %errorlevel% neq 0 (
    echo [ERROR] No device connected. Please connect R1 via USB or WiFi.
    echo [INFO] For WiFi: adb connect [R1_IP]:5555
    exit /b 1
)

echo [INFO] Device connected

REM Check if APK exists
if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at: %APK_PATH%
    echo [INFO] Please build the APK first: gradlew assembleRelease
    exit /b 1
)

echo [INFO] APK found: %APK_PATH%

REM Step 1: Disable Phicomm system apps
echo.
echo === Step 1: Disabling Phicomm system apps ===
echo [INFO] Disabling com.phicomm.speaker.player...
adb shell pm hide com.phicomm.speaker.player 2>nul
echo [INFO] Disabling com.phicomm.speaker.device...
adb shell pm hide com.phicomm.speaker.device 2>nul
echo [INFO] Disabling com.phicomm.speaker.airskill...
adb shell pm hide com.phicomm.speaker.airskill 2>nul
echo [INFO] Disabling com.phicomm.speaker.exceptionreporter...
adb shell pm hide com.phicomm.speaker.exceptionreporter 2>nul

REM Step 2: Uninstall old version
echo.
echo === Step 2: Checking for old installation ===
adb shell pm list packages | findstr /C:"%PACKAGE_NAME%" >nul
if %errorlevel% equ 0 (
    echo [INFO] Old version found. Uninstalling...
    adb uninstall %PACKAGE_NAME%
) else (
    echo [INFO] No old version found.
)

REM Step 3: Install APK
echo.
echo === Step 3: Installing Xiaozhi APK ===
echo [INFO] Pushing APK to device...
adb push "%APK_PATH%" /data/local/tmp/xiaozhi.apk

echo [INFO] Installing APK...
adb shell pm install -t -r /data/local/tmp/xiaozhi.apk

if %errorlevel% equ 0 (
    echo [INFO] APK installed successfully!
) else (
    echo [ERROR] Failed to install APK
    exit /b 1
)

REM Step 4: Grant permissions
echo.
echo === Step 4: Granting permissions ===
echo [INFO] Granting RECORD_AUDIO...
adb shell pm grant %PACKAGE_NAME% android.permission.RECORD_AUDIO 2>nul
echo [INFO] Granting WRITE_EXTERNAL_STORAGE...
adb shell pm grant %PACKAGE_NAME% android.permission.WRITE_EXTERNAL_STORAGE 2>nul
echo [INFO] Granting READ_EXTERNAL_STORAGE...
adb shell pm grant %PACKAGE_NAME% android.permission.READ_EXTERNAL_STORAGE 2>nul

REM Step 5: Check root
echo.
echo === Step 5: Checking root access ===
adb shell su -c "id" 2>nul | findstr /C:"uid=0" >nul
if %errorlevel% equ 0 (
    echo [INFO] Root access available! LED control will work.
) else (
    echo [WARN] No root access. LED control will be disabled.
)

REM Step 6: Start services
echo.
echo === Step 6: Starting Xiaozhi services ===
echo [INFO] Starting MainActivity...
adb shell am start -n %PACKAGE_NAME%/.ui.MainActivity

timeout /t 2 /nobreak >nul

echo [INFO] Starting background services...
adb shell am startservice %PACKAGE_NAME%/.service.XiaozhiConnectionService
adb shell am startservice %PACKAGE_NAME%/.service.VoiceRecognitionService
adb shell am startservice %PACKAGE_NAME%/.service.LEDControlService

REM Step 7: Enable auto-start
echo.
echo === Step 7: Configuring auto-start ===
adb shell pm enable %PACKAGE_NAME%/.receiver.BootReceiver
echo [INFO] Auto-start enabled

REM Step 8: Disable battery optimization
echo.
echo === Step 8: Disabling battery optimization ===
adb shell dumpsys deviceidle whitelist +%PACKAGE_NAME% 2>nul

REM Cleanup
echo.
echo === Cleanup ===
adb shell rm /data/local/tmp/xiaozhi.apk
echo [INFO] Temporary files removed

REM Final status
echo.
echo === Installation Complete! ===
echo.
echo [INFO] App Status:
adb shell dumpsys package %PACKAGE_NAME% | findstr /C:"versionName" | findstr /V /C:"ProcessRecord"

echo.
echo [INFO] Next Steps:
echo 1. Configure Xiaozhi endpoint (Cloud or Self-hosted)
echo 2. Test wake word: Say '小智' (Xiao Zhi)
echo 3. Check logs: adb logcat ^| findstr Xiaozhi
echo 4. Access web interface: http://[R1_IP]:8088
echo.
echo [INFO] Enjoy your Xiaozhi-powered R1! 🎉

endlocal