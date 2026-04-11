module.exports = {
  apps: [
    {
      name: "thinkvalley",
      script: "./app/server.js",
      cwd: "/home/tvaprod02/tvasw",
      env: {
        NODE_ENV: "production",
        PORT: 4080,
        DATA_DIR: "/home/tvaprod02/tvasw/client_queries"
      }
    }
  ]
};