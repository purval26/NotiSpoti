package com.notispoti.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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

    // Header & Views
    private ImageButton btnTopSettings;
    private LinearLayout viewHomeTab;
    private LinearLayout viewSettingsTab;
    private Button btnBackFromSettings;

    // Main Hero Sync Card
    private TextView heroSyncBadge;
    private TextView heroSyncSubStatus;
    private Switch masterSyncSwitch;

    // Now Playing Card
    private TextView detectedSongText;
    private TextView detectedArtistText;
    private TextView syncedBadgeText;

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

    // Connection Status Cards
    private LinearLayout tileNotificationAccess;
    private TextView textNotificationStatus;
    private TextView textNotificationDesc;
    private TextView textNotificationAction;

    private LinearLayout tileSpotifyConnection;
    private TextView textSpotifyStatus;
    private TextView textSpotifyDesc;
    private TextView textSpotifyAction;

    // Primary Action Bottom Banner
    private LinearLayout bannerMasterSync;
    private ImageView imgMasterSyncIcon;
    private TextView textMasterSyncTitle;
    private TextView textMasterSyncSub;

    // Settings & Developer Tools
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
        setupHeaderAndSettings();
        setupHeroAndMasterSync();
        setupSourceAppTiles();
        setupStatusTiles();
        setupDeveloperControls();

        AppLogger.log("NotiSpoti initialized. Ready for silent background sync.");
    }

    private void bindViews() {
        btnTopSettings = findViewById(R.id.btnTopSettings);
        viewHomeTab = findViewById(R.id.viewHomeTab);
        viewSettingsTab = findViewById(R.id.viewSettingsTab);
        btnBackFromSettings = findViewById(R.id.btnBackFromSettings);

        heroSyncBadge = findViewById(R.id.heroSyncBadge);
        heroSyncSubStatus = findViewById(R.id.heroSyncSubStatus);
        masterSyncSwitch = findViewById(R.id.masterSyncSwitch);

        detectedSongText = findViewById(R.id.detectedSongText);
        detectedArtistText = findViewById(R.id.detectedArtistText);
        syncedBadgeText = findViewById(R.id.syncedBadgeText);

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
        textNotificationDesc = findViewById(R.id.textNotificationDesc);
        textNotificationAction = findViewById(R.id.textNotificationAction);

        tileSpotifyConnection = findViewById(R.id.tileSpotifyConnection);
        textSpotifyStatus = findViewById(R.id.textSpotifyStatus);
        textSpotifyDesc = findViewById(R.id.textSpotifyDesc);
        textSpotifyAction = findViewById(R.id.textSpotifyAction);

        bannerMasterSync = findViewById(R.id.bannerMasterSync);
        imgMasterSyncIcon = findViewById(R.id.imgMasterSyncIcon);
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

    private void setupHeaderAndSettings() {
        btnTopSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewHomeTab.setVisibility(View.GONE);
                viewSettingsTab.setVisibility(View.VISIBLE);
            }
        });

        btnBackFromSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewSettingsTab.setVisibility(View.GONE);
                viewHomeTab.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupHeroAndMasterSync() {
        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        masterSyncSwitch.setChecked(isSyncOn);
        updateMasterSyncUI(isSyncOn);

        masterSyncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppPreferences.setSyncEnabled(MainActivity.this, isChecked);
                updateMasterSyncUI(isChecked);
                AppLogger.log(">> Master Sync Engine: " + (isChecked ? "ACTIVATED" : "PAUSED (OFF)"));
            }
        });

        bannerMasterSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean nextState = !AppPreferences.isSyncEnabled(MainActivity.this);
                AppPreferences.setSyncEnabled(MainActivity.this, nextState);
                masterSyncSwitch.setChecked(nextState);
                updateMasterSyncUI(nextState);
            }
        });
    }

    private void updateMasterSyncUI(boolean enabled) {
        if (heroSyncBadge == null || heroSyncSubStatus == null || bannerMasterSync == null) return;

        if (enabled) {
            heroSyncBadge.setText("● ON");
            heroSyncBadge.setBackgroundResource(R.drawable.bg_m3_chip_active);
            heroSyncBadge.setTextColor(0xFF1ED760);

            heroSyncSubStatus.setText("Syncing automatically");
            heroSyncSubStatus.setTextColor(0xFF1ED760);

            bannerMasterSync.setBackgroundResource(R.drawable.bg_master_active_banner);
            if (imgMasterSyncIcon != null) {
                imgMasterSyncIcon.setColorFilter(0xFF000000);
            }
            textMasterSyncTitle.setText("SYNC IS ACTIVE");
            textMasterSyncTitle.setTextColor(0xFF000000);
            textMasterSyncSub.setText("Listening and syncing automatically");
            textMasterSyncSub.setTextColor(0xFF0A3816);
        } else {
            heroSyncBadge.setText("○ OFF");
            heroSyncBadge.setBackgroundResource(R.drawable.bg_m3_chip_inactive);
            heroSyncBadge.setTextColor(0xFFFF5252);

            heroSyncSubStatus.setText("Sync is paused");
            heroSyncSubStatus.setTextColor(0xFFFF5252);

            bannerMasterSync.setBackgroundResource(R.drawable.bg_master_paused_banner);
            if (imgMasterSyncIcon != null) {
                imgMasterSyncIcon.setColorFilter(0xFF1ED760);
            }
            textMasterSyncTitle.setText("START SYNC");
            textMasterSyncTitle.setTextColor(0xFFFFFFFF);
            textMasterSyncSub.setText("Start listening for music");
            textMasterSyncSub.setTextColor(0xFF8E8E93);
        }
    }

    private void setupSourceAppTiles() {
        btnManageApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppSelectionActivity.class));
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
        Set<String> selected = new HashSet<>(AppPreferences.getSelectedPackages(this));
        if (selected.contains(pkg)) {
            selected.remove(pkg);
        } else {
            selected.add(pkg);
        }
        AppPreferences.saveSelectedPackages(this, selected);
        updateSourceBadges();
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
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });

        tileSpotifyConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String clientId = inputClientId.getText().toString().trim();
                if (TextUtils.isEmpty(clientId)) {
                    // Open settings so user can paste Client ID
                    viewHomeTab.setVisibility(View.GONE);
                    viewSettingsTab.setVisibility(View.VISIBLE);
                    inputClientId.requestFocus();
                    Toast.makeText(MainActivity.this, "Please enter your Spotify Client ID", Toast.LENGTH_SHORT).show();
                } else {
                    saveAndConnectSpotify();
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
                saveAndConnectSpotify();
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

    private void saveAndConnectSpotify() {
        String clientId = inputClientId.getText().toString().trim();
        if (TextUtils.isEmpty(clientId)) {
            AppLogger.log("Please enter a valid Spotify Client ID.");
            Toast.makeText(this, "Please enter a valid Spotify Client ID", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(SpotifyPlaybackDispatcher.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(SpotifyPlaybackDispatcher.KEY_CLIENT_ID, clientId).apply();
        AppLogger.log("Saved Client ID. Connecting to App Remote...");
        SpotifyPlaybackDispatcher.connect(MainActivity.this, clientId, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        updateSourceBadges();

        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        if (masterSyncSwitch != null) {
            masterSyncSwitch.setChecked(isSyncOn);
        }
        updateMasterSyncUI(isSyncOn);

        updateSpotifyStatusDisplay();
        AppLogger.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLogger.setListener(null);
    }

    private void checkPermissions() {
        boolean enabled = isNotificationListenerEnabled();
        if (enabled) {
            textNotificationStatus.setText("• Enabled");
            textNotificationStatus.setTextColor(0xFF1ED760);
            textNotificationDesc.setText("NotiSpoti can detect music from your selected apps");
            textNotificationAction.setText("✓");
            textNotificationAction.setTextColor(0xFF1ED760);
        } else {
            textNotificationStatus.setText("• Needs permission");
            textNotificationStatus.setTextColor(0xFFFFAB00);
            textNotificationDesc.setText("Allow notification access to detect your music");
            textNotificationAction.setText("[Enable]");
            textNotificationAction.setTextColor(0xFF1ED760);
        }
    }

    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        String myPackage = getPackageName();
        return (enabledListeners != null && enabledListeners.contains(myPackage));
    }

    private void updateSpotifyStatusDisplay() {
        boolean connected = SpotifyPlaybackDispatcher.isConnected();
        if (connected) {
            textSpotifyStatus.setText("• Connected");
            textSpotifyStatus.setTextColor(0xFF1ED760);
            textSpotifyDesc.setText("Ready to sync music");
            textSpotifyAction.setText("✓");
            textSpotifyAction.setTextColor(0xFF1ED760);
        } else {
            textSpotifyStatus.setText("• Not connected");
            textSpotifyStatus.setTextColor(0xFFFFAB00);
            textSpotifyDesc.setText("Connect Spotify to start syncing");
            textSpotifyAction.setText("[Connect]");
            textSpotifyAction.setTextColor(0xFF1ED760);
        }
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
        if (song != null && !song.isEmpty()) {
            if (detectedSongText != null) {
                // Split title and artist if formatted as "Title - Artist"
                if (song.contains(" - ")) {
                    String[] parts = song.split(" - ", 2);
                    detectedSongText.setText(parts[0].trim());
                    if (detectedArtistText != null) {
                        detectedArtistText.setText(parts[1].trim());
                    }
                } else {
                    detectedSongText.setText(song);
                    if (detectedArtistText != null) {
                        detectedArtistText.setText("");
                    }
                }
            }
            if (syncedBadgeText != null) {
                syncedBadgeText.setText("Synced to Spotify ✓");
            }
        } else {
            if (detectedSongText != null) {
                detectedSongText.setText("No music detected");
            }
            if (detectedArtistText != null) {
                detectedArtistText.setText("Play something in one of your selected apps.");
            }
            if (syncedBadgeText != null) {
                syncedBadgeText.setText("Ready to sync");
            }
        }
    }

    @Override
    public void onSdkStateUpdated(String statusText, int color) {
        updateSpotifyStatusDisplay();
    }
}
