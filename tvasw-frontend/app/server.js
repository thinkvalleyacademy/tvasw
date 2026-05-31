require('dotenv').config();
const express = require('express');
const path = require('path');
const fs = require('fs');

const app = express(); // ✅ define FIRST

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
  if (password !== 'tva808#') {
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

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on port ${PORT}`);
});