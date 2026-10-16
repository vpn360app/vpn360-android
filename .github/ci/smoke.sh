#!/bin/bash
# Smoke test in the Android emulator: install, open, import a subscription by deep link.
set -x
D="$GITHUB_WORKSPACE/diag"; mkdir -p "$D"
APK=$(ls apks/VPN360-x86_64.apk 2>/dev/null || ls apks/VPN360-universal.apk)
adb install -r -g "$APK" > "$D/install.txt" 2>&1
adb shell dumpsys package com.vpn360.app | grep -E "versionName|versionCode|firstInstallTime" > "$D/package.txt"
adb logcat -c
adb shell cmd locale set-app-locales com.vpn360.app --locales es-PE >> "$D/install.txt" 2>&1 || true
adb shell monkey -p com.vpn360.app -c android.intent.category.LAUNCHER 1
sleep 12
adb exec-out screencap -p > "$D/01-abrir-app.png"
adb shell am start -a android.intent.action.VIEW -d "vpn360://install-sub?url=https%3A%2F%2Fexample.com%2Fs%2Fprueba#VPN%20360" > "$D/deeplink.txt" 2>&1
sleep 10
adb exec-out screencap -p > "$D/02-importar-suscripcion.png"
adb shell input keyevent KEYCODE_BACK
sleep 2
adb exec-out screencap -p > "$D/03-pantalla.png"
echo "pid: $(adb shell pidof com.vpn360.app)" >> "$D/package.txt"
adb logcat -d -v time > "$D/logcat-full.txt" 2>&1
grep -E "AndroidRuntime|FATAL|com\.vpn360|UrlScheme" "$D/logcat-full.txt" | tail -n 300 > "$D/logcat.txt"
rm -f "$D/logcat-full.txt"
exit 0
