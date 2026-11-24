#!/bin/bash
# Store the Gemini API key on H81 (hidden prompt; sent over stdin, never as an argument)
set -euo pipefail

SERVER="${SERVER:-tva-pd@100.114.88.64}"

read -rsp "Gemini API key: " KEY; echo
if ! [[ "$KEY" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Key is empty or has unexpected characters"; exit 1
fi

printf '%s\n' "$KEY" | ssh "$SERVER" '
  set -e; umask 077
  cd ~/apps/tvasw
  IFS= read -r KEY
  grep -v "^GEMINI_API_KEY=" backend.env > backend.env.new
  printf "GEMINI_API_KEY=%s\n" "$KEY" >> backend.env.new
  mv backend.env.new backend.env
  systemctl --user restart tvasw-backend
  echo "Key saved, testing..."
  for i in $(seq 30); do
    sleep 2
    curl -s -m 5 -o /dev/null http://127.0.0.1:8080/ && break
  done
  curl -s -m 120 -H "Content-Type: application/json" -d "{\"projectId\":1,\"ideaText\":\"A fee tracker for a small school\"}" -w "\nHTTP %{http_code}\n" http://127.0.0.1:8080/api/ai/start
  journalctl --user -u tvasw-backend --since "-3 min" --no-pager | grep -A6 -i "gemini returned\|gemini request failed" | tail -12 || true
'
