#!/bin/bash
# Build locally and deploy to H81 as systemd user services (no sudo needed)
set -euo pipefail

SERVER="${SERVER:-tva-pd@100.114.88.64}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

(cd "$ROOT/tvasw-backend" && mvn -q -B clean package > /tmp/tvasw-build.log 2>&1 || { tail -40 /tmp/tvasw-build.log; exit 1; })

ssh "$SERVER" 'mkdir -p ~/apps/tvasw/{frontend/app,data,client_queries} ~/.config/systemd/user && chmod 700 ~/apps/tvasw'
scp -q "$ROOT/tvasw-backend/target/backend-0.0.1-SNAPSHOT.jar" "$SERVER:apps/tvasw/backend.jar.new"
scp -q "$ROOT"/tvasw-frontend/app/{server.js,package.json,package-lock.json} "$SERVER:apps/tvasw/frontend/app/"
scp -qr "$ROOT/tvasw-frontend/public" "$SERVER:apps/tvasw/frontend/"
scp -q "$ROOT"/deploy/h81/*.service "$ROOT"/deploy/h81/*.timer "$SERVER:.config/systemd/user/"
scp -q "$ROOT/deploy/h81/backup.sh" "$SERVER:apps/tvasw/"

ssh "$SERVER" bash -s <<'REMOTE'
set -euo pipefail
umask 077
cd ~/apps/tvasw

# Create env files once; later deploys keep them
if [ ! -f backend.env ]; then
  cat > backend.env <<EOF
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:h2:file:$HOME/apps/tvasw/data/tvaswdb
SPRING_JPA_SHOW_SQL=false
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=INFO
TVASW_APP_JWTSECRET=$(openssl rand -hex 48)
GEMINI_API_KEY=
EOF
fi
if [ ! -f frontend.env ]; then
  cat > frontend.env <<EOF
NODE_ENV=production
HOST=127.0.0.1
TRUST_PROXY=loopback
PORT=4080
BACKEND_PORT=8080
DATA_DIR=$HOME/apps/tvasw/client_queries
ADMIN_PASSWORD=$(openssl rand -hex 12)
EOF
fi

# Shared token that lets the frontend call the backend admin API
if ! grep -q '^TVASW_APP_ADMINTOKEN=' backend.env || ! grep -q '^ADMIN_API_TOKEN=' frontend.env; then
  TOKEN=$(openssl rand -hex 32)
  for entry in "backend.env TVASW_APP_ADMINTOKEN" "frontend.env ADMIN_API_TOKEN"; do
    set -- $entry
    grep -v "^$2=" "$1" > "$1.new" || true
    echo "$2=$TOKEN" >> "$1.new"
    mv "$1.new" "$1"
  done
fi

# Version script and style links, so browsers don't keep old copies after a deploy
VERSION=$(cat frontend/public/*.js frontend/public/*.css | sha256sum | cut -c1-10)
sed -i -E "s#(src|href)=\"(/?[a-z]+\.(js|css))\"#\1=\"\2?v=$VERSION\"#g" frontend/public/*.html

(cd frontend/app && npm ci --omit=dev --silent)
mv backend.jar.new backend.jar
systemctl --user daemon-reload
systemctl --user enable -q tvasw-backend tvasw-frontend
systemctl --user enable -q --now tvasw-backup.timer
systemctl --user restart tvasw-backend tvasw-frontend

BIND_IP=$(sed -n 's/^HOST=//p' frontend.env)
for i in $(seq 60); do
  code=$(curl -s -m 5 -o /dev/null -w '%{http_code}' "http://$BIND_IP:4080/" || true)
  api=$(curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/ai/none || true)
  if [ "$code" = 200 ] && [ "$api" != 000 ]; then
    echo "Deployed: http://$BIND_IP:4080"
    exit 0
  fi
  sleep 2
done
echo "Health check failed (frontend $code, backend $api)"
exit 1
REMOTE
