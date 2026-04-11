#!/bin/bash

set -e  # stop on error

APP_DIR="/home/tvaprod02/tvasw"

echo "🚀 Starting deployment..."

cd $APP_DIR

echo "📥 Pulling latest code..."
git stash && git pull origin main

echo "📦 Installing dependencies..."
npm install
cd app
npm install

echo "🔁 Restarting application..."

# Check if app exists in PM2
if pm2 describe thinkvalley > /dev/null; then
    pm2 reload thinkvalley --update-env
else
    echo "⚠️ App not found in PM2, starting new..."
    pm2 start ecosystem.config.js
fi

echo "💾 Saving PM2 process..."
pm2 save

echo "🧹 Cleaning logs (optional)..."
pm2 flush thinkvalley

echo "✅ Deployment completed successfully!"