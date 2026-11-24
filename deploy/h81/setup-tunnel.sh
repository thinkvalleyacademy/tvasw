#!/bin/bash
# Publish the site through its own Cloudflare tunnel on H81 (asks for a browser login once)
set -euo pipefail

SERVER="${SERVER:-tva-pd@100.114.88.64}"
DOMAIN="thinkvalleysoftwares.in"
TUNNEL="tvasw"
DIR="$(cd "$(dirname "$0")" && pwd)"

ssh "$SERVER" 'mkdir -p ~/.config/systemd/user'
scp -q "$DIR/tvasw-tunnel.service" "$SERVER:.config/systemd/user/"

if ! ssh "$SERVER" 'test -f ~/.cloudflared/cert.pem'; then
  echo "Open the link below in your browser, log in to Cloudflare and choose $DOMAIN."
  ssh -t "$SERVER" 'umask 077; cloudflared tunnel login'
fi

echo "This points $DOMAIN and www.$DOMAIN at H81, replacing their current DNS records."
read -rp "Continue? [y/N] " OK
[[ "$OK" == [yY] ]] || exit 1

ssh "$SERVER" bash -s -- "$DOMAIN" "$TUNNEL" <<'REMOTE'
set -euo pipefail
umask 077
DOMAIN="$1"; TUNNEL="$2"
cd ~/.cloudflared

if [ ! -f tvasw.json ]; then
  cloudflared tunnel create --credentials-file "$HOME/.cloudflared/tvasw.json" "$TUNNEL" </dev/null
fi
ID=$(grep -oE '"TunnelID":"[^"]+' tvasw.json | cut -d'"' -f4)

cat > tvasw.yml <<EOF
tunnel: $ID
credentials-file: $HOME/.cloudflared/tvasw.json
metrics: 127.0.0.1:20251
ingress:
  - hostname: $DOMAIN
    service: http://127.0.0.1:4080
  - hostname: www.$DOMAIN
    service: http://127.0.0.1:4080
  - service: http_status:404
EOF
cloudflared --config tvasw.yml tunnel ingress validate </dev/null

systemctl --user daemon-reload
systemctl --user enable -q tvasw-tunnel
systemctl --user restart tvasw-tunnel

# Only move DNS once the tunnel is connected
for i in $(seq 30); do
  sleep 2
  curl -sf -m 5 http://127.0.0.1:20251/ready >/dev/null && break
  [ "$i" != 30 ] || { echo "Tunnel did not connect"; journalctl --user -u tvasw-tunnel -n 15 --no-pager; exit 1; }
done
echo "Tunnel connected, updating DNS..."
for host in "$DOMAIN" "www.$DOMAIN"; do
  cloudflared tunnel route dns --overwrite-dns "$ID" "$host" </dev/null
done

for host in "$DOMAIN" "www.$DOMAIN"; do
  code=000
  for i in $(seq 30); do
    code=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "https://$host/" || true)
    [ "$code" != 200 ] || break
    sleep 3
  done
  echo "https://$host/ -> HTTP $code"
done

# The login certificate can change DNS for the whole domain and is only needed for setup
rm -f cert.pem
REMOTE
