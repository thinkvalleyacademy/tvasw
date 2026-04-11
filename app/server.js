require('dotenv').config();
const express = require('express');
const bodyParser = require('body-parser');
const path = require('path');
const fs = require('fs');

const app = express();

app.use(bodyParser.urlencoded({ extended: true }));
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

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

const PORT = process.env.PORT || 4080;

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on port ${PORT}`);
});

// View queries in browser
app.get('/admin', (req, res) => {
  try {
    const data = JSON.parse(fs.readFileSync(DATA_FILE));
    res.json(data);
  } catch (err) {
    res.status(500).send('Error reading queries');
  }
});
