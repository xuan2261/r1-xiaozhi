# Complete Testing Checklist - R1 Xiaozhi App

## 🎯 Quick Reference

**Date**: 2025-10-20  
**App Version**: 1.0 (after runtime permission fix)  
**Target Device**: Phicomm R1 (Android 5.1, API 22)

---

## ✅ Pre-Testing Setup

### Device Connection
- [ ] Device connected via ADB: `adb devices`
- [ ] Android version verified: `adb shell getprop ro.build.version.release` → 5.1
- [ ] Root access available: `adb shell su -c "id"` → uid=0(root)
- [ ] SELinux permissive: `adb shell getenforce` → Permissive

### App Installation
- [ ] APK built successfully
- [ ] APK installed: `adb install -r app-release.apk`
- [ ] Permissions granted via script: `scripts/install.bat` or `scripts/install.sh`
- [ ] App launches without crash

### Logging Setup
- [ ] Logcat cleared: `adb logcat -c`
- [ ] Logcat monitoring started: `adb logcat | grep -E "MainActivity|VoiceRecognition|LEDControl"`

---

## 🧪 Critical Tests (Must Pass)

### Test 1: App Startup Without Crash ⭐⭐⭐
- [ ] App starts from launcher
- [ ] Permission dialog shown (if first launch)
- [ ] All permissions granted
- [ ] UI loads completely
- [ ] No crash in logcat
- [ ] Status text shows: "Chua kich hoat - Bam 'Ket Noi' de bat dau"

**Logcat Check**:
```
MainActivity: === PERMISSION CHECK ===
MainActivity: RECORD_AUDIO: true
MainActivity: === INITIALIZING SERVICES ===
```

### Test 2: VoiceRecognitionService Starts Successfully ⭐⭐⭐
- [ ] Service created without crash
- [ ] RECORD_AUDIO permission granted
- [ ] AudioRecord initialized successfully
- [ ] Recording thread started
- [ ] Foreground notification visible

**Logcat Check**:
```
VoiceRecognition: RECORD_AUDIO permission: true
VoiceRecognition: === STARTING AUDIO RECORDING ===
VoiceRecognition: === RECORDING STARTED SUCCESSFULLY ===
```

### Test 3: Wake Word Detection Works ⭐⭐⭐
- [ ] Say "Hi Lili" or "Alexa" loudly
- [ ] Wake word detected in logcat
- [ ] LED changes to green (LISTENING state)
- [ ] No app crash
- [ ] Audio recording starts

**Logcat Check**:
```
VoiceRecognition: High energy detected, possible wake word: 1523.45
VoiceRecognition: Wake word detected!
LEDControl: State: LISTENING
```

### Test 4: Device Activation Flow ⭐⭐
- [ ] Tap "Ket Noi" button
- [ ] Activation code displayed (6 digits)
- [ ] Code copied to clipboard
- [ ] Enter code on https://xiaozhi.me/activate
- [ ] Activation completes successfully
- [ ] WebSocket connects
- [ ] Status shows: "Da kich hoat va ket noi thanh cong"

**Logcat Check**:
```
XiaozhiConnection: Activation code: 123456
XiaozhiConnection: === ACTIVATION SUCCESS ===
XiaozhiConnection: WebSocket connected
```

### Test 5: LED Control Works (Requires Root) ⭐⭐
- [ ] Root access verified
- [ ] LED path exists: `/sys/class/leds/multi_leds0/led_color`
- [ ] IDLE state: Blue LED (0x0066CC)
- [ ] LISTENING state: Green LED (0x00FF00)
- [ ] THINKING state: White LED (0xFFFFFF)
- [ ] SPEAKING state: Cyan LED (0x00FFFF)

**Manual Test**:
```bash
adb shell su -c "echo -n '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"
```

---

## 🔧 Functional Tests

### Test 6: Voice Command Recording
- [ ] Wake word triggers recording
- [ ] Speak command: "今天天气怎么样"
- [ ] Voice activity detected
- [ ] Silence detection works (after ~0.4s silence)
- [ ] Audio data sent to WebSocket
- [ ] LED changes to THINKING state

### Test 7: Audio Playback Response
- [ ] Server response received
- [ ] Audio playback starts
- [ ] LED changes to SPEAKING state
- [ ] Audio plays through speaker
- [ ] LED returns to IDLE after playback

### Test 8: App Lifecycle
- [ ] Press Home button (background app)
- [ ] Wait 30 seconds
- [ ] Return to app (foreground)
- [ ] Services still running
- [ ] WebSocket still connected
- [ ] Wake word detection still works

### Test 9: App Restart & Auto-Connect
- [ ] Force stop: `adb shell am force-stop com.phicomm.r1.xiaozhi`
- [ ] Restart: `adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity`
- [ ] App starts without crash
- [ ] Permissions already granted (no dialog)
- [ ] Services auto-start
- [ ] If activated, WebSocket auto-connects
- [ ] Status shows connected state

---

## 🐛 Error Handling Tests

### Test 10: No RECORD_AUDIO Permission
- [ ] Revoke permission: `adb shell pm revoke com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO`
- [ ] Restart app
- [ ] Permission dialog shown
- [ ] Grant permission
- [ ] Services start successfully
- [ ] No crash

### Test 11: No Internet Connection
- [ ] Disable WiFi: `adb shell svc wifi disable`
- [ ] Try to activate
- [ ] Error message shown: "Khong co ket noi mang"
- [ ] Enable WiFi: `adb shell svc wifi enable`
- [ ] Retry activation succeeds

### Test 12: No Root Access (LED Control)
- [ ] LED control disabled gracefully
- [ ] No crash
- [ ] Logcat shows: "No root access, LED control disabled"
- [ ] App still functional (voice recognition works)

---

## 📊 Performance Tests

### Test 13: CPU Usage
- [ ] Monitor CPU: `adb shell top | grep xiaozhi`
- [ ] Idle state: < 5% CPU
- [ ] Recording state: < 20% CPU
- [ ] No CPU spikes

### Test 14: Memory Usage
- [ ] Monitor memory: `adb shell dumpsys meminfo com.phicomm.r1.xiaozhi`
- [ ] Total memory: < 100MB
- [ ] No memory leaks over 1 hour

### Test 15: Battery Usage
- [ ] Monitor battery: `adb shell dumpsys batterystats | grep xiaozhi`
- [ ] Reasonable battery consumption
- [ ] No excessive wake locks

---

## 🔄 Long-Term Stability Tests

### Test 16: 24-Hour Stability
- [ ] App runs for 24 hours
- [ ] No crashes
- [ ] No service restarts
- [ ] WebSocket connection stable
- [ ] Wake word detection still works
- [ ] Memory usage stable

### Test 17: Multiple Wake Word Triggers
- [ ] Trigger wake word 100 times
- [ ] All triggers detected
- [ ] No crashes
- [ ] No memory leaks
- [ ] Response time consistent

---

## 📝 Test Results Summary

```
=== CRITICAL TESTS ===
Test 1: App Startup                        [ PASS / FAIL ]
Test 2: VoiceRecognitionService            [ PASS / FAIL ]
Test 3: Wake Word Detection                [ PASS / FAIL ]
Test 4: Device Activation                  [ PASS / FAIL ]
Test 5: LED Control                        [ PASS / FAIL ]

=== FUNCTIONAL TESTS ===
Test 6: Voice Command Recording            [ PASS / FAIL ]
Test 7: Audio Playback Response            [ PASS / FAIL ]
Test 8: App Lifecycle                      [ PASS / FAIL ]
Test 9: App Restart & Auto-Connect         [ PASS / FAIL ]

=== ERROR HANDLING TESTS ===
Test 10: No RECORD_AUDIO Permission        [ PASS / FAIL ]
Test 11: No Internet Connection            [ PASS / FAIL ]
Test 12: No Root Access                    [ PASS / FAIL ]

=== PERFORMANCE TESTS ===
Test 13: CPU Usage                         [ PASS / FAIL ]
Test 14: Memory Usage                      [ PASS / FAIL ]
Test 15: Battery Usage                     [ PASS / FAIL ]

=== STABILITY TESTS ===
Test 16: 24-Hour Stability                 [ PASS / FAIL ]
Test 17: Multiple Wake Word Triggers       [ PASS / FAIL ]

Overall Status: [ PASS / FAIL ]
```

---

## 🚀 Next Steps After Testing

### If All Tests Pass ✅
1. Document test results
2. Create release notes
3. Tag release version
4. Deploy to production
5. Monitor user feedback

### If Tests Fail ❌
1. Document failure details
2. Capture logcat output
3. Create bug report
4. Fix issues
5. Re-test

---

## 📚 Reference Commands

### Quick Install & Test
```bash
# Install and grant permissions
scripts/install.bat  # Windows
./scripts/install.sh # Linux/Mac

# Start app
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Monitor logs
adb logcat | grep -E "MainActivity|VoiceRecognition|LEDControl|XiaozhiConnection"
```

### Quick Debug
```bash
# Check service status
adb shell dumpsys activity services | grep xiaozhi

# Check permissions
adb shell dumpsys package com.phicomm.r1.xiaozhi | grep permission

# Force stop and restart
adb shell am force-stop com.phicomm.r1.xiaozhi
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### LED Manual Control
```bash
# Red
adb shell su -c "echo -n '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"

# Green
adb shell su -c "echo -n '7fff 00ff00' > /sys/class/leds/multi_leds0/led_color"

# Blue
adb shell su -c "echo -n '7fff 0066cc' > /sys/class/leds/multi_leds0/led_color"

# White
adb shell su -c "echo -n '7fff ffffff' > /sys/class/leds/multi_leds0/led_color"

# Off
adb shell su -c "echo -n '0000 000000' > /sys/class/leds/multi_leds0/led_color"
```

---

**Last Updated**: 2025-10-20  
**Status**: Ready for testing on R1 device

