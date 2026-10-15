# NotiSpoti 🎵

> **Silent, automated background bridge that syncs music playing in YouTube Music, Obsidian, or local players to Spotify — keeping your Instagram Notes "Share from Spotify" updated in real time!**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34%2B)-green.svg)](https://developer.android.com)
[![Spotify](https://img.shields.io/badge/Spotify-Free%20%26%20Premium-1DB954.svg)](https://spotify.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/purval26/NotiSpoti?color=brightgreen)](https://github.com/purval26/NotiSpoti/releases)

---

## 🌟 Why NotiSpoti?

Instagram Notes offers a popular feature: **"Share from Spotify"**, displaying what song you are listening to in real time. However, Instagram only reads playback data directly from Spotify's cloud servers. 

If you listen to music on **YouTube Music**, **Obsidian audio**, **SoundCloud**, or local media players — or if you do not have Spotify Premium — Instagram Notes cannot detect your music.

**NotiSpoti bridges this gap seamlessly:**
1. Intercepts media playback notifications from user-selected apps (e.g. YouTube Music, Obsidian).
2. Resolves the exact track on Spotify's database in `<300ms` using an automated zero-auth Pathfinder search.
3. Commands the Spotify Android app **100% silently in the background** via local Android Binder IPC (Spotify App Remote SDK).
4. Spotify updates its cloud state to `is_playing: true`, and **Instagram Notes immediately displays the song!**

---

## ✨ Features

- ⚡ **100% Silent & Headless Background Operation**: Uses the official Spotify App Remote SDK IPC. No UI popups, no screen theft, no foreground interruptions while browsing or reading.
- 🆓 **Spotify Free Compatible**: Works with standard Free (non-premium) Spotify accounts as well as Premium.
- 🎯 **Source App Whitelist**: Select specifically which apps NotiSpoti should listen to (pre-seeded with YouTube Music and Obsidian). Other media, social apps, and video players are completely ignored.
- 🔘 **Master Sync Engine Switch**: Toggle sync ON or OFF with a single tap. Pause it whenever you are not sharing notes.
- 🔍 **Zero-Auth Spotify Pathfinder Resolver**: Fast track matcher that uses Spotify's GraphQL Pathfinder API to find track URIs without requiring Spotify user authentication tokens.
- 🔒 **Privacy Focused**: Operates locally on your device with no external proxy servers or credentials collected.

---

## 🚀 Quick Start Guide

### Step 1: Download & Install
Download the latest `NotiSpoti.apk` from the [GitHub Releases](https://github.com/purval26/NotiSpoti/releases) page and install it on your Android device (Android 8.0+ / SDK 26–34+ supported).

---

### Step 2: Create a Spotify Developer App (2 Minutes)
To allow NotiSpoti to communicate with Spotify on your device, you need a free Client ID from Spotify's Developer Dashboard:

1. Log in to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Click **Create an App**.
3. Fill in:
   - **App Name**: `NotiSpoti`
   - **App Description**: `Music sync bridge`
   - **Redirect URI**: `com.notispoti.app://callback`
4. Under **Which API/SDKs are you planning to use?**, check **App Remote** and **Web API**.
5. Under **Android Configuration**, add:
   - **Package Name**: `com.notispoti.app`
   - **SHA-1 Fingerprint**: `C8:DD:03:A6:25:54:60:C1:5D:D1:B6:E3:F0:31:D1:2F:84:1B:59:AF`
6. Click **Save**, then copy your **Client ID**.

---

### Step 3: Setup NotiSpoti
1. Open **NotiSpoti**.
2. Tap **"Enable Notification Listener"** and enable NotiSpoti in Android Settings.
3. Paste your Spotify **Client ID** into the text field and tap **Connect**.
4. Spotify will display a one-time authorization dialog (*"Authorize NotiSpoti to control Spotify"*). Tap **Agree**.
5. Once authorized, the status will show **`SDK State: Connected & Ready`**.

---

### Step 4: Configure Source Apps
1. In NotiSpoti, tap **"Select Source Apps"**.
2. Check the apps you want to listen to (e.g., YouTube Music, Obsidian, VLC, etc.).
3. Tap **Save** or **← Back**.

---

### Step 5: Android Background Optimization (Important!)
To ensure Android's battery manager doesn't kill Spotify while it's in the background:
1. Go to Android **Settings** → **Apps** → **Spotify** → **Battery** → Select **Unrestricted**.
2. Open Android Recent Apps / App Switcher overview, tap/long-press the Spotify app icon, and select **Lock** (padlock icon).

**You're all set!** Whenever you play a track in YouTube Music or Obsidian, NotiSpoti will automatically match and play it on Spotify silently. Open Instagram Notes, tap "Share from Spotify", and your music is there!

---

## 🛠️ How to Build from Source (Termux / Linux CLI)

NotiSpoti is designed to be built completely standalone using native Android SDK CLI tools (`aapt2`, `javac`, `d8`, `jar`, `apksigner`):

```bash
# Clone the repository
git clone https://github.com/purval26/NotiSpoti.git
cd NotiSpoti

# Run the build script
bash notispoti_app/build_apk.sh
```

The compiled and signed APK will be generated at `./NotiSpoti.apk`.

To install directly to your device via Termux:
```bash
termux-open ./NotiSpoti.apk
```

---

## 📖 Technical Journey & Reverse Engineering

For an in-depth breakdown of how we reverse-engineered the Spotify Web Player, tested headless virtual playback, bypassed edge firewalls, and solved Android 15/16 background IPC constraints, read our complete technical report:

👉 [**How We Achieved the Goal (HOW_WE_ACHIEVED_THE_GOAL.md)**](HOW_WE_ACHIEVED_THE_GOAL.md)

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

*Disclaimer: NotiSpoti is an independent open-source project and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Spotify AB, Instagram, Google, or any of their subsidiaries.*
