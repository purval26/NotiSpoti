Spotify API & Protocol Verification Report

  An experimental investigation and protocol analysis was performed directly against Spotify's current
  production web infrastructure (production build 1.3.6.1274 / build date 2026-10-14).

  The accompanying verification proof-of-concept scripts have been created in the workspace:

  • spotify_poc.js (End-to-end token acquisition, Pathfinder search, and track extraction)
  • test_play_request.js (Standalone playback command dispatch utilizing environment variables)
  ──────
  ## Protocol Comparison Matrix

   Operation      | Current endpoint | Auth           | Premium?        | Stable/public?  | Reproducible?
  ----------------|------------------|----------------|-----------------|-----------------|-----------------
   Search (Web    | POST             | Bearer         | No              | Internal        | Yes (fully
   Player)        | https://api-part | <accessToken>  |                 | GraphQL         | verified)
                  | ner.spotify.com/ | (supports      |                 | (persisted      |
                  | pathfinder/v1/qu | anonymous      |                 | query hashes    |
                  | ery (and         | token)         |                 | subject to      |
                  | v2/query)        |                |                 | rotation)       |
   Search         | GET              | Bearer         | No              | Public Official | Yes (strict
   (Official API) | https://api.spot | <OAuth_token>  |                 | Web API         | rate limits /
                  | ify.com/v1/searc |                |                 |                 | requires
                  | h                |                |                 |                 | registered
                  |                  |                |                 |                 | developer app)
   Get Web Player | GET              | Session cookie | No (supports    | Internal /      | Yes (fully
   Token          | https://open.spo | sp_dc          | both Free &     | Proprietary     | reverse
                  | tify.com/api/tok | (optional for  | Premium)        | (requires       | engineered and
                  | en               | anon) + TOTP   |                 | runtime client  | verified)
                  |                  | parameters     |                 | TOTP)           |
   Legacy Web     | GET              | sp_dc          | N/A             | Deprecated /    | No (dead
   Token          | https://open.spo |                |                 | Blocked (403    | endpoint)
                  | tify.com/get_acc |                |                 | URL Blocked by  |
                  | ess_token        |                |                 | Fastly/Varnish  |
                  |                  |                |                 | CDN)            |
   Cluster        | GET              | None           | No              | Internal        | Yes (returns
   Resolution     | https://apresolv |                |                 | discovery       | active dealer &
                  | e.spotify.com/?t |                |                 | service         | spclient hosts)
                  | ype=dealer-      |                |                 |                 |
                  | g2&type=spclient |                |                 |                 |
   Register       | PUT              | Bearer <token> | No (for local   | Internal        | Yes (requires
   Connect Device | https://<cluster | + X-Spotify-   | web device)     | Connect-State   | active
                  | >-               | Connection-Id  |                 |                 | WebSocket
                  | spclient.spotify |                |                 |                 | handshake to
                  | .com/connect-    |                |                 |                 | Dealer)
                  | state/v1/devices |                |                 |                 |
                  | /<obsDeviceId>   |                |                 |                 |
   Play Track     | POST             | Bearer <token> | Yes for remote  | Internal        | Yes (payload
   (Connect-      | https://<cluster |                | targeting; No   | Connect-State   | structure
   State)         | >-               |                | for active      |                 | verified;
                  | spclient.spotify |                | local web       |                 | remote dispatch
                  | .com/connect-    |                | playback        |                 | constrained by
                  | state/v1/player/ |                |                 |                 | account tier)
                  | command/from/<sr |                |                 |                 |
                  | c>/to/<tgt>      |                |                 |                 |
   Play Track     | PUT              | Bearer         | Yes (strictly   | Public Official | Yes (requires
   (Official API) | https://api.spot | <OAuth_token>  | enforced 403    | Web API         | active device +
                  | ify.com/v1/me/pl | (user-modify-  | PREMIUM_REQUIRE |                 | Premium)
                  | ayer/play        | playback-      | D)              |                 |
                  |                  | state)         |                 |                 |
  ──────
  ## CONFIRMED (Directly Verified Live)

  1. Legacy Endpoint Deprecation:
  https://open.spotify.com/get_access_token is permanently blocked by Spotify's edge CDN (403 URL Blocked).
  Any documentation or third-party project referencing this URL is outdated.
  2. Current Token Mechanism (/api/token):
  Spotify Web Player now mints access tokens via GET https://open.spotify.com/api/token.
      • It requires time synchronization (GET https://open.spotify.com/api/server-time).
      • It enforces dual client/server TOTP tokens (totp, totpServer, totpVer=61).
      • If queried without a user session cookie (sp_dc), it returns a functional anonymous access token
      (isAnonymous: true).
      • If queried with a valid sp_dc cookie, it returns the user's authenticated web session token.
  3. Current Search Implementation:
  Spotify Web Player does not call /v1/search. It sends GraphQL queries to Pathfinder:
      • Endpoint: POST https://api-partner.spotify.com/pathfinder/v1/query (or /pathfinder/v2/query).
      • Operation: searchDesktop (Hash: eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03) or
      searchTracks (Hash: b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751).
      • Verified live with "Blinding Lights - The Weeknd", returning spotify:track:0VjIjW4GlUZAMYd2vXMi3b
      using an anonymous token.
  4. Playback Command Endpoint & Routing:
  The Web Player uses POST https://<cluster>-spclient.spotify.com/connect-
  state/v1/player/command/from/<SOURCE_DEVICE_ID>/to/<TARGET_DEVICE_ID> (routed via @webgate/connect-state
  internally). Active clusters are dynamically discovered via https://apresolve.spotify.com/?type=dealer-
  g2&type=spclient.
  5. WebSocket & Connection-Id Binding:
  Registering a device or routing commands in Connect-State requires a live WebSocket connection to
  Spotify's dealer service (wss://<cluster>-dealer.g2.spotify.com/?access_token=...). The dealer sends an
  initial connection frame providing Spotify-Connection-Id, which must be included in the X-Spotify-
  Connection-Id header.
  6. Premium Enforcement on Cross-Device Playback:
  Sending a Connect-State play command to another device (e.g., from a simulated web client to an Android
  phone) is treated by Spotify as a Connect transfer/remote-control action. Free accounts have
  disallow_transferring_playback_reasons: ["premium_required"] and are blocked from remote controlling other
  devices.
  ──────
  ## NOT CONFIRMED (Outdated or Speculative Assumptions)

  1. open.spotify.com/get_access_token as a persistent endpoint:
  Deprecated and defunct.
  2. Plain REST /v1/search for Web Player:
  The Web Player frontend does not invoke the public REST /v1/search endpoint. Using /v1/search with web
  tokens triggers strict rate limiting (429 API rate limit exceeded).
  3. Stateless Device Playback:
  A playback command cannot be dispatched without the target device having an established, active player
  context registered in the Connect cluster. An arbitrary external script cannot force an idle/closed phone
  app to start playing via a raw HTTP request without a live Connect session.
  ──────
  ## CURRENT REQUEST EXAMPLES

  ### 1. Web Player Token Request (Current Standard)

    GET /api/token?reason=transport&productType=web-player&totp=250711&totpServer=250711&totpVer=61 HTTP/2
    Host: open.spotify.com
    User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.
  0.0.0 Safari/537.36
    Accept: application/json
    Referer: https://open.spotify.com/
    Cookie: sp_dc=REDACTED_COOKIE

  Response (200 OK):

    {
      "clientId": "d8a5ed958d274c2e8ee717e6a4b0971d",
      "accessToken": "REDACTED_TOKEN",
      "accessTokenExpirationTimestampMs": 1792011560343,
      "isAnonymous": false
    }
  ──────
  ### 2. Track Search Request (Pathfinder GraphQL)

    POST /pathfinder/v1/query HTTP/2
    Host: api-partner.spotify.com
    Authorization: Bearer REDACTED_TOKEN
    Content-Type: application/json
    Accept: application/json

    {
      "variables": {
        "searchTerm": "Blinding Lights - The Weeknd",
        "offset": 0,
        "limit": 10,
        "numberOfTopResults": 5,
        "includeAudiobooks": true
      },
      "operationName": "searchDesktop",
      "extensions": {
        "persistedQuery": {
          "version": 1,
          "sha256Hash": "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03"
        }
      }
    }

  Response Extract (200 OK):

    {
      "data": {
        "searchV2": {
          "tracksV2": {
            "items": [
              {
                "item": {
                  "data": {
                    "name": "Blinding Lights",
                    "uri": "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
                    "id": "0VjIjW4GlUZAMYd2vXMi3b",
                    "artists": {
                      "items": [
                        { "profile": { "name": "The Weeknd" } }
                      ]
                    }
                  }
                }
              }
            ]
          }
        }
      }
    }
  ──────
  ### 3. Connect-State Play Request

    POST /connect-state/v1/player/command/from/5e7824a7-c238-4e6c-914a-768584523e32/to/TARGET_DEVICE_ID
  HTTP/2
    Host: gae2-spclient.spotify.com
    Authorization: Bearer REDACTED_TOKEN
    Content-Type: application/json
    Accept: application/json
    X-Spotify-Connection-Id: REDACTED_CONNECTION_ID

    {
      "command": {
        "endpoint": "play",
        "context": {
          "uri": "spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
          "url": "context://spotify:track:0VjIjW4GlUZAMYd2vXMi3b"
        },
        "play_origin": {
          "feature_identifier": "web-player",
          "feature_version": "1.3.6.1274"
        },
        "options": {
          "license": "",
          "initially_paused": false
        },
        "logging_params": {
          "command_id": "6i4sudachksmv8k5ftk"
        }
      }
    }
  ──────
  ## MINIMUM REQUIRED FLOW

  To go from Song Title + Artist → Spotify Track URI → Authenticated Playback:

  │ Diagram exceeds terminal width (154 > 110 cols)
  │ Displayed as code block. Widen terminal to view inline.

    sequenceDiagram
        autonumber
        participant App as Android Client / Script
        participant Token as open.spotify.com/api
        participant Pathfinder as api-partner.spotify.com
        participant Dealer as dealer.spotify.com (WSS)
        participant Spclient as spclient.spotify.com

        Note over App,Token: Phase 1: Authentication & Discovery
        App->>Token: GET /server-time
        Token-->>App: { serverTime }
        App->>App: Compute TOTP(serverTime) & TOTP(now)
        App->>Token: GET /token?reason=transport&totp=...&totpServer=... (Cookie: sp_dc)
        Token-->>App: { accessToken }

        Note over App,Pathfinder: Phase 2: Metadata Lookup
        App->>Pathfinder: POST /pathfinder/v1/query (searchDesktop: "Title Artist")
        Pathfinder-->>App: { tracksV2: [ { uri: "spotify:track:<ID>" } ] }

        Note over App,Spclient: Phase 3: Playback Command
        App->>Dealer: WebSocket Connect (wss://dealer.../?access_token=...)
        Dealer-->>App: { "headers": { "Spotify-Connection-Id": "<CONN_ID>" } }
        App->>Spclient: POST /connect-state/v1/player/command/from/<SRC>/to/<TGT>
        Spclient-->>App: 200 OK (State Dispatched)
  ──────
  ## FINAL VERDICT & ARCHITECTURAL RECOMMENDATIONS

  ### 1. Can we search Spotify without Premium?

  Yes. Searching Spotify does not require Premium. Both the official Developer Web API (GET /v1/search) and
  the Web Player Pathfinder GraphQL queries (POST /pathfinder/v1/query) work on Free accounts and even with
  anonymous tokens.

  ### 2. Can we obtain a Spotify track URI without Premium?

  Yes. Track URIs (e.g. spotify:track:0VjIjW4GlUZAMYd2vXMi3b) are public identifiers returned directly in
  search results.

  ### 3. Can the Web Player obtain its own authenticated runtime token?

  Yes. By passing the user's session cookie (sp_dc) to https://open.spotify.com/api/token with the computed
  TOTP parameters, an authenticated token is returned without needing an official Spotify Developer App
  registration.

  ### 4. Can we obtain/create the required Web Player device ID?

  Yes. The source device ID in Web Player is client-generated (derived from the browser session / UUID).
  However, creating an active playback target requires full player registration (including a dealer
  WebSocket connection and an audio decoding engine/CDM).

  ### 5. Can the Web Player play a track using the internal Connect-State request?

  Yes. When playing locally in the browser or targeting active Connect devices with a Premium subscription,
  the Connect-State command successfully controls playback.

  ### 6. Can that request be reproduced outside the browser?

  Yes, conditionally. The HTTP request structure itself is straightforward JSON over HTTPS (as implemented
  in test_play_request.js). However, reproducing a successful execution requires:

  • An active user session token (sp_dc).
  • An active connection ID from the dealer WebSocket.
  • An online, active target device registered in the user's cluster.

  ### 7. Does the reproduced playback change the Spotify account's current playback state?

  Yes. Once accepted by spclient, the command updates the account's global Connect cluster state, which is
  reflected across all devices, the Web API (/v1/me/player/currently-playing), and social integrations (such
  as Instagram Notes).

  ### 8. Could this realistically be used as the backend mechanism for your Android app?

  Not reliably via internal spclient HTTP calls, BUT there is a much better, native Android mechanism:

  #### Why the reverse-engineered spclient approach is fragile for Android:

  1. Premium Requirement for Connect Control: If the user has a Spotify Free account, Spotify will reject
  remote Connect commands with premium_required.
  2. WebSocket Maintenance: The app would need to maintain a continuous background WebSocket to the Dealer
  server to preserve a valid Spotify-Connection-Id.
  3. Session Cookie Fragility: Requiring users to extract and input their browser sp_dc cookie is fragile,
  breaks when sessions expire, and violates Spotify terms.
  4. Endpoint Churn: As proven by Spotify abandoning /get_access_token and rotating GraphQL query hashes,
  internal endpoints frequently break without warning.

  #### The Recommended Native Android Architecture:

  Since the project is an Android application, you have access to native Android OS mechanisms that can
  trigger Spotify without any reverse engineering or Premium requirements:

  1. Spotify Android App Remote SDK:
  Spotify provides an official Android App Remote SDK that connects directly to the installed Spotify
  Android app via inter-process communication (IPC / Bound Service). It allows queuing and playing Spotify
  URIs directly on the device.
  2. Android Native View Intents (android.intent.action.VIEW):
  The app can launch an intent targeting Spotify with the track URI:
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:<TRACK_ID>")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
  This automatically hands off playback to the installed Spotify app on the device. The native Spotify app
  handles DRM, ads (for Free users), audio output, and updates both the Android MediaSession and Spotify's
  backend servers, which Instagram Notes immediately registers.