#!/bin/bash

set -e  # stop on error

APP_DIR="$(pwd)"
LOG() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

LOG "🚀 Starting deployment..."

cd $APP_DIR

LOG "📦 Installing root dependencies..."
npm install

LOG "📦 Installing app dependencies..."
(cd app && npm install)

LOG "🔁 Restarting application..."

# Check if app exists in PM2
if pm2 describe thinkvalley > /dev/null; then
    pm2 delete thinkvalley
    sleep 1
fi
pm2 start ecosystem.config.js

LOG "💾 Saving PM2 process..."
pm2 save

LOG "🧹 Cleaning logs..."
pm2 flush thinkvalley

# ✅ Sanity check: wait a few seconds, then verify the server responds
LOG "🔍 Running sanity check..."
sleep 3

HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:4080)

if [ "$HEALTH_CODE" = "200" ]; then
    LOG "✅ Sanity check passed (HTTP $HEALTH_CODE)"
else
    LOG "❌ Sanity check FAILED (HTTP $HEALTH_CODE)"
    LOG "❌ Check pm2 logs 0 for errors"
    exit 1
fi

LOG "✅ Deployment completed successfully!"
