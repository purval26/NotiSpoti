Android WebView Proof-of-Concept Report (Goal
  3)

  A complete, self-contained Android test harness
  has been built, compiled, and signed directly in
  the workspace:

  • Compiled & Signed APK:
  NotiSpoti-WebView-POC.apk (21 KB)
  • Source Code:
      • Main Activity & WebView Harness:
      MainActivity.java
      • Native ↔ JavaScript Bridge: JsBridge.java
      • Manifest & Permissions:
      AndroidManifest.xml
      • Invisible Offscreen Layout:
      activity_main.xml
      • Standalone Build Pipeline: build_apk.sh

  ──────
  ## FINAL RESULT

  ### OVERALL OUTCOME: FAIL

  • Logged-out / Anonymous State (Scenario B):
  FAIL (Playback impossible; redirects to
  authentication modal).
  • Default Mobile WebView User-Agent: FAIL
  (Spotify refuses web playback; redirects to
  native app / Google Play).
  • Desktop User-Agent on Free Account (Scenario
  A): FAIL (Blocked by Connect registration tier
  gate 403 PREMIUM_REQUIRED, Widevine CDM headless
  limitations, and invisible WebView throttling).
  ──────
  ## Detailed Experimental Findings

  ### 1. Default Mobile User-Agent Test

  • What Spotify Serves: When loaded with the
  standard Android WebView User-Agent (Mozilla/5.0
  (Linux; Android 13; ...) Mobile Safari/537.36),
  Spotify's CDN detects the mobile client and
  serves mobile-web-player.js instead of the full
  desktop player.
  • App-Install Redirects:
      • The DOM contains explicit app-download
      banners: <button data-testid="download-
      button"> and <a href="https://www.spotify.
      com/download/">.
      • When user interaction or navigation occurs,
      Spotify attempts to trigger intent schemes:
      intent://#Intent;package=com.spotify.music;
      scheme=spotify... and
      market://details?id=com.spotify.music.
      • Caught and logged in MainActivity.java via
      shouldOverrideUrlLoading:
        [NAV_REDIRECT] Navigation request:
      intent://...
        [APP_RESTRICTION] >>> Spotify attempted
      native redirect to: intent://...

  • Player UI Absence: The Mobile Web Player has
  no now-playing bar ([data-testid="now-playing-
  bar"] is completely absent from the DOM). On
  mobile web, Spotify explicitly restricts full
  track playback, forcing users into the native
  Android application.
  ──────
  ### 2. Desktop User-Agent Test (Mozilla/5.0
  (X11; Linux x86_64)...)

  When overriding settings.setUserAgentString(...)
  to a desktop browser UA:

  • DOM Readiness & UI:
      • Spotify serves the desktop Single Page
      Application (web-player.js).
      • The desktop layout renders, and the player
      bar container ([data-testid="now-playing-
      bar"]) is present in the DOM tree.
  • Widevine DRM & MediaDrm State:
      • Android WebView supports navigator.
      requestMediaKeySystemAccess("com.widevine.
      alpha") only if the native app explicitly
      grants PermissionRequest.
      RESOURCE_PROTECTED_MEDIA_ID in
      WebChromeClient.onPermissionRequest (which
      we implemented in MainActivity.java).
      • However, Widevine inside an Android
      WebView is restricted to L3 software
      security and lacks Verified Media Path (VMP).
  • The Playback Pipeline Failure:
      1. Logged-out / Anonymous State:
      The desktop player mints an anonymous token
      (isAnonymous: true). The Dealer WebSocket is
      disabled (useDealer: !N.isAnonymous). Any
      play action triggers the login dialog modal
      ([data-testid="auth-modal"]). Playback
      cannot start without logging in.
      2. Free Account State:
      When logged into a Spotify Free account,
      Spotify's backend still evaluates the device
      registration at POST https://<cluster>-
      spclient.spotify.com/track-
      playback/v1/devices. As verified in Goal 2,
      Spotify returns:
        {
          "error_type": "PREMIUM_REQUIRED",
          "message": "Cannot register to Track
      Playback with non-premium user."
        }
      Furthermore, Free desktop accounts are
      served intermittent audio advertisements. In
      an invisible / background WebView, ad
      playback stalls, and Chrome's background tab
      throttling freezes requestAnimationFrame and
      audio timers, terminating playback.

  ──────
  ## Key WebView & Protocol Audit Metrics

   Checkpoint | Mobil… | Deskto… | Result / Impa…
  ------------|--------|---------|----------------
   Loaded     | mobile | web-    | Mobile serves
   Bundle     | -web-  | player. | restricted web
              | player | js      | player;
              | .js    |         | Desktop serves
              |        |         | full SPA
   Redirects  | Yes    | No      | Mobile forces
   to Native  | (inten | (stayed | user to Google
   App        | t://,  | within  | Play; Desktop
              | market | web     | stays in DOM
              | ://)   | domain) |
   Player Bar | Absent | Present | Mobile has no
   (now-      |        |         | web player
   playing-   |        |         | controls
   bar)       |        |         |
   Logged-Out | Blocke | Blocked | Both demand
   Playback   | d      |         | user login
              |        |         | ([data-
              |        |         | testid="auth-
              |        |         | modal"])
   Free       | Blocke | Blocked | Neither
   Account    | d (app | (PREMIU | establishes a
   Playback   | requir | M_REQUI | valid Connect
              | ed)    | RED)    | player session
   Widevine   | Requir | Require | Handled in
   EME Access | es     | s       | MainActivity.j
              | Protec | Protect | ava via
              | ted    | ed      | request.grant(
              | Media  | Media   | ...)
              | ID     | ID      |
   Invisible  | thrott | throttl | Chrome halts
   / 1x1      | led by | ed by   | timers when
   Rendering  | Chromi | Chromiu | view is not
              | um     | m       | visible
  ──────
  ## Architectural Conclusion

  The experimental evidence across Goals 1, 2, and
  3 establishes that neither a pure headless
  HTTP/WebSocket client nor an invisible Android
  WebView can silently establish an authenticated
  Spotify playback session on a Free account:

  • Spotify intentionally prevents headless and
  third-party web playback on Free accounts to
  protect their advertising revenue and DRM rights.
  • Remote Connect device commands and track-
  playback session registrations strictly enforce
  PREMIUM_REQUIRED on Spotify's backend.
  • Any attempt to bypass this via mobile web is
  blocked by Spotify's mobile web redirect
  policies, bot detection, and Widevine DRM
  constraints.

  The signed test app NotiSpoti-WebView-POC.apk
  remains in the workspace as a complete, working
  reference implementation of the invisible
  WebView and JavaScript bridge harness.