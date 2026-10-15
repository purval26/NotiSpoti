# How We Achieved the Goal: NotiSpoti Technical Architecture & Journey

## 1. Executive Summary & Objective

The objective of **NotiSpoti** was to build an automated, zero-touch bridge on Android that:
1. Intercepts whatever song is playing in third-party music apps (**YouTube Music**, **Obsidian**, local media players, or browsers).
2. Automatically resolves the exact track on **Spotify** without requiring user intervention.
3. Commands Spotify to play the resolved track **100% silently in the background** (with zero on-screen UI, no screen flicker, and no foreground focus theft).
4. Updates Spotify's cloud backend in real time so that **Instagram Notes' "Share from Spotify"** feature immediately detects and displays the currently playing song.

All of this had to operate on a **standard Free (non-premium) Spotify account** on modern **Android 15 / 16 preview (SDK 36)**.

---

## 2. Research & Reverse-Engineering Phase

Before writing native Android code, we conducted four rigorous reverse-engineering investigations:

### Goal 1: Spotify Web Player & Token Protocol Reverse Engineering
- Reverse-engineered the Web Player client bundle (`vendor.js` and `web-player.js`).
- Extracted the TOTP client secret derivation algorithm:
  $$\text{secret}[i] = \text{raw}[i] \oplus ((i \bmod 33) + 9)$$
- Synchronized with Spotify's server time endpoint (`GET https://open.spotify.com/api/server-time`).
- Successfully minted zero-auth transport tokens via `GET https://open.spotify.com/api/token?reason=transport&productType=web-player&totp=...&totpServer=...`.
- Verified the modern **Pathfinder GraphQL endpoint** (`POST https://api-partner.spotify.com/pathfinder/v1/query`) using `searchDesktop` and persisted query hash `eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03`.

### Goal 2: Headless Virtual Playback Limits (Free vs. Premium)
- Tested whether a purely headless, HTTP/WebSocket client in Node.js or Kotlin could emulate a Web Player session.
- **Finding**: Spotify's `track-playback` service (`POST /track-playback/v1/devices`) strictly enforces **`403 PREMIUM_REQUIRED`** for device registration.
- **DRM Requirement**: Web playback requires Widevine Content Decryption Modules (CDM) and encrypted DASH streams (`@webgate/widevine-license/v1/audio/license`).
- **Conclusion**: A purely headless cloud/network client cannot simulate active playback for a Free user without the official app or a real browser CDM.

### Goal 3: Hidden Android WebView Feasibility
- Built and signed `NotiSpoti-WebView-POC.apk` to test an invisible, offscreen Android WebView running `open.spotify.com`.
- **Finding**: Mobile WebViews are redirected to Google Play/native app; spoofing a desktop User-Agent enabled DOM rendering, but background tab throttling froze timers, and Free desktop accounts encountered unskippable audio ads and bot verification.

### Goal 4: Instagram Notes Verification Mechanism
- Deconstructed Instagram Notes' "Share from Spotify" feature.
- **Finding**: Instagram does **not** listen to physical audio output, microphone, or on-device broadcasts. It uses a server-to-server OAuth integration pulling from `GET https://api.spotify.com/v1/me/player/currently-playing`.
- **Takeaway**: As long as Spotify's cloud reports `is_playing: true`, physical sound waves do not matter (Spotify can be muted or routed to a virtual sink).

---

## 3. Roadblocks Overcome During Native Android Development

### Roadblock 1: Android 15/16 "App Not Installed" Rejection
- **Symptom**: When attempting to install the initial prototype APK, the Android package installer failed with *"App not installed"*.
- **Root Cause**: The device was running Android SDK 36 (Android 15+). The initial manifest omitted `<uses-sdk>`, which caused Android to default `targetSdkVersion` to `1`. Starting in Android 14, Android strictly enforces `INSTALL_FAILED_DEPRECATED_SDK_VERSION` (blocking any app targeting API < 23).
- **Fix**:
  - Explicitly configured `<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />` in `AndroidManifest.xml` and `aapt2 link`.
  - Recompiled all Java code with `-source 8 -target 8` for universal ART compatibility.
  - Aligned uncompressed DEX packaging using `jar -u -0 -f`.

### Roadblock 2: Spotify Edge Firewall 403 Forbidden
- **Symptom**: Track resolution failed with `Pathfinder search returned HTTP 403 Client/request not allowed`.
- **Root Cause**: Android's default `HttpURLConnection` sends `User-Agent: Dalvik/...` with no `Origin` or `Referer`. Spotify's Akamai/Cloudflare edge firewall blocked the request as an unauthorized bot.
- **Fix**: Added desktop browser headers, `Referer: https://open.spotify.com/`, `Origin: https://open.spotify.com`, and `app-platform: WebPlayer` into `SpotifyTrackResolver.java`.

### Roadblock 3: Generic Android MediaSession Ignored by Spotify
- **Symptom**: When commanding Spotify via Android's `MediaController.TransportControls.playFromSearch(...)` or `playFromUri(...)`, Spotify ignored the command or resumed the previously paused song.
- **Root Cause**:
  1. Spotify's `MediaSession.Callback` intentionally ignores external track injection from third-party apps to protect its playback model.
  2. Inspecting Spotify's `MediaBrowserService` via reverse engineering revealed an internal, hardcoded package whitelist in `onGetRoot` that logs:
     ```
     E/Spotify: onGetRoot: IGNORING request from untrusted package com.notispoti.app
     ```
  3. Calling `controller.getTransportControls().play()` simply told Spotify to resume whatever old track was sitting dormant in its cache.

### Roadblock 4: Foreground Screen Theft & Background Activity Launch (BAL) Blocks
- **Symptom**: Launching Spotify via `Intent.ACTION_VIEW` loaded the track, but popped Spotify's full UI onto the screen, and Android 15 blocked the background service from restoring the previous app.
- **Root Cause**: Activities are visual components that Android's WindowManager brings to the foreground. In Android 10 through 15, background services cannot launch activities to steal focus back without privileged overlay permissions.

---

## 4. The Final Working Solution: Spotify App Remote IPC

The breakthrough that provided a **100% silent, uninterrupted background experience** was integrating the **official Spotify App Remote SDK (v0.8.0)**.

### How the Architecture Works:

```
┌──────────────────────────────────────┐
│  YouTube Music / Obsidian / Player   │  (User listens normally)
└──────────────────┬───────────────────┘
                   │ Android Notification Posted
                   ▼
┌────────────────────────────────────────────────────────┐
│  MediaNotificationListenerService (NotiSpoti)          │
│  - Extracts Title & Artist                             │
│  - Strips clutter: [Official Audio], (Lyrics), etc.    │
│  - Deduplicates seekbar / progress updates             │
└──────────────────┬─────────────────────────────────────┘
                   │
                   ▼
┌────────────────────────────────────────────────────────┐
│  SpotifyTrackResolver (Native Java)                    │
│  - Mints TOTP token via https://open.spotify.com       │
│  - Executes Pathfinder GraphQL 'searchDesktop' query   │
│  - Matches song to 'spotify:track:<id>'                │
└──────────────────┬─────────────────────────────────────┘
                   │
                   ▼
┌────────────────────────────────────────────────────────┐
│  SpotifyPlaybackDispatcher (App Remote SDK v0.8.0)     │
│  - Binds to Spotify's background service over IPC      │
│  - Calls: mSpotifyAppRemote.getPlayerApi().play(uri)   │
│  - ZERO UI / ZERO SCREEN THEFT / 100% SILENT           │
└──────────────────┬─────────────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│  Official Spotify Android App        │  (Streams audio silently in background)
└──────────────────┬───────────────────┘
                   │ WebSocket / Connect State
                   ▼
┌──────────────────────────────────────┐
│  Spotify Global Cloud Backend        │  (currently-playing updated in real-time)
└──────────────────┬───────────────────┘
                   │ Server-to-Server OAuth
                   ▼
┌──────────────────────────────────────┐
│  Instagram Notes                     │  ("Share from Spotify" displays active song)
└──────────────────────────────────────┘
```

### Overcoming the One-Time Auth Barrier:
- On initial handshake, Spotify requires a one-time approval from the user (*"Authorize NotiSpoti to control Spotify"*).
- By passing `MainActivity.this` with `showAuthView(true)` during setup, the user approves the connection once.
- The approval token is stored permanently on the device, allowing all subsequent playback commands to execute **100% silently in the background** without ever showing a UI prompt again.

---

## 5. Granular Source App Whitelisting

To prevent unintended audio/video notifications from interfering (e.g. social media, games, video players, browsers):
- **User-Configured Whitelist**: Instead of intercepting every global Android media notification, `MediaNotificationListenerService` strictly verifies notifications against a user-selected package whitelist stored in `SharedPreferences` via [`AppPreferences.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/AppPreferences.java).
- **Source App Selection Screen**: [`AppSelectionActivity.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/AppSelectionActivity.java) provides a dedicated management screen:
  - Queries all installed launcher and user apps with `PackageManager.queryIntentActivities` (supported via `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`).
  - Shows app icons, friendly names, and package identifiers with interactive checkboxes.
  - Features real-time search/filter, "Reset Defaults" (seeding YouTube Music and Obsidian), and "Clear All".
  - Selected apps are pinned to the top of the list for quick inspection.
- **Main Dashboard Integration**: [`MainActivity.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/MainActivity.java) displays live active source count and friendly names (e.g., `Syncing from 2 apps (YouTube Music, Obsidian)`).
- **Master Sync Engine On/Off Switch**:
  - Located at the very top of `MainActivity`.
  - When toggled **OFF**, `MediaNotificationListenerService` immediately ignores all notifications with zero CPU or network overhead.
  - State is saved persistently in `AppPreferences` (`KEY_SYNC_ENABLED`), allowing users to completely disable sync when not sharing notes on Instagram.

---

## 6. Summary of Core Project Files

| File | Purpose |
| :--- | :--- |
| [`MediaNotificationListenerService.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/MediaNotificationListenerService.java) | Intercepts media notifications strictly from user-whitelisted apps. |
| [`AppSelectionActivity.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/AppSelectionActivity.java) | App selection UI with search, toggles, app icons, and default presets. |
| [`AppPreferences.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/AppPreferences.java) | Persistent storage and helper methods for source app whitelisting. |
| [`SpotifyTrackResolver.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/SpotifyTrackResolver.java) | Zero-auth token derivation + Pathfinder GraphQL desktop track search. |
| [`SpotifyPlaybackDispatcher.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/SpotifyPlaybackDispatcher.java) | Headless playback dispatcher powered by the Spotify App Remote SDK. |
| [`MainActivity.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/MainActivity.java) | Dashboard for Notification Access, Client ID config, source app management, manual tests, and live logs. |
| [`AppLogger.java`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/src/com/notispoti/app/AppLogger.java) | Thread-safe in-memory log buffer and live UI event forwarding hub. |
| [`build_apk.sh`](file:///data/data/com.termux/files/home/storage/downloads/NotiSpoti/notispoti_app/build_apk.sh) | Self-contained build pipeline (`aapt2`, `javac`, `d8`, `jar`, `apksigner`). |

---

## 7. How to Rebuild & Run

To build the APK from source inside Termux:

```bash
cd /data/data/com.termux/files/home/storage/downloads/NotiSpoti
bash notispoti_app/build_apk.sh
```

To install directly to the device:
```bash
termux-open /storage/emulated/0/Download/NotiSpoti.apk
```

**Final Deliverable**: Verified and operational at `/storage/emulated/0/Download/NotiSpoti.apk` (`381 KB`).
