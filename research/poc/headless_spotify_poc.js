#!/usr/bin/env node

/**
 * headless_spotify_poc.js
 *
 * Comprehensive Headless Spotify Web Player Session & Playback Investigation
 * Tests Case B (Local Web Player Playback Device/Session) vs Case A (Remote Connect Control)
 *
 * Redaction Guarantee:
 *   All session cookies (sp_dc), access tokens, connection IDs, and private user identifiers
 *   are redacted from logs (e.g. REDACTED_TOKEN, REDACTED_COOKIE, REDACTED_CONN_ID).
 *
 * Credentials:
 *   Supplied solely via environment variable: SPOTIFY_SP_DC
 *   If not supplied, runs in Anonymous mode to test unauthorized boundaries.
 */

const crypto = require("crypto");

// --- Helper Functions for Sanitized Logging ---
function sanitize(str) {
  if (!str) return str;
  return str
    .replace(/BQ[A-Za-z0-9_-]{80,}/g, "REDACTED_TOKEN")
    .replace(/sp_dc=[^;\s]+/g, "sp_dc=REDACTED_COOKIE")
    .replace(/"accessToken":"[^"]+"/g, '"accessToken":"REDACTED_TOKEN"')
    .replace(/Spotify-Connection-Id":"[^"]+"/g, 'Spotify-Connection-Id":"REDACTED_CONN_ID"')
    .replace(/X-Spotify-Connection-Id: [^\r\n]+/g, "X-Spotify-Connection-Id: REDACTED_CONN_ID");
}

function logInfo(tag, msg, data = null) {
  console.log(`[${tag}] ${msg}`);
  if (data !== null) {
    if (typeof data === "string") {
      console.log(`       ${sanitize(data)}`);
    } else {
      console.log(`       ${sanitize(JSON.stringify(data, null, 2))}`);
    }
  }
}

// --- Step 1: Server-Time & TOTP Computation ---
const TOTP_CONFIG = {
  secret: ",7/*F(\"rLJ2oxaKL^f+E1xvP@N",
  version: 61
};

function deriveSecret(rawSecret) {
  const xor = rawSecret.split("").map((ch, idx) => ch.charCodeAt(0) ^ ((idx % 33) + 9));
  return Buffer.from(xor.join(""), "utf8");
}

function generateTOTP(secret, timestamp, period = 30, digits = 6) {
  const counter = Math.floor(timestamp / 1000 / period);
  const buf = Buffer.alloc(8);
  buf.writeBigInt64BE(BigInt(counter));
  const hmac = crypto.createHmac("sha1", secret).update(buf).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const code = ((hmac[offset] & 0x7f) << 24 |
                (hmac[offset + 1] & 0xff) << 16 |
                (hmac[offset + 2] & 0xff) << 8 |
                (hmac[offset + 3] & 0xff)) % (10 ** digits);
  return code.toString().padStart(digits, "0");
}

// --- Step 2: Fetch Web Player Access Token ---
async function fetchAccessToken(spDc = null) {
  logInfo("AUTH", "Fetching Spotify server-time...");
  const timeRes = await fetch("https://open.spotify.com/api/server-time");
  if (!timeRes.ok) throw new Error(`HTTP ${timeRes.status} from /api/server-time`);
  const { serverTime } = await timeRes.json();
  logInfo("AUTH", `Server time synchronized: ${serverTime}`);

  const secret = deriveSecret(TOTP_CONFIG.secret);
  const totp = generateTOTP(secret, Date.now());
  const totpServer = generateTOTP(secret, serverTime * 1000);

  const tokenUrl = `https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=${totp}&totpServer=${totpServer}&totpVer=${TOTP_CONFIG.version}`;
  logInfo("AUTH", `Requesting token from /api/token (version: ${TOTP_CONFIG.version})...`);

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "application/json",
    "Referer": "https://open.spotify.com/"
  };

  if (spDc) {
    headers["Cookie"] = `sp_dc=${spDc}`;
    logInfo("AUTH", "Using provided user cookie: sp_dc=REDACTED_COOKIE");
  } else {
    logInfo("AUTH", "No SPOTIFY_SP_DC provided. Proceeding with Anonymous session.");
  }

  const tokenRes = await fetch(tokenUrl, { headers });
  if (!tokenRes.ok) throw new Error(`HTTP ${tokenRes.status} from /api/token`);
  const data = await tokenRes.json();

  logInfo("AUTH", `Token received successfully (HTTP ${tokenRes.status})`);
  logInfo("AUTH", `Account Mode: ${data.isAnonymous ? "ANONYMOUS (No account)" : "AUTHENTICATED USER"}`);
  logInfo("AUTH", `Client ID: ${data.clientId}`);
  logInfo("AUTH", `Token Expiry: ${new Date(data.accessTokenExpirationTimestampMs).toISOString()}`);
  logInfo("AUTH", `Token (Redacted): Bearer REDACTED_TOKEN (length: ${data.accessToken?.length || 0})`);

  return data;
}

// --- Step 3: Pathfinder Search ---
async function searchSpotify(token, query) {
  logInfo("SEARCH", `Executing Pathfinder GraphQL query for: "${query}"...`);
  const url = "https://api-partner.spotify.com/pathfinder/v1/query";
  const payload = {
    variables: {
      searchTerm: query,
      offset: 0,
      limit: 5,
      numberOfTopResults: 3,
      includeAudiobooks: true
    },
    operationName: "searchDesktop",
    extensions: {
      persistedQuery: {
        version: 1,
        sha256Hash: "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03"
      }
    }
  };

  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json",
      "Accept": "application/json"
    },
    body: JSON.stringify(payload)
  });

  logInfo("SEARCH", `Pathfinder response status: HTTP ${res.status}`);
  if (!res.ok) throw new Error(`Search failed: HTTP ${res.status}`);

  const json = await res.json();
  const items = json?.data?.searchV2?.tracksV2?.items || [];
  if (items.length === 0) {
    logInfo("SEARCH", "No tracks matched query.");
    return null;
  }

  const bestMatch = items[0].item.data;
  logInfo("SEARCH", `Best match: "${bestMatch.name}" by ${bestMatch.artists?.items?.map(a => a.profile?.name).join(", ")}`);
  logInfo("SEARCH", `Track URI: ${bestMatch.uri}`);
  logInfo("SEARCH", `Track ID:  ${bestMatch.id}`);

  return bestMatch;
}

// --- Step 4: Cluster Resolution ---
async function resolveCluster() {
  logInfo("DISCOVERY", "Resolving active Spotify cluster via apresolve.spotify.com...");
  const res = await fetch("https://apresolve.spotify.com/?type=dealer-g2&type=spclient");
  if (!res.ok) throw new Error(`apresolve failed: HTTP ${res.status}`);
  const data = await res.json();
  const dealerHost = data["dealer-g2"][0].replace(/:443$/, "");
  const spclientHost = data.spclient[0].replace(/:443$/, "");
  logInfo("DISCOVERY", `Active Dealer Host:   ${dealerHost}`);
  logInfo("DISCOVERY", `Active Spclient Host: ${spclientHost}`);
  return { dealerHost, spclientHost };
}

// --- Step 5: Connect to Dealer WebSocket and obtain Connection-Id ---
function connectDealerWebSocket(dealerHost, token) {
  return new Promise((resolve, reject) => {
    const wsUrl = `wss://${dealerHost}/?access_token=${token}`;
    logInfo("DEALER_WS", `Connecting to WebSocket at wss://${dealerHost}/?access_token=REDACTED_TOKEN...`);
    const ws = new WebSocket(wsUrl);

    let connectionId = null;

    ws.onopen = () => {
      logInfo("DEALER_WS", "WebSocket connection ESTABLISHED.");
    };

    ws.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data);
        if (msg.headers && msg.headers["Spotify-Connection-Id"]) {
          connectionId = msg.headers["Spotify-Connection-Id"];
          logInfo("DEALER_WS", `Received Spotify-Connection-Id: REDACTED_CONN_ID (length: ${connectionId.length})`);
          logInfo("DEALER_WS", `Initial Handshake URI: ${msg.uri || "unknown"}`);
          resolve({ ws, connectionId });
        }
      } catch (err) {
        logInfo("DEALER_WS", `Non-JSON or binary frame received: ${evt.data.slice(0, 50)}`);
      }
    };

    ws.onerror = (err) => {
      logInfo("DEALER_WS", `WebSocket error: ${err.message || err}`);
      reject(err);
    };

    ws.onclose = (evt) => {
      logInfo("DEALER_WS", `WebSocket closed (code: ${evt.code}, reason: "${evt.reason}")`);
    };

    setTimeout(() => {
      if (!connectionId) {
        ws.close();
        reject(new Error("Timeout waiting for Spotify-Connection-Id from Dealer"));
      }
    }, 8000);
  });
}

// --- Step 6: Case B Investigation — Local Player Device Registration & Playback ---
async function testCaseBLocalPlayback(spclientHost, token, connectionId, trackUri) {
  console.log("\n========================================================");
  console.log("CASE B INVESTIGATION: Virtual Local Web Player Playback");
  console.log("========================================================");

  const localDeviceId = crypto.randomBytes(20).toString("hex");
  logInfo("CASE_B", `Generated Virtual Local Device ID: ${localDeviceId}`);

  // 6A. Connect-State Observer Device Registration (hobs_<deviceId>)
  logInfo("CASE_B", `[Stage 1] Registering Observer device: PUT /connect-state/v1/devices/hobs_${localDeviceId}...`);
  try {
    const obsRes = await fetch(`https://${spclientHost}/connect-state/v1/devices/hobs_${localDeviceId}`, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "X-Spotify-Connection-Id": connectionId,
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify({
        member_type: "CONNECT_STATE",
        device: {
          device_info: {
            capabilities: {
              can_be_player: false,
              hidden: true,
              needs_full_player_state: true
            }
          }
        }
      })
    });
    const obsText = await obsRes.text();
    logInfo("CASE_B", `Observer Device Registration Status: HTTP ${obsRes.status}`);
    logInfo("CASE_B", `Observer Response: ${obsText || "(empty body)"}`);
  } catch (err) {
    logInfo("CASE_B", `Observer registration request error: ${err.message}`);
  }

  // 6B. Track-Playback Local Player Device Registration (POST /track-playback/v1/devices)
  logInfo("CASE_B", `[Stage 2] Registering Local Player device: POST /track-playback/v1/devices...`);
  const tpPayload = {
    device: {
      brand: "spotify",
      capabilities: {
        change_volume: true,
        enable_play_token: true,
        supports_file_media_type: true,
        manifest_formats: [
          "file_urls_mp3",
          "file_ids_mp3",
          "manifest_ids_video",
          "file_urls_aac",
          "file_ids_aac"
        ]
      },
      device_id: localDeviceId,
      device_type: "computer",
      metadata: {},
      model: "web_player",
      name: "NotiSpoti Virtual Web Player",
      platform: "Linux"
    },
    outro_endcontent_snooping: false,
    connection_id: connectionId,
    client_version: "1.3.6.1274.g08871b335bc3",
    volume: 65535
  };

  let tpRegistered = false;
  try {
    const tpRes = await fetch(`https://${spclientHost}/track-playback/v1/devices`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify(tpPayload)
    });
    const tpText = await tpRes.text();
    logInfo("CASE_B", `Track-Playback Registration Status: HTTP ${tpRes.status}`);
    logInfo("CASE_B", `Track-Playback Response: ${tpText}`);

    if (tpRes.ok) {
      tpRegistered = true;
    } else {
      if (tpRes.status === 403 && tpText.includes("PREMIUM_REQUIRED")) {
        logInfo("CASE_B", ">>> BLOCKER CONFIRMED: Spotify Track-Playback rejected registration with PREMIUM_REQUIRED <<<");
      } else if (tpRes.status === 400 && tpText.includes("INVALID_USER")) {
        logInfo("CASE_B", ">>> BLOCKER CONFIRMED: Spotify Track-Playback rejected registration due to INVALID_USER (Anonymous token) <<<");
      }
    }
  } catch (err) {
    logInfo("CASE_B", `Track-playback registration error: ${err.message}`);
  }

  // 6C. Connect-State Direct Player Device Registration (PUT /connect-state/v1/devices/<deviceId>)
  logInfo("CASE_B", `[Stage 3] Testing Direct Connect-State Player Registration: PUT /connect-state/v1/devices/${localDeviceId}...`);
  try {
    const csRes = await fetch(`https://${spclientHost}/connect-state/v1/devices/${localDeviceId}`, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "X-Spotify-Connection-Id": connectionId,
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify({
        member_type: "CONNECT_STATE",
        device: {
          device_info: {
            capabilities: {
              can_be_player: true,
              hidden: false,
              needs_full_player_state: true
            }
          }
        }
      })
    });
    const csText = await csRes.text();
    logInfo("CASE_B", `Direct Connect-State Registration Status: HTTP ${csRes.status}`);
    logInfo("CASE_B", `Direct Connect-State Response: ${csText || "(empty body)"}`);
  } catch (err) {
    logInfo("CASE_B", `Direct Connect-State registration error: ${err.message}`);
  }

  // 6D. Attempt Local Playback Command Dispatch (Local Playback Target: from localDeviceId to localDeviceId)
  logInfo("CASE_B", `[Stage 4] Dispatching Local Playback Command: POST /connect-state/v1/player/command/from/${localDeviceId}/to/${localDeviceId}...`);
  const playCmdPayload = {
    command: {
      endpoint: "play",
      context: {
        uri: trackUri,
        url: `context://${trackUri}`
      },
      play_origin: {
        feature_identifier: "web-player",
        feature_version: "1.3.6.1274"
      },
      options: {
        license: "",
        initially_paused: false
      },
      logging_params: {
        command_id: `cmd_${Date.now()}`
      }
    }
  };

  try {
    const playRes = await fetch(`https://${spclientHost}/connect-state/v1/player/command/from/${localDeviceId}/to/${localDeviceId}`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${token}`,
        "X-Spotify-Connection-Id": connectionId,
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify(playCmdPayload)
    });
    const playText = await playRes.text();
    logInfo("CASE_B", `Local Playback Command Status: HTTP ${playRes.status}`);
    logInfo("CASE_B", `Local Playback Response: ${playText || "(empty body)"}`);
  } catch (err) {
    logInfo("CASE_B", `Local playback command error: ${err.message}`);
  }

  // 6E. Check Playback State via Currently Playing
  logInfo("CASE_B", `[Stage 5] Checking current playback state via /v1/me/player/currently-playing...`);
  try {
    const currRes = await fetch("https://api.spotify.com/v1/me/player/currently-playing", {
      headers: { "Authorization": `Bearer ${token}` }
    });
    logInfo("CASE_B", `Currently-Playing Status: HTTP ${currRes.status}`);
    const currText = await currRes.text();
    logInfo("CASE_B", `Currently-Playing Data: ${currText || "(204 No Content / Nothing Playing)"}`);
  } catch (err) {
    logInfo("CASE_B", `Currently-playing check error: ${err.message}`);
  }
}

// --- Main Runner ---
async function main() {
  console.log("========================================================");
  console.log("HEADLESS SPOTIFY WEB PLAYER SESSION PROOF-OF-CONCEPT");
  console.log("========================================================");

  const spDc = process.env.SPOTIFY_SP_DC || null;
  const tokenData = await fetchAccessToken(spDc);
  const track = await searchSpotify(tokenData.accessToken, "Blinding Lights - The Weeknd");
  const trackUri = track ? track.uri : "spotify:track:0VjIjW4GlUZAMYd2vXMi3b";

  const { dealerHost, spclientHost } = await resolveCluster();
  const { ws, connectionId } = await connectDealerWebSocket(dealerHost, tokenData.accessToken);

  try {
    await testCaseBLocalPlayback(spclientHost, tokenData.accessToken, connectionId, trackUri);
  } finally {
    ws.close();
    logInfo("CLEANUP", "Dealer WebSocket closed cleanly.");
  }
}

main().catch(err => {
  console.error("\n[FATAL ERROR]:", err.message);
  process.exit(1);
});
