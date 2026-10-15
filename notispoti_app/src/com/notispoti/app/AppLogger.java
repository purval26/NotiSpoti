package com.notispoti.app;

import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLogger {

    public interface Listener {
        void onLogAdded(String line);
        void onStateUpdated(String song, String spotifyUri, String dispatchStatus);
        void onSdkStateUpdated(String statusText, int color);
    }

    private static final int MAX_LOGS = 100;
    private static final List<String> logs = new ArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Listener currentListener = null;

    private static String currentSong = "Waiting for music...";
    private static String currentSpotifyUri = "-";
    private static String currentDispatchStatus = "Idle";
    private static String currentSdkStatus = "SDK State: Not Connected";
    private static int currentSdkColor = 0xFFFFCA28; // Amber
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public static synchronized void setListener(Listener listener) {
        currentListener = listener;
        if (listener != null) {
            listener.onStateUpdated(currentSong, currentSpotifyUri, currentDispatchStatus);
            listener.onSdkStateUpdated(currentSdkStatus, currentSdkColor);
            for (String line : logs) {
                listener.onLogAdded(line);
            }
        }
    }

    public static synchronized void log(final String message) {
        String timestamp = timeFormat.format(new Date());
        final String formatted = "[" + timestamp + "] " + message;
        logs.add(formatted);
        if (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (AppLogger.class) {
                    if (currentListener != null) {
                        currentListener.onLogAdded(formatted);
                    }
                }
            }
        });
    }

    public static synchronized void updateState(final String song, final String uri, final String status) {
        if (song != null) currentSong = song;
        if (uri != null) currentSpotifyUri = uri;
        if (status != null) currentDispatchStatus = status;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (AppLogger.class) {
                    if (currentListener != null) {
                        currentListener.onStateUpdated(currentSong, currentSpotifyUri, currentDispatchStatus);
                    }
                }
            }
        });
    }

    public static synchronized void updateSdkState(final String statusText, final int color) {
        currentSdkStatus = statusText;
        currentSdkColor = color;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (AppLogger.class) {
                    if (currentListener != null) {
                        currentListener.onSdkStateUpdated(currentSdkStatus, currentSdkColor);
                    }
                }
            }
        });
    }

    public static synchronized String getCurrentSong() {
        return currentSong;
    }

    public static synchronized String getCurrentSpotifyUri() {
        return currentSpotifyUri;
    }

    public static synchronized String getCurrentDispatchStatus() {
        return currentDispatchStatus;
    }
}
