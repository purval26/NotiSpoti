#!/usr/bin/env node

/**
 * spotify_poc.js
 *
 * Verified Proof of Concept:
 * 1. Obtains Spotify Web Player Token via current /api/token + TOTP flow.
 * 2. Searches Spotify using current Pathfinder GraphQL query (searchDesktop).
 * 3. Extracts track URI & ID.
 * 4. Displays reproduction request for playback.
 */

const crypto = require("crypto");

// Web Player current TOTP configuration (derived from current client bundle)
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

async function getWebPlayerToken(spDcCookie = null) {
  const timeRes = await fetch("https://open.spotify.com/api/server-time");
  if (!timeRes.ok) throw new Error(`Failed to get server-time: ${timeRes.status}`);
  const { serverTime } = await timeRes.json();

  const secret = deriveSecret(TOTP_CONFIG.secret);
  const totp = generateTOTP(secret, Date.now());
  const totpServer = generateTOTP(secret, serverTime * 1000);

  const tokenUrl = `https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=${totp}&totpServer=${totpServer}&totpVer=${TOTP_CONFIG.version}`;

  const headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "application/json",
    "Referer": "https://open.spotify.com/"
  };

  if (spDcCookie) {
    headers["Cookie"] = `sp_dc=${spDcCookie}`;
  }

  const res = await fetch(tokenUrl, { headers });
  if (!res.ok) throw new Error(`Token fetch failed: ${res.status}`);
  return await res.json();
}

async function searchTrack(token, query) {
  const url = "https://api-partner.spotify.com/pathfinder/v1/query";
  const payload = {
    variables: {
      searchTerm: query,
      offset: 0,
      limit: 10,
      numberOfTopResults: 5,
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

  if (!res.ok) throw new Error(`Search failed: ${res.status}`);
  const json = await res.json();
  const items = json?.data?.searchV2?.tracksV2?.items || [];
  return items.map(i => i.item.data);
}

async function main() {
  const query = process.argv[2] || "Blinding Lights - The Weeknd";
  console.log(`[Step 1] Fetching Web Player Token...`);
  const tokenData = await getWebPlayerToken(process.env.SPOTIFY_SP_DC || null);
  console.log(`  -> Token acquired (isAnonymous: ${tokenData.isAnonymous})`);
  console.log(`  -> Client ID: ${tokenData.clientId}`);
  console.log(`  -> Expires: ${new Date(tokenData.accessTokenExpirationTimestampMs).toISOString()}`);

  console.log(`\n[Step 2] Searching for: "${query}" via Pathfinder...`);
  const tracks = await searchTrack(tokenData.accessToken, query);
  if (tracks.length === 0) {
    console.log("  -> No tracks found.");
    return;
  }

  const top = tracks[0];
  console.log(`  -> Best Match: "${top.name}" by ${top.artists?.items?.map(a => a.profile?.name).join(", ")}`);
  console.log(`  -> Spotify URI: ${top.uri}`);
  console.log(`  -> Spotify ID:  ${top.id}`);

  console.log(`\n[Step 3] Playback Command Specification:`);
  console.log(`  Target Endpoint: POST https://<cluster>-spclient.spotify.com/connect-state/v1/player/command/from/<SOURCE_DEVICE_ID>/to/<TARGET_DEVICE_ID>`);
  console.log(`  Payload Context: { "uri": "${top.uri}", "url": "context://${top.uri}" }`);
}

main().catch(err => {
  console.error("Error:", err.message);
  process.exit(1);
});
