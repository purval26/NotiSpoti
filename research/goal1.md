I want you to investigate and experimentally verify the Spotify APIs/protocols needed for this project.

Goal

I am building an Android app that:

1. Reads the currently playing song from another music app through Android notifications/MediaSession.
2. Extracts:
   - song title
   - artist
3. Searches Spotify for the matching track.
4. Obtains the Spotify track URI/ID.
5. Attempts to make Spotify register/play that track so that Spotify-dependent integrations such as Instagram's music/Notes state can potentially recognize it.

I DO NOT want you to start building the application yet.

Your task is to verify the actual Spotify network/API flow first.

---

Part 1 — Verify Spotify Search

Determine exactly how Spotify Web Player currently searches for tracks.

Test:

"https://open.spotify.com"

Use browser/network inspection or another appropriate method.

Search for a known song such as:

"Blinding Lights - The Weeknd"

Determine:

- HTTP method
- exact hostname
- exact endpoint/path
- query parameters
- request body, if any
- required headers
- authentication mechanism
- response structure
- how the Spotify track ID/URI is obtained

Determine whether the Web Player currently uses:

- "/v1/search"
- Pathfinder
- GraphQL
- "/pathfinder/v1/query"
- "/pathfinder/v2/query"
- another endpoint

Do not assume information from old reverse-engineering projects is still correct.

---

Part 2 — Verify Web Player authentication

Investigate:

"https://open.spotify.com/get_access_token?reason=transport&productType=web-player"

Determine:

- exact request method
- required cookies/session state
- response JSON structure
- access token field
- expiration field
- whether the token is usable for subsequent Web Player requests
- whether the token is different from a normal Spotify Web API OAuth token

DO NOT expose or save my actual Spotify credentials, cookies, or tokens.

Only report the structure and redact all secrets.

---

Part 3 — Verify the Web Player device/session

Determine how the Web Player creates/registers its playback device.

Look for requests involving:

- "track-playback"
- "devices"
- "spclient"
- "dealer"
- Connect
- device IDs
- WebSocket connections

Determine:

- how the Web Player obtains its device ID
- which request creates/registers the device
- what identifiers are required later to issue playback commands
- whether source and target device IDs can be the same for local Web Player playback

---

Part 4 — Verify the Play request

This is the MOST IMPORTANT part.

Open Spotify Web Player.

Search for a track.

Then click the track to play it.

Capture the network request(s) generated specifically by the play action.

Determine whether the current Web Player uses something similar to:

"POST https://<cluster>-spclient.spotify.com/connect-state/v1/player/command/from/<SOURCE_DEVICE_ID>/to/<TARGET_DEVICE_ID>"

If yes, document the exact current structure.

Determine:

- HTTP method
- hostname
- path
- required headers
- authentication
- source device ID
- target device ID
- request body
- track URI field
- play command structure
- whether "play_origin" is required
- whether "options" are required
- response status
- response body

DO NOT provide or expose my real authentication credentials.

Replace them with:

"REDACTED_TOKEN"

and:

"REDACTED_COOKIE"

---

Part 5 — Determine whether the play request works independently

This is critical.

After discovering the request, determine whether it can be reproduced outside the Web Player.

Create a harmless local test script/request where all credentials are supplied by environment variables.

For example:

"SPOTIFY_WEB_TOKEN"

"SPOTIFY_SOURCE_DEVICE"

"SPOTIFY_TARGET_DEVICE"

The script should accept:

"spotify:track:<TRACK_ID>"

and construct the equivalent playback request.

DO NOT hardcode credentials.

Do not attempt to bypass authentication, DRM, payment systems, or account security.

The purpose is only to determine whether the authenticated Web Player playback command can be reproduced.

---

Part 6 — Compare with the official API

Also verify the current official Spotify Web API:

Search

"GET https://api.spotify.com/v1/search"

Playback

"PUT https://api.spotify.com/v1/me/player/play"

Determine:

- required OAuth scopes
- Premium requirement
- device requirements
- whether either endpoint can accomplish the project without Premium

---

Final report format

Give me a clear table:

Operation| Current endpoint| Auth| Premium?| Stable/public?| Reproducible?
Search| ...| ...| ...| ...| ...
Get Web Token| ...| ...| ...| ...| ...
Register Web Player device| ...| ...| ...| ...| ...
Play track| ...| ...| ...| ...| ...

Then give me:

CONFIRMED

Only things you actually verified.

NOT CONFIRMED

Things found only in old reverse-engineering projects or documentation.

CURRENT REQUEST EXAMPLES

Provide sanitized HTTP requests showing the exact current structure.

MINIMUM REQUIRED FLOW

Give the smallest sequence of requests needed to:

"Song title + artist → Spotify track URI → authenticated Spotify playback"

FINAL VERDICT

Answer these explicitly:

1. Can we search Spotify without Premium?
2. Can we obtain a Spotify track URI without Premium?
3. Can the Web Player obtain its own authenticated runtime token?
4. Can we obtain/create the required Web Player device ID?
5. Can the Web Player play a track using the internal Connect-State request?
6. Can that request be reproduced outside the browser?
7. Does the reproduced playback change the Spotify account's current playback state?
8. Could this realistically be used as the backend mechanism for my Android app?

Do not implement the Android application yet. First give me the verified protocol and a minimal proof-of-concept.