package com.notispoti.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;

public class SpotifyPlaybackDispatcher {

    public static final String PREFS_NAME = "NotiSpotiPrefs";
    public static final String KEY_CLIENT_ID = "spotify_client_id";
    public static final String REDIRECT_URI = "com.notispoti.app://callback";

    private static SpotifyAppRemote mSpotifyAppRemote = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean isConnecting = false;

    public interface SdkCallback {
        void onSuccess();
        void onError(String message);
    }

    public static synchronized boolean isConnected() {
        return mSpotifyAppRemote != null && mSpotifyAppRemote.isConnected();
    }

    public static synchronized void connect(final Context context, final String clientId, final SdkCallback callback) {
        if (clientId == null || clientId.trim().isEmpty()) {
            AppLogger.log(">> [SDK] Client ID is empty. Please configure Client ID.");
            AppLogger.updateSdkState("SDK State: Missing Client ID", 0xFFFF5252);
            if (callback != null) callback.onError("Empty Client ID");
            return;
        }

        if (mSpotifyAppRemote != null && mSpotifyAppRemote.isConnected()) {
            AppLogger.log(">> [SDK] Already connected!");
            AppLogger.updateSdkState("SDK State: Connected & Ready", 0xFF00E676);
            if (callback != null) callback.onSuccess();
            return;
        }

        if (isConnecting) {
            AppLogger.log(">> [SDK] Connection already in progress...");
            return;
        }

        isConnecting = true;
        SpotifyAppRemote.setDebugMode(true);
        final String cleanId = clientId.trim();
        AppLogger.log(">> [SDK] Connecting to Spotify App Remote with Client ID: " + cleanId.substring(0, Math.min(8, cleanId.length())) + "...");
        AppLogger.updateSdkState("SDK State: Connecting...", 0xFFFFCA28);

        // Safety timeout to reset isConnecting
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnecting) {
                    isConnecting = false;
                    AppLogger.log(">> [SDK Timeout] Connection timed out. Make sure Spotify is open in background and tap Connect.");
                    AppLogger.updateSdkState("SDK State: Connection Timeout", 0xFFFF5252);
                }
            }
        }, 15000);

        ConnectionParams connectionParams = new ConnectionParams.Builder(cleanId)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(true)
                .build();

        Context targetContext = (context instanceof android.app.Activity) ? context : context.getApplicationContext();
        SpotifyAppRemote.connect(targetContext, connectionParams, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                isConnecting = false;
                mSpotifyAppRemote = spotifyAppRemote;
                AppLogger.log(">> [SDK CONNECTED] Successfully bound to Spotify background IPC! Ready for 100% headless playback.");
                AppLogger.updateSdkState("SDK State: Connected & Ready", 0xFF00E676);

                // Subscribe to player state silently
                try {
                    mSpotifyAppRemote.getPlayerApi().subscribeToPlayerState().setEventCallback(new Subscription.EventCallback<PlayerState>() {
                        @Override
                        public void onEvent(PlayerState playerState) {
                            if (playerState != null) {
                                isPlaying = !playerState.isPaused;
                                if (playerState.track != null) {
                                    AppLogger.log(">> [SDK State] Playing: " + playerState.track.name + " by " + playerState.track.artist.name);
                                    AppLogger.updateState(null, playerState.track.uri, "Playing: " + playerState.track.name);
                                }
                            }
                        }
                    });
                } catch (Exception ignored) {}

                if (callback != null) callback.onSuccess();
            }

            @Override
            public void onFailure(Throwable throwable) {
                isConnecting = false;
                mSpotifyAppRemote = null;
                String msg = (throwable != null) ? throwable.getMessage() : "Unknown error";
                AppLogger.log(">> [SDK CONNECTION FAILED] " + msg);
                AppLogger.updateSdkState("SDK State: Failed (" + msg + ")", 0xFFFF5252);
                if (callback != null) callback.onError(msg);
            }
        });
    }

    public static void disconnect() {
        if (mSpotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
            mSpotifyAppRemote = null;
            AppLogger.updateSdkState("SDK State: Disconnected", 0xFFFFCA28);
        }
    }

    public static void dispatch(final Context context, final ComponentName listenerComponent, final String spotifyUri, final String trackName) {
        if (spotifyUri == null || spotifyUri.trim().isEmpty()) {
            AppLogger.log("Dispatcher error: empty Spotify URI");
            return;
        }

        final String cleanUri = spotifyUri.trim();
        AppLogger.log("Dispatching track: " + trackName + " (" + cleanUri + ")");
        AppLogger.updateState(null, cleanUri, "Dispatching...");

        // Check if App Remote SDK is connected
        if (isConnected()) {
            playViaSdk(cleanUri, trackName);
            return;
        }

        // Try to auto-connect using saved Client ID
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedClientId = prefs.getString(KEY_CLIENT_ID, "");

        if (!savedClientId.isEmpty()) {
            AppLogger.log(">> [SDK] Auto-connecting with saved Client ID...");
            connect(context, savedClientId, new SdkCallback() {
                @Override
                public void onSuccess() {
                    playViaSdk(cleanUri, trackName);
                }

                @Override
                public void onError(String message) {
                    AppLogger.log(">> [SDK Error] Auto-connect failed: " + message);
                }
            });
        } else {
            AppLogger.log(">> [ACTION NEEDED] Spotify App Remote Client ID not set.");
            AppLogger.log(">> Please paste your Spotify Client ID on the dashboard to enable uninterrupted headless playback.");
            AppLogger.updateState(null, cleanUri, "Missing Client ID");
        }
    }

    private static void playViaSdk(final String spotifyUri, final String trackName) {
        if (!isConnected()) return;

        AppLogger.log(">> [SDK] Sending silent background command: play(" + spotifyUri + ")");
        mSpotifyAppRemote.getPlayerApi().play(spotifyUri)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty data) {
                        AppLogger.log(">> [SDK SUCCESS] Track started playing silently in the background!");
                        AppLogger.updateState(null, spotifyUri, "Playing Headlessly via SDK");
                    }
                })
                .setErrorCallback(new ErrorCallback() {
                    @Override
                    public void onError(Throwable throwable) {
                        String msg = (throwable != null) ? throwable.getMessage() : "Playback failed";
                        AppLogger.log(">> [SDK Play Error] " + msg);
                        AppLogger.updateState(null, spotifyUri, "Play Error: " + msg);
                    }
                });
    }

    private static boolean isPlaying = false;

    public static boolean isPlaying() {
        return isPlaying;
    }

    public static void pause() {
        if (isConnected() && mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi().pause();
            isPlaying = false;
        }
    }

    public static void resume() {
        if (isConnected() && mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi().resume();
            isPlaying = true;
        }
    }

    public static void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            resume();
        }
    }

    public static void skipNext() {
        if (isConnected() && mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi().skipNext();
        }
    }

    public static void skipPrevious() {
        if (isConnected() && mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi().skipPrevious();
        }
    }
}
