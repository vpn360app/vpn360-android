#!/bin/bash
# Smoke test in the Android emulator: Spanish locale, install, open (welcome screen),
# scroll through plans, go to the main screen, import by deep link.
set -x
D="$GITHUB_WORKSPACE/diag"; mkdir -p "$D"
shot() { adb exec-out screencap -p > "$D/$1.png"; }

# Spanish (Peru) system language, like the students' phones
adb root >/dev/null 2>&1 && sleep 3
adb shell setprop persist.sys.locale es-PE
adb shell setprop ctl.restart zygote
sleep 5; adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 2; done
sleep 10
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0

APK=$(ls apks/VPN360-x86_64.apk 2>/dev/null || ls apks/VPN360-universal.apk)
adb install -r -g "$APK" > "$D/install.txt" 2>&1
adb shell dumpsys package com.vpn360.app | grep -E "versionName|versionCode" > "$D/package.txt"
SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
echo "screen $W x $H" >> "$D/package.txt"
adb logcat -c
adb shell monkey -p com.vpn360.app -c android.intent.category.LAUNCHER 1
sleep 15
shot 01-inicio
adb shell uiautomator dump /sdcard/ui0.xml >/dev/null 2>&1; adb pull /sdcard/ui0.xml "$D/ui0.xml" >/dev/null 2>&1
grep -o 'text="[^"]\{3,\}"' "$D/ui0.xml" | head -40 > "$D/texts-inicio.txt"
for n in 02 03 04 05; do
  adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400; sleep 2; shot $n-planes
done
# activation dialog
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) 300; sleep 2
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$D/ui-inicio.xml" >/dev/null 2>&1
B=$(grep -o 'text="Activar mi cuenta"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$D/ui-inicio.xml" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
if [ -n "$B" ]; then
  X=$(echo "$B" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \3/' | awk '{print int(($1+$2)/2)}')
  Y=$(echo "$B" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2 \4/' | awk '{print int(($1+$2)/2)}')
  adb shell input tap $X $Y; sleep 3; shot 06-activar-cuenta
  adb shell input keyevent KEYCODE_BACK; sleep 2
fi
# main screen
adb shell input keyevent KEYCODE_BACK; sleep 3; shot 07-pantalla-principal
adb shell am start -a android.intent.action.VIEW -d "vpn360://install-sub?url=https%3A%2F%2Fexample.com%2Fs%2Fprueba#VPN%20360" > "$D/deeplink.txt" 2>&1
sleep 8; shot 08-tras-enlace
echo "pid: $(adb shell pidof com.vpn360.app)" >> "$D/package.txt"
adb logcat -d -v time > "$D/logcat-full.txt" 2>&1
grep -E "AndroidRuntime|FATAL|com\.vpn360|UrlScheme" "$D/logcat-full.txt" | tail -n 300 > "$D/logcat.txt"
rm -f "$D/logcat-full.txt" "$D/ui-inicio.xml" "$D/ui0.xml"
exit 0
