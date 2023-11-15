const OpenTok = require('opentok');
const apiKey = process.env.VIDEO_API_API_KEY;
const apiSecret = process.env.VIDEO_API_API_SECRET;
const { projectToken } = require('opentok-jwt');
const axios = require('axios');

if (!apiKey || !apiSecret) {
  throw new Error(
    'Missing config values for env params OT_API_KEY and OT_API_SECRET'
  );
}
let sessionId;

const opentok = new OpenTok(apiKey, apiSecret);

const createSessionandToken = () => {
  return new Promise((resolve, reject) => {
    opentok.createSession({ mediaMode: 'routed' }, function (error, session) {
      if (error) {
        reject(error);
      } else {
        sessionId = session.sessionId;
        const token = opentok.generateToken(sessionId);
        resolve({ sessionId: sessionId, token: token });
        //console.log("Session ID: " + sessionId);
      }
    });
  });
};

const generateToken = (sessionId, role) => {
  const token = opentok.generateToken(sessionId, {role: role ?? "publisher" });
  return { token: token, apiKey: apiKey };
};

const getCredentials = async (session = null) => {
  const data = await createSessionandToken(session);
  sessionId = data.sessionId;
  const token = data.token;
  await enableLiveCaptions(sessionId)
  return { sessionId: sessionId, token: token, apiKey: apiKey };
};

const enableLiveCaptions = async (sessionId) => {
  try {
    const captionId = await axios.post(`https://api.opentok.com/v2/project/${apiKey}/captions`, {
        "sessionId": sessionId,
        "token": generateToken(sessionId, "moderator").token,
        "languageCode": "en-US",
        "maxDuration": 1800,
        "partialCaptions": true
      } , { headers: {
        "Content-Type" : "application/json",
        "X-OPENTOK-AUTH": projectToken(apiKey, apiSecret)
        } 
      }) 
  }
  catch (err){
    console.log("enable live captions error: ", err)
  }
}

module.exports = {
  getCredentials,
  generateToken
};
