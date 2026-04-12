#!/bin/bash

set -e  # stop on error

APP_DIR="/home/tvaprod02/tvasw"
LOG() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"; }

LOG "🚀 Starting deployment..."

cd $APP_DIR

LOG "📥 Discarding local changes and pulling latest code..."
git checkout -- . && git pull origin main

LOG "📦 Installing root dependencies..."
npm install

LOG "📦 Installing app dependencies..."
cd app
npm install

LOG "🔁 Restarting application..."

# Check if app exists in PM2
if pm2 describe thinkvalley > /dev/null; then
    pm2 reload thinkvalley --update-env
else
    LOG "⚠️ App not found in PM2, starting new..."
    pm2 start ecosystem.config.js
fi

LOG "💾 Saving PM2 process..."
pm2 save

LOG "🧹 Cleaning logs..."
pm2 flush thinkvalley

LOG "✅ Deployment completed successfully!"
