const path = require('path');
const BASE_DIR = path.resolve(__dirname);

module.exports = {
  apps: [
    {
      name: "thinkvalley",
      script: path.join(BASE_DIR, "app/server.js"),
      cwd: BASE_DIR,
      env: {
        NODE_ENV: "production",
        PORT: 4080,
        DATA_DIR: path.join(BASE_DIR, "client_queries")
      }
    }
  ]
};
