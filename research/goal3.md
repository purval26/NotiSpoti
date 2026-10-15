We have now confirmed that a pure ⁰headless Spotify HTTP/WebSocket client will NOT work for our target:

- Spotify Free account
- no Spotify Android app
- no visible Spotify UI
- need Spotify backend playback state to update

The current POC confirmed that Free users receive PREMIUM_REQUIRED when registering through track-playback, and pure Node/headless playback also lacks the browser Widevine/CDM stack.

DO NOT continue developing the Node/spclient headless playback approach.

Instead, build a MINIMAL Android proof-of-concept using an INVISIBLE/OFF-SCREEN Android WebView.

Goal:

Run the real Spotify Web Player inside our Android app without displaying Spotify UI to the user.

Requirements:

1. Create a minimal Android app.
2. Add an Android WebView.
3. WebView must support JavaScript, DOM storage, cookies and media playback.
4. Navigate to:
   https://open.spotify.com
5. Do NOT display the WebView visually.
   It can be 1x1/off-screen/transparent as long as the page actually runs.
6. Do NOT use the Spotify Android application.
7. Do NOT use Spotify private HTTP APIs directly.
8. Do NOT extract sp_dc cookies or access tokens.
9. Let Spotify's own Web Player JavaScript perform authentication/session/device registration.
10. First determine whether open.spotify.com can actually initialize the Web Player inside Android WebView.
11. Determine whether Widevine/MediaDrm is available and whether Spotify successfully initializes its playback pipeline.
12. Use a desktop-like User-Agent ONLY if necessary for testing Web Player compatibility. Test the normal Android WebView UA first.
13. Log:
    - page URL changes
    - console messages
    - WebView errors
    - navigation redirects
    - DOM readiness
    - whether Spotify displays mobile/app-install restrictions
    - whether a player element becomes available
    - whether playback initialization succeeds/fails
14. Do NOT attempt to bypass CAPTCHA, bot detection, DRM, authentication, or access controls.
15. For the first POC, do not automatically click Play.

After the Web Player successfully loads, expose a tiny local JavaScript bridge so the native Android code can later execute controlled commands in the page.

For now, the only goal is:

PASS:
Spotify Web Player successfully initializes inside the invisible WebView and creates a usable playback session.

FAIL:
Spotify refuses WebView/mobile playback, redirects to app installation, blocks the environment, or cannot initialize its playback pipeline.

Also test both:
A. Spotify Free account
B. Logged-out/anonymous state

Do not build the full YT Music integration yet.

Return a detailed PASS/FAIL report explaining exactly where initialization succeeds or fails.