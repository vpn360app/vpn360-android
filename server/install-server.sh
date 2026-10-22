#!/usr/bin/env bash
# VPN 360 — complete install on a NEW server (Ubuntu 22.04/24.04, run as root).
#
# What it sets up:
#   1. 3x-ui panel, reachable ONLY from the server itself (127.0.0.1); from your PC you open it
#      through an SSH tunnel. Nothing of the panel is visible from the internet.
#   2. One VLESS/REALITY inbound on TCP 443 ("VPN-Estudiantes-443") with a first test account.
#   3. The UDP route (Hysteria2) on UDP 443, through server/install-udp.sh.
#   4. Firewall: only SSH, TCP 443 and UDP 443 are open.
#
# Run:
#   bash <(curl -fsSL https://raw.githubusercontent.com/vpn360app/vpn360-android/main/server/install-server.sh)
# Show the summary again:  cat /etc/vpn360/resumen.txt
set -euo pipefail

REPO_RAW=https://raw.githubusercontent.com/vpn360app/vpn360-android/main/server
XUI_INSTALL=https://raw.githubusercontent.com/MHSanaei/3x-ui/master/install.sh
PORT="${PORT:-443}"
SNI="${SNI:-www.samsung.com}"
INBOUND_NAME="VPN-Estudiantes-443"
TEST_USER="${TEST_USER:-prueba-1}"
DIR=/etc/vpn360

say()  { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || fail "Ejecuta este comando como root."
command -v systemctl >/dev/null || fail "Este servidor no usa systemd."
command -v apt-get >/dev/null || fail "Este comando es para Ubuntu/Debian."
if [ -f "$DIR/resumen.txt" ]; then
  say "Este servidor ya fue instalado. Resumen guardado:"; cat "$DIR/resumen.txt"; exit 0
fi

say "1/8 Programas base"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq >/dev/null
apt-get install -y -qq curl openssl ufw jq qrencode ca-certificates tar >/dev/null
mkdir -p "$DIR"; chmod 700 "$DIR"

say "2/8 Instalando el panel 3x-ui (solo visible desde el propio servidor)"
if [ ! -x /usr/local/x-ui/x-ui ]; then
  XUI_NONINTERACTIVE=1 XUI_SSL_MODE=none bash <(curl -fsSL "$XUI_INSTALL") >"$DIR/xui-install.log" 2>&1 \
    || { tail -20 "$DIR/xui-install.log"; fail "La instalación del panel falló (detalle arriba)."; }
fi
[ -f /etc/x-ui/install-result.env ] || fail "El panel no dejó sus credenciales en /etc/x-ui/install-result.env."
# shellcheck disable=SC1091
. /etc/x-ui/install-result.env
/usr/local/x-ui/x-ui setting -listenIP 127.0.0.1 >/dev/null
systemctl restart x-ui
PANEL="http://127.0.0.1:${XUI_PANEL_PORT}/${XUI_WEB_BASE_PATH}"
for _ in $(seq 1 30); do
  curl -fsS -o /dev/null -H "Authorization: Bearer $XUI_API_TOKEN" "$PANEL/panel/api/inbounds/list" 2>/dev/null && break
  sleep 1
done
curl -fsS -o /dev/null -H "Authorization: Bearer $XUI_API_TOKEN" "$PANEL/panel/api/inbounds/list" \
  || fail "El panel no responde en $PANEL."
echo "Panel activo en 127.0.0.1:${XUI_PANEL_PORT}."

say "3/8 Claves REALITY y cuenta de prueba"
XRAY=$(ls /usr/local/x-ui/bin/xray-linux-* | head -1)
KEYS="$("$XRAY" x25519)"
PRIV=$(echo "$KEYS" | awk -F': ' '/^PrivateKey/{print $2}')
PBK=$(echo "$KEYS" | awk -F': ' '/PublicKey|^Password/{print $2}' | head -1)
[ -n "$PRIV" ] && [ -n "$PBK" ] || fail "No se pudieron generar las claves REALITY."
SID=$(openssl rand -hex 4)
UUID=$("$XRAY" uuid)
SUBID=$(openssl rand -hex 8)
printf 'PBK=%s\n' "$PBK" > "$DIR/reality.env"; chmod 600 "$DIR/reality.env"

say "4/8 Creando la entrada $INBOUND_NAME en TCP $PORT"
SETTINGS=$(jq -cn --arg id "$UUID" --arg email "$TEST_USER" --arg sub "$SUBID" '{
  clients: [{id:$id, flow:"xtls-rprx-vision", email:$email, limitIp:2, totalGB:0, expiryTime:0,
             enable:true, tgId:"", subId:$sub, comment:"Cuenta de prueba", reset:0}],
  decryption:"none", fallbacks:[] }')
STREAM=$(jq -cn --arg sni "$SNI" --arg priv "$PRIV" --arg pbk "$PBK" --arg sid "$SID" '{
  network:"tcp", security:"reality", externalProxy:[],
  realitySettings:{show:false, xver:0, target:($sni+":443"), serverNames:[$sni], privateKey:$priv,
    minClientVer:"", maxClientVer:"", maxTimediff:0, shortIds:[$sid], mldsa65Seed:"",
    settings:{publicKey:$pbk, fingerprint:"chrome", serverName:"", spiderX:"/", mldsa65Verify:""}},
  tcpSettings:{acceptProxyProtocol:false, header:{type:"none"}} }')
SNIFF=$(jq -cn '{enabled:true, destOverride:["http","tls","quic","fakedns"], metadataOnly:false, routeOnly:false}')
BODY=$(jq -cn --arg remark "$INBOUND_NAME" --argjson port "$PORT" --arg s "$SETTINGS" --arg st "$STREAM" --arg sn "$SNIFF" '{
  up:0, down:0, total:0, remark:$remark, enable:true, expiryTime:0, listen:"", port:$port,
  protocol:"vless", settings:$s, streamSettings:$st, sniffing:$sn }')
RESP=$(curl -fsS -X POST -H "Authorization: Bearer $XUI_API_TOKEN" -H "Content-Type: application/json" \
  --data "$BODY" "$PANEL/panel/api/inbounds/add") || fail "No se pudo crear la entrada en el panel."
echo "$RESP" | jq -e '.success == true' >/dev/null || { echo "$RESP" | head -c 400; echo; fail "El panel rechazó la entrada."; }
INBOUND_ID=$(echo "$RESP" | jq -r '.obj.id')
echo "Entrada creada (id $INBOUND_ID)."

say "5/8 Ruta UDP (Hysteria2) en UDP $PORT"
PORT="$PORT" bash <(curl -fsSL "$REPO_RAW/install-udp.sh") >"$DIR/udp-install.log" 2>&1 \
  || { tail -20 "$DIR/udp-install.log"; fail "La ruta UDP falló (detalle arriba)."; }
HY2=$(cat /etc/vpn360-udp/link.txt)
echo "Ruta UDP activa."

say "6/8 Red: BBR"
cat > /etc/sysctl.d/98-vpn360-bbr.conf <<'EOF'
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
EOF
sysctl -q -p /etc/sysctl.d/98-vpn360-bbr.conf || true

say "7/8 Firewall: solo SSH, TCP $PORT y UDP $PORT"
SSH_PORT=$(grep -Ei '^\s*Port\s+[0-9]+' /etc/ssh/sshd_config 2>/dev/null | awk '{print $2}' | head -1)
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow "${SSH_PORT:-22}"/tcp >/dev/null
ufw allow "$PORT"/tcp >/dev/null
ufw allow "$PORT"/udp >/dev/null
ufw --force enable >/dev/null
echo "Firewall activo. El panel (puerto ${XUI_PANEL_PORT}) NO es visible desde internet."

say "8/8 Resumen"
IP="${PUBLIC_IP:-}"
[ -n "$IP" ] || IP="$(curl -fsS4 --max-time 8 https://api.ipify.org || true)"
[ -n "$IP" ] || IP="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')"
[ -n "$IP" ] || fail "No se pudo saber la IP pública. Repite con: PUBLIC_IP=x.x.x.x y el mismo comando."
VLESS="vless://${UUID}@${IP}:${PORT}?type=tcp&security=reality&pbk=${PBK}&fp=chrome&sni=${SNI}&sid=${SID}&spx=%2F&flow=xtls-rprx-vision#VPN%20360%20${TEST_USER}"

{
  echo "VPN 360 — servidor $IP (instalado $(date '+%Y-%m-%d %H:%M'))"
  echo
  echo "== Para la app (mandar a quien actualiza la app) =="
  echo "IP: $IP"
  echo "Clave publica REALITY (pbk): $PBK"
  echo
  echo "== Enlace de la cuenta de prueba '$TEST_USER' (ruta TCP) =="
  echo "$VLESS"
  echo
  echo "== Enlace de la ruta UDP =="
  echo "$HY2"
  echo
  echo "== Panel 3x-ui (solo por tunel SSH; nunca abierto a internet) =="
  echo "1) En tu PC:  ssh -L 2053:127.0.0.1:${XUI_PANEL_PORT} root@${IP}"
  echo "2) En Chrome: http://127.0.0.1:2053/${XUI_WEB_BASE_PATH}"
  echo "Usuario: $XUI_USERNAME"
  echo "Contrasena: $XUI_PASSWORD"
  echo "Entrada: $INBOUND_NAME (id $INBOUND_ID)"
  echo
  echo "== Para la web de cuentas (central): PANELS_JSON =="
  echo "[{\"name\":\"$(hostname)\",\"base_url\":\"$PANEL\",\"token\":\"$XUI_API_TOKEN\",\"inbound_ids\":[$INBOUND_ID],\"public_host\":\"$IP\"}]"
} > "$DIR/resumen.txt"
chmod 600 "$DIR/resumen.txt"

echo
echo "IP del servidor:              $IP"
echo "Clave publica (pbk):          $PBK"
echo
echo "Enlace TCP (cuenta $TEST_USER):"
echo "$VLESS"
echo
echo "Enlace UDP:"
echo "$HY2"
echo
echo "Panel: ver /etc/vpn360/resumen.txt (usuario, contrasena y como abrirlo por tunel SSH)."
say "Listo. Manda la IP y la clave publica (pbk) a quien actualiza la app; los enlaces solo a la persona de prueba."
