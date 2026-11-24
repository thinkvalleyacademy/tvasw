require('dotenv').config();
const express = require('express');
const path = require('path');
const fs = require('fs');
const http = require('http');
const crypto = require('crypto');

const app = express(); // ✅ define FIRST

// Set TRUST_PROXY=loopback when running behind a local reverse proxy
if (process.env.TRUST_PROXY) app.set('trust proxy', process.env.TRUST_PROXY);

// Simple per-IP limiter (in memory, reset on restart)
function rateLimit(limit, periodMs, failuresOnly = false) {
  const hits = new Map();
  setInterval(() => {
    const now = Date.now();
    hits.forEach((h, ip) => { if (h.expiresAt <= now) hits.delete(ip); });
  }, periodMs).unref();

  return (req, res, next) => {
    const now = Date.now();
    let hit = hits.get(req.ip);
    if (!hit || hit.expiresAt <= now) {
      hit = { count: 0, expiresAt: now + periodMs };
      hits.set(req.ip, hit);
    }
    if (++hit.count > limit) {
      if (failuresOnly) hit.count--;
      return res.status(429).send('Too many requests. Please try again later.');
    }
    if (failuresOnly) res.on('finish', () => { if (res.statusCode !== 401) hit.count--; });
    next();
  };
}

// Proxy API to the backend (must run before body parsers)
const BACKEND_HOST = process.env.BACKEND_HOST || '127.0.0.1';
const BACKEND_PORT = process.env.BACKEND_PORT || 8080;

app.use('/api', (req, res) => {
  const headers = { ...req.headers };
  headers['x-forwarded-for'] = req.ip; // never pass on a client-supplied value
  delete headers['x-admin-token'];
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
app.post('/send', rateLimit(5, 10 * 60 * 1000), (req, res) => {
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

function isAdminPassword(password) {
  if (!process.env.ADMIN_PASSWORD || !password) return false;
  const hash = (s) => crypto.createHash('sha256').update(s).digest();
  return crypto.timingSafeEqual(hash(password), hash(process.env.ADMIN_PASSWORD));
}

// Admin API - password protected, at most 10 wrong passwords per 15 minutes
app.use('/admin/api', rateLimit(10, 15 * 60 * 1000, true), (req, res, next) => {
  if (!isAdminPassword(req.get('X-Admin-Password'))) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
});

app.get('/admin/api', (req, res) => {
  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE));
    res.json(data);
  } catch (err) {
    res.status(500).send('Error reading queries');
  }
});

// Client projects, served by the backend
async function adminBackend(res, path, options = {}) {
  try {
    const resp = await fetch(`http://${BACKEND_HOST}:${BACKEND_PORT}/api/internal/projects${path}`, {
      ...options,
      headers: { 'Content-Type': 'application/json', 'X-Admin-Token': process.env.ADMIN_API_TOKEN || '' },
      signal: AbortSignal.timeout(15000)
    });
    // 401 here would mean a token mismatch, not a wrong admin password
    const status = resp.status === 401 ? 502 : resp.status;
    res.status(status).type('json').send(await resp.text());
  } catch (err) {
    console.error(err.message);
    res.status(502).json({ error: 'Backend unavailable' });
  }
}

app.get('/admin/api/projects', (req, res) => adminBackend(res, ''));

app.get('/admin/api/projects/:id', (req, res) => adminBackend(res, `/${encodeURIComponent(req.params.id)}`));

app.post('/admin/api/projects/:id/status', (req, res) => {
  const body = JSON.stringify({ status: req.body?.status });
  return adminBackend(res, `/${encodeURIComponent(req.params.id)}/status`, { method: 'POST', body });
});

// Start server
const PORT = process.env.PORT || 4080;

const HOST = process.env.HOST || '0.0.0.0';

app.listen(PORT, HOST, () => {
  console.log(`Server running on port ${PORT}`);
});