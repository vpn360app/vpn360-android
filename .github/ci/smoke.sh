#!/bin/bash
# Smoke test in the Android emulator (Spanish locale): welcome pages, plans, simple home,
# access-code dialog, language switch (es/en/ru), import by link, connect, menu, advanced mode.
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

# tap <label> <fallback x%> <fallback y%>: finds a text or content-desc on screen and taps it.
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$D/ui.xml" >/dev/null 2>&1
  local B
  B=$(grep -o "\(text\|content-desc\)=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$D/ui.xml" 2>/dev/null \
      | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
  if [ -n "$B" ]; then
    X=$(echo "$B" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \3/' | awk '{print int(($1+$2)/2)}')
    Y=$(echo "$B" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2 \4/' | awk '{print int(($1+$2)/2)}')
    echo "tap '$1' found at $X,$Y" >> "$D/taps.txt"
  else
    X=$((W * $2 / 100)); Y=$((H * $3 / 100))
    echo "tap '$1' NOT found, fallback $X,$Y" >> "$D/taps.txt"
  fi
  adb shell input tap "$X" "$Y"
}
texts() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$D/ui.xml" >/dev/null 2>&1
  { echo "== $1"; grep -o '\(text\|content-desc\)="[^"]\{2,\}"' "$D/ui.xml" | head -40; } >> "$D/texts.txt"
}

adb logcat -c
adb shell monkey -p com.vpn360.app -c android.intent.category.LAUNCHER 1
sleep 12
shot 01-bienvenida; texts bienvenida
# swipe through the benefit pages to the plans
for n in 02 03 04 05; do
  adb shell input swipe $((W*85/100)) $((H/2)) $((W*15/100)) $((H/2)) 300; sleep 2; shot $n-pagina
done
texts planes
adb shell input swipe $((W/2)) $((H*70/100)) $((W/2)) $((H*25/100)) 400; sleep 2; shot 06-planes-estudiante
# "Empezar" -> simple home without account
tap "Empezar" 50 86; sleep 4; shot 07-inicio-sin-cuenta; texts inicio
# access-code dialog
tap "Tengo un código de acceso" 50 93; sleep 2; shot 08-codigo
adb shell input text "ABCD2345"; sleep 1; tap "Activar" 75 60; sleep 2; shot 09-codigo-respuesta
tap "Cancelar" 55 60; sleep 2
# language: English, then Russian, then back to Spanish
tap "Idioma" 80 5; sleep 2; shot 10-idioma
tap "English" 50 45; sleep 5; shot 11-home-ingles; texts ingles
tap "Language" 80 5; sleep 2; tap "Русский" 50 50; sleep 5; shot 12-home-ruso; texts ruso
tap "Язык" 80 5; sleep 2; tap "Español" 50 40; sleep 5
# import a demo (non-working) server by link: the home switches to "has account"
adb shell am start -a android.intent.action.VIEW -d "vpn360://install-config?url=vless%3A%2F%2F00000000-0000-4000-8000-000000000000%40203.0.113.10%3A443%3Fencryption%3Dnone%26security%3Dreality%26sni%3Dwww.samsung.com%26fp%3Dchrome%26pbk%3DAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA%26type%3Dtcp%26flow%3Dxtls-rprx-vision%23Servidor%2520Demo" > "$D/deeplink.txt" 2>&1
sleep 8; shot 13-con-cuenta; texts con-cuenta
# connect: VPN permission prompt, accept, connected screen
tap "Conectar VPN" 50 45; sleep 3; shot 14-permiso-vpn
tap "Aceptar" 75 60; sleep 2; tap "OK" 75 60; sleep 8; shot 15-conectado; texts conectado
sleep 5; shot 16-conectado-timer
tap "Desconectar VPN" 50 45; sleep 4; shot 17-desconectado
# menu and advanced mode
tap "Menú" 94 5; sleep 2; shot 18-menu
tap "Modo avanzado" 40 30; sleep 4; shot 19-modo-avanzado
adb shell input keyevent KEYCODE_BACK; sleep 3; shot 20-volver-inicio

echo "pid: $(adb shell pidof com.vpn360.app)" >> "$D/package.txt"
adb logcat -d -v time > "$D/logcat-full.txt" 2>&1
grep -E "AndroidRuntime|FATAL|com\.vpn360|UrlScheme|VPN 360" "$D/logcat-full.txt" | tail -n 300 > "$D/logcat.txt"
rm -f "$D/logcat-full.txt" "$D/ui.xml"

# ---- Real connection with the test account (encrypted in the repo; never printed) ----
set +x
LINK=$(openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$GITHUB_WORKSPACE/.github/ci/test-link.enc" -pass env:KEYSTORE_PASSWORD 2>/dev/null)
R="$D/conexion-real.txt"
if [ -z "$LINK" ]; then
  echo "conexion real: no ejecutada (sin enlace de prueba)" > "$R"
  exit 0
fi
HOST=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.urlsplit(sys.argv[1]).hostname)' "$LINK")
UUID=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.urlsplit(sys.argv[1]).username)' "$LINK")
HOSTIP=$(python3 -c 'import socket,sys;print(socket.gethostbyname(sys.argv[1]))' "$HOST" 2>/dev/null)
myip() { adb shell "printf 'GET / HTTP/1.1\r\nHost: api.ipify.org\r\nConnection: close\r\n\r\n' | nc -w 15 api.ipify.org 80" 2>/dev/null | tr -d '\r' | tail -n 1; }
IP0=$(myip)
adb shell pm clear com.vpn360.app >/dev/null 2>&1; sleep 2
adb logcat -c
adb shell "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$LINK' -n com.vpn360.app/com.v2ray.ang.ui.UrlSchemeActivity" >/dev/null 2>&1
sleep 10; shot 21-real-cuenta
tap "Conectar VPN" 50 47; sleep 3
tap "Aceptar" 75 60; sleep 12; shot 22-real-conectado
IP1=$(myip)
{
  echo "antes de conectar: ip publica distinta al servidor = $([ -n "$IP0" ] && [ "$IP0" != "$HOSTIP" ] && echo si || echo no) (vacia=$([ -z "$IP0" ] && echo si || echo no))"
  if [ -n "$IP1" ] && [ "$IP1" = "$HOSTIP" ]; then
    echo "conexion real: OK - con la VPN encendida, internet sale por el servidor VPN 360"
  else
    echo "conexion real: FALLO - ip con VPN vacia=$([ -z "$IP1" ] && echo si || echo no), coincide=no"
  fi
} > "$R"
tap "Desconectar VPN" 50 47; sleep 3
adb logcat -d -v time | grep -E "AndroidRuntime|FATAL|com\.vpn360|StartCore|Xray|xray" | tail -n 200 > "$D/logcat-real.txt"
# never publish the server address or the account id
python3 - "$D" "$HOST" "$HOSTIP" "$UUID" <<'PY'
import os, sys
d, secrets = sys.argv[1], [s for s in sys.argv[2:] if s]
for n in os.listdir(d):
    p = os.path.join(d, n)
    if n.endswith((".txt", ".xml")):
        t = open(p, errors="replace").read()
        for s in secrets:
            t = t.replace(s, "***")
        open(p, "w").write(t)
PY
rm -f "$D/ui.xml"
exit 0
