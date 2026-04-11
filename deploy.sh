#!/bin/bash

echo "Pulling latest code..."
git pull

echo "Installing dependencies..."
cd app
npm install

echo "Restarting app..."
pm2 restart thinkvalley

echo "Deployment complete!"