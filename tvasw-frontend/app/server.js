require('dotenv').config();
const express = require('express');
const path = require('path');
const fs = require('fs');
const http = require('http');

const app = express(); // ✅ define FIRST

// Proxy AI API to the backend (must run before body parsers)
const BACKEND_HOST = process.env.BACKEND_HOST || '127.0.0.1';
const BACKEND_PORT = process.env.BACKEND_PORT || 8080;

app.use('/api/ai', (req, res) => {
  const headers = { ...req.headers };
  delete headers.origin; // same-origin for the browser, so skip backend CORS

  const proxyReq = http.request({
    host: BACKEND_HOST,
    port: BACKEND_PORT,
    path: req.originalUrl,
    method: req.method,
    headers
  }, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });

  proxyReq.on('error', (err) => {
    console.error(err.message);
    if (res.headersSent) return res.destroy();
    res.status(502).json({ error: 'Backend unavailable' });
  });
  res.on('close', () => proxyReq.destroy());
  req.pipe(proxyReq);
});

// Middleware
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// ✅ Serve frontend (CORRECT PATH)
app.use(express.static(path.join(__dirname, '../public')));

// Root route
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/index.html'));
});

// Storage path
const DATA_DIR = process.env.DATA_DIR || './data';
const DATA_FILE = path.join(DATA_DIR, 'queries.json');

// Ensure directory & file exist
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}
if (!fs.existsSync(DATA_FILE)) {
  fs.writeFileSync(DATA_FILE, JSON.stringify([], null, 2));
}

// Save query
app.post('/send', (req, res) => {
  const { name, email, message } = req.body;

  if (!name || !email || !message) {
    return res.status(400).send('Missing fields');
  }

  const newEntry = {
    name,
    email,
    message,
    timestamp: new Date().toISOString()
  };

  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE));
    data.push(newEntry);
    fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
    res.send('Query submitted successfully!');
  } catch (err) {
    console.error(err);
    res.status(500).send('Error saving query');
  }
});

// Admin route - serve page
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, '../public/admin.html'));
});

// Admin API - password protected
app.get('/admin/api', (req, res) => {
  const { password } = req.query;
  if (!process.env.ADMIN_PASSWORD || password !== process.env.ADMIN_PASSWORD) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE));
    res.json(data);
  } catch (err) {
    res.status(500).send('Error reading queries');
  }
});

// Start server
const PORT = process.env.PORT || 4080;

const HOST = process.env.HOST || '0.0.0.0';

app.listen(PORT, HOST, () => {
  console.log(`Server running on port ${PORT}`);
});