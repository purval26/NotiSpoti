package com.notispoti.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity implements AppLogger.Listener {

    private static final String SHA1_FINGERPRINT = "C8:DD:03:A6:25:54:60:C1:5D:D1:B6:E3:F0:31:D1:2F:84:1B:59:AF";

    // Known source packages
    private static final String PKG_YT_MUSIC = "com.google.android.apps.youtube.music";
    private static final String PKG_OBSIDIAN = "md.obsidian";
    private static final String PKG_MUSIC = "com.android.music";
    private static final String PKG_OBSIDIAN_ALT = "com.example.obsidian";

    // Views: Top Bar
    private ImageButton btnTopSettings;
    private ImageButton btnTopHelp;

    // View Containers (Tabs)
    private LinearLayout viewHomeTab;
    private LinearLayout viewSettingsTab;

    // Bottom Navigation
    private LinearLayout tabHome;
    private LinearLayout tabSourceApps;
    private LinearLayout tabSettings;
    private ImageView iconTabHome;
    private TextView textTabHome;
    private ImageView iconTabApps;
    private TextView textTabApps;
    private ImageView iconTabSettings;
    private TextView textTabSettings;

    // Hero Card
    private TextView heroSyncBadge;

    // Now Playing Card
    private ImageView imgAlbumArt;
    private TextView syncedBadgeText;
    private TextView detectedSongText;
    private TextView detectedArtistText;
    private ProgressBar playerProgressBar;
    private TextView textTimeElapsed;
    private TextView textTimeTotal;
    private ImageButton btnPrevTrack;
    private ImageButton btnPlayPauseTrack;
    private ImageButton btnNextTrack;

    // Source Apps Section
    private Button btnManageApps;
    private LinearLayout cardSourceYt;
    private LinearLayout cardSourceObsidian;
    private LinearLayout cardSourceMusic;
    private LinearLayout cardSourceCustom;
    private ImageView checkBadgeYt;
    private ImageView checkBadgeObsidian;
    private ImageView checkBadgeMusic;
    private ImageView checkBadgeCustom;

    // Status Tiles
    private LinearLayout tileNotificationAccess;
    private TextView textNotificationStatus;
    private LinearLayout tileSpotifyConnection;
    private TextView textSpotifyStatus;

    // Big Master Sync Banner
    private LinearLayout bannerMasterSync;
    private ImageView imgMasterSyncPlay;
    private TextView textMasterSyncTitle;
    private TextView textMasterSyncSub;

    // Developer / Settings Views
    private EditText inputClientId;
    private Button btnConnectSdk;
    private Button btnCopySha1;
    private EditText inputTestSong;
    private Button btnTestResolve;
    private Button btnClearLogs;
    private ScrollView logScrollView;
    private TextView logTextView;

    private final SpotifyTrackResolver resolver = new SpotifyTrackResolver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupNavigation();
        setupHeroAndMasterSync();
        setupNowPlayingControls();
        setupSourceAppTiles();
        setupStatusTiles();
        setupDeveloperControls();

        AppLogger.log("NotiSpoti initialized. Ready for silent background sync.");
    }

    private void bindViews() {
        btnTopSettings = findViewById(R.id.btnTopSettings);
        btnTopHelp = findViewById(R.id.btnTopHelp);

        viewHomeTab = findViewById(R.id.viewHomeTab);
        viewSettingsTab = findViewById(R.id.viewSettingsTab);

        tabHome = findViewById(R.id.tabHome);
        tabSourceApps = findViewById(R.id.tabSourceApps);
        tabSettings = findViewById(R.id.tabSettings);
        iconTabHome = findViewById(R.id.iconTabHome);
        textTabHome = findViewById(R.id.textTabHome);
        iconTabApps = findViewById(R.id.iconTabApps);
        textTabApps = findViewById(R.id.textTabApps);
        iconTabSettings = findViewById(R.id.iconTabSettings);
        textTabSettings = findViewById(R.id.textTabSettings);

        heroSyncBadge = findViewById(R.id.heroSyncBadge);

        imgAlbumArt = findViewById(R.id.imgAlbumArt);
        syncedBadgeText = findViewById(R.id.syncedBadgeText);
        detectedSongText = findViewById(R.id.detectedSongText);
        detectedArtistText = findViewById(R.id.detectedArtistText);
        playerProgressBar = findViewById(R.id.playerProgressBar);
        textTimeElapsed = findViewById(R.id.textTimeElapsed);
        textTimeTotal = findViewById(R.id.textTimeTotal);
        btnPrevTrack = findViewById(R.id.btnPrevTrack);
        btnPlayPauseTrack = findViewById(R.id.btnPlayPauseTrack);
        btnNextTrack = findViewById(R.id.btnNextTrack);

        btnManageApps = findViewById(R.id.btnManageApps);
        cardSourceYt = findViewById(R.id.cardSourceYt);
        cardSourceObsidian = findViewById(R.id.cardSourceObsidian);
        cardSourceMusic = findViewById(R.id.cardSourceMusic);
        cardSourceCustom = findViewById(R.id.cardSourceCustom);
        checkBadgeYt = findViewById(R.id.checkBadgeYt);
        checkBadgeObsidian = findViewById(R.id.checkBadgeObsidian);
        checkBadgeMusic = findViewById(R.id.checkBadgeMusic);
        checkBadgeCustom = findViewById(R.id.checkBadgeCustom);

        tileNotificationAccess = findViewById(R.id.tileNotificationAccess);
        textNotificationStatus = findViewById(R.id.textNotificationStatus);
        tileSpotifyConnection = findViewById(R.id.tileSpotifyConnection);
        textSpotifyStatus = findViewById(R.id.textSpotifyStatus);

        bannerMasterSync = findViewById(R.id.bannerMasterSync);
        imgMasterSyncPlay = findViewById(R.id.imgMasterSyncPlay);
        textMasterSyncTitle = findViewById(R.id.textMasterSyncTitle);
        textMasterSyncSub = findViewById(R.id.textMasterSyncSub);

        inputClientId = findViewById(R.id.inputClientId);
        btnConnectSdk = findViewById(R.id.btnConnectSdk);
        btnCopySha1 = findViewById(R.id.btnCopySha1);
        inputTestSong = findViewById(R.id.inputTestSong);
        btnTestResolve = findViewById(R.id.btnTestResolve);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        logScrollView = findViewById(R.id.logScrollView);
        logTextView = findViewById(R.id.logTextView);
    }

    private void setupNavigation() {
        btnTopSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });

        btnTopHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelpDialog();
            }
        });

        tabHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });

        tabSourceApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AppSelectionActivity.class);
                startActivity(intent);
            }
        });

        tabSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });
    }

    private void switchTab(int tabIndex) {
        if (tabIndex == 0) {
            // Home Tab
            viewHomeTab.setVisibility(View.VISIBLE);
            viewSettingsTab.setVisibility(View.GONE);

            iconTabHome.setColorFilter(0xFF1ED760);
            textTabHome.setTextColor(0xFF1ED760);

            iconTabApps.setColorFilter(0xFF8E8E93);
            textTabApps.setTextColor(0xFF8E8E93);

            iconTabSettings.setColorFilter(0xFF8E8E93);
            textTabSettings.setTextColor(0xFF8E8E93);
        } else if (tabIndex == 2) {
            // Settings / Dev Tab
            viewHomeTab.setVisibility(View.GONE);
            viewSettingsTab.setVisibility(View.VISIBLE);

            iconTabHome.setColorFilter(0xFF8E8E93);
            textTabHome.setTextColor(0xFF8E8E93);

            iconTabApps.setColorFilter(0xFF8E8E93);
            textTabApps.setTextColor(0xFF8E8E93);

            iconTabSettings.setColorFilter(0xFF1ED760);
            textTabSettings.setTextColor(0xFF1ED760);
        }
    }

    private void setupHeroAndMasterSync() {
        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        updateMasterSyncUI(isSyncOn);

        bannerMasterSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean current = AppPreferences.isSyncEnabled(MainActivity.this);
                boolean toggled = !current;
                AppPreferences.setSyncEnabled(MainActivity.this, toggled);
                updateMasterSyncUI(toggled);
                AppLogger.log(">> Master Sync Engine toggled: " + (toggled ? "ACTIVE" : "PAUSED (OFF)"));
                Toast.makeText(MainActivity.this,
                        toggled ? "Sync is now Active" : "Sync paused",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMasterSyncUI(boolean active) {
        if (active) {
            bannerMasterSync.setBackgroundResource(R.drawable.bg_master_active_banner);
            imgMasterSyncPlay.setImageResource(R.drawable.ic_play_black);
            textMasterSyncTitle.setText("Sync is Active");
            textMasterSyncTitle.setTextColor(0xFF000000);
            textMasterSyncSub.setText("Listening and syncing automatically");
            textMasterSyncSub.setTextColor(0xFF0A3816);

            heroSyncBadge.setText("● Syncing");
            heroSyncBadge.setBackgroundResource(R.drawable.bg_m3_chip_active);
            heroSyncBadge.setTextColor(0xFF1ED760);
        } else {
            bannerMasterSync.setBackgroundResource(R.drawable.bg_master_paused_banner);
            imgMasterSyncPlay.setImageResource(R.drawable.ic_pause_white);
            textMasterSyncTitle.setText("Sync is Paused");
            textMasterSyncTitle.setTextColor(0xFFFFFFFF);
            textMasterSyncSub.setText("Tap to resume background sync");
            textMasterSyncSub.setTextColor(0xFFA0A0A0);

            heroSyncBadge.setText("○ Paused");
            heroSyncBadge.setBackgroundResource(R.drawable.bg_m3_chip_inactive);
            heroSyncBadge.setTextColor(0xFFFF5252);
        }
    }

    private void setupNowPlayingControls() {
        btnPlayPauseTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpotifyPlaybackDispatcher.togglePlayPause();
                boolean isPlaying = SpotifyPlaybackDispatcher.isPlaying();
                btnPlayPauseTrack.setImageResource(isPlaying ? R.drawable.ic_pause_white : R.drawable.ic_play_white);
            }
        });

        btnPrevTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpotifyPlaybackDispatcher.skipPrevious();
            }
        });

        btnNextTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SpotifyPlaybackDispatcher.skipNext();
            }
        });
    }

    private void setupSourceAppTiles() {
        btnManageApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AppSelectionActivity.class);
                startActivity(intent);
            }
        });

        cardSourceYt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePackage(PKG_YT_MUSIC);
            }
        });

        cardSourceObsidian.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePackage(PKG_OBSIDIAN);
            }
        });

        cardSourceMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePackage(PKG_MUSIC);
            }
        });

        cardSourceCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePackage(PKG_OBSIDIAN_ALT);
            }
        });

        updateSourceBadges();
    }

    private void togglePackage(String pkg) {
        Set<String> selected = AppPreferences.getSelectedPackages(this);
        if (selected.contains(pkg)) {
            selected.remove(pkg);
        } else {
            selected.add(pkg);
        }
        AppPreferences.saveSelectedPackages(this, selected);
        updateSourceBadges();
        Toast.makeText(this, selected.contains(pkg) ? "App added to sync" : "App removed from sync", Toast.LENGTH_SHORT).show();
    }

    private void updateSourceBadges() {
        Set<String> selected = AppPreferences.getSelectedPackages(this);
        checkBadgeYt.setVisibility(selected.contains(PKG_YT_MUSIC) ? View.VISIBLE : View.GONE);
        checkBadgeObsidian.setVisibility(selected.contains(PKG_OBSIDIAN) ? View.VISIBLE : View.GONE);
        checkBadgeMusic.setVisibility(selected.contains(PKG_MUSIC) ? View.VISIBLE : View.GONE);
        checkBadgeCustom.setVisibility(selected.contains(PKG_OBSIDIAN_ALT) ? View.VISIBLE : View.GONE);
    }

    private void setupStatusTiles() {
        tileNotificationAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            }
        });

        tileSpotifyConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs = getSharedPreferences(SpotifyPlaybackDispatcher.PREFS_NAME, Context.MODE_PRIVATE);
                String clientId = prefs.getString(SpotifyPlaybackDispatcher.KEY_CLIENT_ID, "");
                if (TextUtils.isEmpty(clientId)) {
                    switchTab(2);
                    Toast.makeText(MainActivity.this, "Please enter your Spotify Client ID", Toast.LENGTH_SHORT).show();
                    inputClientId.requestFocus();
                } else {
                    SpotifyPlaybackDispatcher.connect(MainActivity.this, clientId, null);
                }
            }
        });
    }

    private void setupDeveloperControls() {
        final SharedPreferences prefs = getSharedPreferences(SpotifyPlaybackDispatcher.PREFS_NAME, Context.MODE_PRIVATE);
        String savedClientId = prefs.getString(SpotifyPlaybackDispatcher.KEY_CLIENT_ID, "");
        if (!savedClientId.isEmpty()) {
            inputClientId.setText(savedClientId);
        }

        btnConnectSdk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String clientId = inputClientId.getText().toString().trim();
                if (TextUtils.isEmpty(clientId)) {
                    Toast.makeText(MainActivity.this, "Please enter a valid Spotify Client ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(SpotifyPlaybackDispatcher.KEY_CLIENT_ID, clientId).apply();
                AppLogger.log("Saved Client ID. Connecting to App Remote...");
                SpotifyPlaybackDispatcher.connect(MainActivity.this, clientId, null);
            }
        });

        btnCopySha1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("NotiSpoti SHA-1", SHA1_FINGERPRINT);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, "SHA-1 copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnTestResolve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = inputTestSong.getText().toString().trim();
                if (TextUtils.isEmpty(input)) {
                    input = "Blinding Lights The Weeknd";
                    inputTestSong.setText(input);
                }
                performManualTest(input);
            }
        });

        btnClearLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (logTextView != null) {
                    logTextView.setText("");
                }
            }
        });
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("How NotiSpoti Works")
                .setMessage("1. Grant Notification Access so NotiSpoti can detect song changes.\n\n" +
                        "2. Create a free app at developer.spotify.com with package 'com.notispoti.app' and redirect URI 'com.notispoti.app://callback'.\n\n" +
                        "3. Paste your Client ID and tap Save.\n\n" +
                        "4. Keep Spotify running in the background (set battery to Unrestricted).\n\n" +
                        "5. Play any song on YouTube Music or Obsidian — your Instagram Notes will update automatically!")
                .setPositiveButton("Got It", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        updateSourceBadges();
        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        updateMasterSyncUI(isSyncOn);
        AppLogger.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLogger.setListener(null);
    }

    private void checkPermissions() {
        boolean enabled = isNotificationListenerEnabled();
        if (textNotificationStatus != null) {
            textNotificationStatus.setText(enabled ? "Enabled" : "Tap to Grant");
            textNotificationStatus.setTextColor(enabled ? 0xFF1ED760 : 0xFFFFB74D);
        }
    }

    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String myPackage = getPackageName();
        return (enabledListeners != null && enabledListeners.contains(myPackage));
    }

    private void performManualTest(final String query) {
        AppLogger.log("--- Manual Test Initiated: " + query + " ---");
        AppLogger.updateState(query, "-", "Resolving via Spotify Pathfinder...");

        resolver.resolve(query, new SpotifyTrackResolver.Callback() {
            @Override
            public void onSuccess(final SpotifyTrackResolver.TrackInfo track) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AppLogger.log("Manual match success: " + track.name + " (" + track.artist + ")");
                        AppLogger.log("Spotify URI: " + track.uri);
                        AppLogger.updateState(track.name + " - " + track.artist, track.uri, "Spotify Resolved");

                        ComponentName comp = new ComponentName(MainActivity.this, MediaNotificationListenerService.class);
                        SpotifyPlaybackDispatcher.dispatch(MainActivity.this, comp, track.uri, track.name);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AppLogger.log("Manual match failed: " + message);
                        AppLogger.updateState(query, "-", "Match Failed");
                    }
                });
            }
        });
    }

    @Override
    public void onLogAdded(String line) {
        if (logTextView != null) {
            logTextView.append(line + "\n");
            if (logScrollView != null) {
                logScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        logScrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        }
    }

    @Override
    public void onStateUpdated(String song, String spotifyUri, String dispatchStatus) {
        if (song != null) {
            if (song.contains(" - ")) {
                String[] parts = song.split(" - ", 2);
                if (detectedSongText != null) detectedSongText.setText(parts[0].trim());
                if (detectedArtistText != null) detectedArtistText.setText(parts[1].trim());
            } else {
                if (detectedSongText != null) detectedSongText.setText(song);
                if (detectedArtistText != null) detectedArtistText.setText("Playing via NotiSpoti");
            }
        }
        if (syncedBadgeText != null) {
            if (dispatchStatus != null && dispatchStatus.contains("Playing")) {
                syncedBadgeText.setText("Synced to Spotify ✓");
            } else if (dispatchStatus != null) {
                syncedBadgeText.setText(dispatchStatus);
            }
        }
        if (btnPlayPauseTrack != null) {
            btnPlayPauseTrack.setImageResource(R.drawable.ic_pause_white);
        }
    }

    @Override
    public void onSdkStateUpdated(String statusText, int color) {
        if (textSpotifyStatus != null) {
            if (statusText != null && statusText.contains("Connected")) {
                textSpotifyStatus.setText("Connected");
                textSpotifyStatus.setTextColor(0xFF1ED760);
            } else {
                textSpotifyStatus.setText("Not Connected");
                textSpotifyStatus.setTextColor(0xFFFFB74D);
            }
        }
    }
}
