#!/bin/bash
# Build locally and deploy to H81 as systemd user services (no sudo needed)
set -euo pipefail

SERVER="${SERVER:-tva-pd@100.114.88.64}"
BIND_IP="${SERVER#*@}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

(cd "$ROOT/tvasw-backend" && mvn -q -B clean package)

ssh "$SERVER" 'mkdir -p ~/apps/tvasw/{frontend/app,data,client_queries} ~/.config/systemd/user && chmod 700 ~/apps/tvasw'
scp -q "$ROOT/tvasw-backend/target/backend-0.0.1-SNAPSHOT.jar" "$SERVER:apps/tvasw/backend.jar.new"
scp -q "$ROOT"/tvasw-frontend/app/{server.js,package.json,package-lock.json} "$SERVER:apps/tvasw/frontend/app/"
scp -qr "$ROOT/tvasw-frontend/public" "$SERVER:apps/tvasw/frontend/"
scp -q "$ROOT"/deploy/h81/*.service "$SERVER:.config/systemd/user/"

ssh "$SERVER" BIND_IP="$BIND_IP" bash -s <<'REMOTE'
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
HOST=$BIND_IP
PORT=4080
BACKEND_PORT=8080
DATA_DIR=$HOME/apps/tvasw/client_queries
ADMIN_PASSWORD=$(openssl rand -hex 12)
EOF
fi

(cd frontend/app && npm ci --omit=dev --silent)
mv backend.jar.new backend.jar
systemctl --user daemon-reload
systemctl --user enable -q tvasw-backend tvasw-frontend
systemctl --user restart tvasw-backend tvasw-frontend

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
