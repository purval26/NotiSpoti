Forget the Android Spotify-app/deep-link approach. That is NOT what I want.

My actual goal is to create an invisible/headless Spotify Web Player client.

The user should NOT need the Spotify Android app running, and there should be no Spotify UI.

Target architecture:

YT Music / Obsidian MediaSession
→ our Android app detects Title + Artist
→ our headless Spotify client searches Spotify
→ obtains Spotify track URI
→ establishes a Spotify Web Player-like playback session
→ updates Spotify's playback state
→ Instagram Notes should see the Spotify track.

We already have a verification report showing Spotify's current Web Player architecture:

- GET /api/server-time
- compute TOTP
- GET /api/token
- Pathfinder GraphQL search
- Dealer WebSocket
- Spotify-Connection-Id
- Connect-State device/session registration
- Connect-State player command

Do NOT assume old endpoints are valid.
Do NOT use open.spotify.com/get_access_token.
Do NOT use the Spotify Android app.
Do NOT use ACTION_VIEW.
Do NOT build the Android UI yet.

Build a standalone Node.js/TypeScript proof-of-concept that investigates ONLY whether we can create an invisible Web Player-like session and update Spotify playback state.

Requirements:

1. Implement the CURRENT /api/token flow using the required server-time/TOTP mechanism.
2. Implement Spotify Pathfinder track search.
3. Implement Dealer WebSocket connection.
4. Capture the Spotify-Connection-Id.
5. Determine the exact device-registration/session initialization sequence required by the current Web Player.
6. Determine what is required to create a LOCAL playback target rather than remotely controlling another Spotify device.
7. Attempt to play a known Spotify track through that virtual Web Player session.
8. Determine whether Spotify accepts the command on a FREE account.
9. Determine whether the resulting state appears in:
   - Spotify's playback state/currently-playing state where accessible
   - the Spotify account's Connect state
   - social integrations such as Instagram Notes, if testable.
10. Log every HTTP/WebSocket request and response status, but ALWAYS redact:

- sp_dc
- access tokens
- cookies
- connection IDs
- other credentials/secrets

Use environment variables for credentials/session values. Never hard-code secrets.

Most importantly, distinguish these two cases:

A) Remote Connect control:
our virtual client controls an existing Spotify phone/device.

B) Local Web Player playback:
our virtual client itself becomes the Spotify playback device/session.

I specifically need case B.

If case B is possible on a Spotify FREE account, prove it with the smallest working POC.

If Spotify requires actual audio decoding/CDM/DRM for the session to become a valid local player, document exactly where that requirement appears and whether a headless implementation can satisfy it.

Do not prematurely conclude that the project is impossible merely because remote Connect control requires Premium. We specifically want to test whether a virtual Web Player/local playback session is treated differently.

At the end give me:

RESULT:

- HEADLESS FREE PLAYBACK: PASS / FAIL / PARTIAL

Then explain:

- what worked
- what failed
- exact blocker
- whether Spotify backend playback state changed
- whether this can realistically be converted into an Android app
- next experiment if not solved

Do not build the final Android application yet.