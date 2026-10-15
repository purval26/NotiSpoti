package com.notispoti.app;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class MediaNotificationListenerService extends NotificationListenerService {

    private final SpotifyTrackResolver resolver = new SpotifyTrackResolver();
    private String lastTitle = "";
    private String lastArtist = "";
    private long lastDispatchTime = 0;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        AppLogger.log("MediaNotificationListenerService CONNECTED & active!");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        AppLogger.log("MediaNotificationListenerService DISCONNECTED.");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!AppPreferences.isSyncEnabled(this)) {
            return;
        }

        if (sbn == null || sbn.getNotification() == null) return;

        String pkg = sbn.getPackageName();
        if (pkg == null) return;

        // Ignore Spotify notifications to avoid feedback loop
        if ("com.spotify.music".equals(pkg)) {
            return;
        }

        // Ignore our own app
        if (getPackageName().equals(pkg)) {
            return;
        }

        // Check if package is in user-selected whitelist
        if (!AppPreferences.isPackageWhitelisted(this, pkg)) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (titleCs == null || titleCs.length() == 0) return;

        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subTextCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        final String title = cleanMetadata(titleCs.toString());
        final String artist = cleanMetadata((textCs != null) ? textCs.toString() : "");

        if (title.isEmpty()) return;

        // Prevent rapid re-triggering for seekbar/progress updates
        long now = System.currentTimeMillis();
        if (title.equalsIgnoreCase(lastTitle) && artist.equalsIgnoreCase(lastArtist) && (now - lastDispatchTime < 15000)) {
            return;
        }

        lastTitle = title;
        lastArtist = artist;
        lastDispatchTime = now;

        final String songDisplay = artist.isEmpty() ? title : (title + " - " + artist);
        AppLogger.log("♪ Detected playing song [" + pkg + "]: " + songDisplay);
        AppLogger.updateState(songDisplay, null, "Resolving Spotify match...");

        final String query = artist.isEmpty() ? title : (title + " " + artist);

        resolver.resolve(query, new SpotifyTrackResolver.Callback() {
            @Override
            public void onSuccess(final SpotifyTrackResolver.TrackInfo track) {
                AppLogger.log("Matched Spotify: " + track.name + " (" + track.artist + ") → " + track.uri);
                AppLogger.updateState(songDisplay, track.uri, "Spotify Track Resolved");

                ComponentName myComponent = new ComponentName(getApplicationContext(), MediaNotificationListenerService.class);
                SpotifyPlaybackDispatcher.dispatch(getApplicationContext(), myComponent, track.uri, track.name);
            }

            @Override
            public void onError(final String message) {
                AppLogger.log("Spotify resolver: " + message);
                AppLogger.updateState(songDisplay, "-", "No Spotify match found");
            }
        });
    }

    private String cleanMetadata(String raw) {
        if (raw == null) return "";
        // Strip common suffixes like (Official Video), [Official Audio], etc.
        String cleaned = raw.replaceAll("(?i)\\s*[\\[\\(](official\\s*(video|audio|music\\s*video)?|lyrics|hd|4k)[\\]\\)]", "");
        return cleaned.trim();
    }
}
