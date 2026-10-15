#!/usr/bin/env node

/**
 * test_play_request.js
 *
 * Proof-of-concept standalone script reproducing the Spotify Connect-State playback command.
 * All credentials and identifiers are read exclusively from environment variables.
 *
 * Environment variables:
 *   SPOTIFY_WEB_TOKEN: Bearer token obtained from Web Player session (sp_dc or /api/token)
 *   SPOTIFY_SOURCE_DEVICE: Spotify source device ID (e.g., Web Player correlation/device ID)
 *   SPOTIFY_TARGET_DEVICE: Spotify target device ID (e.g., active phone/desktop/web device)
 *   SPOTIFY_CLUSTER: (Optional) Spotify cluster prefix, default 'gae2' or 'spclient.wg'
 *   SPOTIFY_CONNECTION_ID: (Optional) Connection ID from dealer WebSocket if registered
 *
 * Usage:
 *   node test_play_request.js spotify:track:<TRACK_ID>
 */

const trackUri = process.argv[2];

if (!trackUri || !trackUri.startsWith("spotify:track:")) {
  console.error("Usage: node test_play_request.js spotify:track:<TRACK_ID>");
  process.exit(1);
}

const token = process.env.SPOTIFY_WEB_TOKEN;
const sourceDevice = process.env.SPOTIFY_SOURCE_DEVICE;
const targetDevice = process.env.SPOTIFY_TARGET_DEVICE;
const cluster = process.env.SPOTIFY_CLUSTER || "gae2";
const connectionId = process.env.SPOTIFY_CONNECTION_ID;

if (!token || !sourceDevice || !targetDevice) {
  console.error("Missing required environment variables:");
  if (!token) console.error("  - SPOTIFY_WEB_TOKEN is not set");
  if (!sourceDevice) console.error("  - SPOTIFY_SOURCE_DEVICE is not set");
  if (!targetDevice) console.error("  - SPOTIFY_TARGET_DEVICE is not set");
  process.exit(1);
}

async function sendPlayCommand() {
  const host = cluster.includes(".") ? cluster : `${cluster}-spclient.spotify.com`;
  const url = `https://${host}/connect-state/v1/player/command/from/${encodeURIComponent(sourceDevice)}/to/${encodeURIComponent(targetDevice)}`;

  const commandId = Math.random().toString(36).substring(2) + Date.now().toString(36);

  const payload = {
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
        command_id: commandId
      }
    }
  };

  const headers = {
    "Authorization": `Bearer ${token}`,
    "Content-Type": "application/json",
    "Accept": "application/json"
  };

  if (connectionId) {
    headers["X-Spotify-Connection-Id"] = connectionId;
  }

  console.log("=== Spotify Play Command Dispatch ===");
  console.log(`Endpoint: POST ${url}`);
  console.log(`Target Track: ${trackUri}`);
  console.log(`Source Device: ${sourceDevice}`);
  console.log(`Target Device: ${targetDevice}`);
  console.log(`Connection ID: ${connectionId ? "SET" : "NONE"}`);
  console.log("Payload:", JSON.stringify(payload, null, 2));

  try {
    const res = await fetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify(payload)
    });

    console.log(`\nResponse Status: ${res.status} ${res.statusText}`);
    const body = await res.text();
    if (body) {
      console.log("Response Body:", body);
    } else {
      console.log("Response Body: (empty)");
    }
  } catch (err) {
    console.error("Request failed:", err.message);
  }
}

sendPlayCommand();
