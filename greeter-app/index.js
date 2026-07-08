const http = require('http');
const version = process.env.VERSION || 'v1';
const port = 80;

const server = http.createServer((req, res) => {
  res.statusCode = 200;
  res.setHeader('Content-Type', 'text/plain');
  res.end(`Hello from version ${version}\n`);
});

server.listen(port, () => {
  console.log(`Greeter ${version} running on port ${port}`);
});