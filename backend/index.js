const express = require('express');
const bodyParser = require('body-parser');
const app = express(); // create express app
require('dotenv').config();

const opentok = require('./opentok/opentok');
app.use(bodyParser.json());

const sessions = {};

app.get('/session/:room', async (req, res) => {
  try {
    const { room: roomName } = req.params;
    console.log(sessions);
    if (sessions[roomName]) {
      const data = opentok.generateToken(sessions[roomName]);
      res.json({
        sessionId: sessions[roomName],
        token: data.token,
        apiKey: data.apiKey,
      });
    } else {
      const data = await opentok.getCredentials();
      sessions[roomName] = data.sessionId;
      res.json({
        sessionId: data.sessionId,
        token: data.token,
        apiKey: data.apiKey,
      });
    }
  } catch (error) {
    console.log(error.message);
    res.status(500).send({ message: error.message });
  }
});

const serverPort = process.env.SERVER_PORT || process.env.PORT || 5000;
// start express server on port 5000
app.listen(serverPort, () => {
  console.log('server started on port', serverPort);
});
