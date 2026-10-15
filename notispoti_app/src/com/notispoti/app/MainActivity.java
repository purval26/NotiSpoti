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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity implements AppLogger.Listener {

    private static final String SHA1_FINGERPRINT = "C8:DD:03:A6:25:54:60:C1:5D:D1:B6:E3:F0:31:D1:2F:84:1B:59:AF";

    // Top Bar & Dev Mode
    private Button btnToggleDevMode;
    private LinearLayout layoutDevMode;

    // Hero Master Switch Card
    private LinearLayout masterSwitchLayout;
    private TextView masterSwitchBadge;
    private Switch masterSyncSwitch;
    private TextView masterSwitchTitle;
    private TextView masterSwitchSubtitle;

    // Live Detection Card
    private TextView detectedSongText;
    private TextView spotifyMatchText;
    private TextView dispatchStatusText;

    // Setup Checklist Cards
    private TextView permissionStatusText;
    private Button btnGrantPermission;

    private TextView sdkStatusText;
    private Button btnQuickConnectSdk;

    private TextView selectedAppsStatusText;
    private Button btnManageApps;

    // Developer Section
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

        // Top Bar & Dev Mode Toggle
        btnToggleDevMode = findViewById(R.id.btnToggleDevMode);
        layoutDevMode = findViewById(R.id.layoutDevMode);

        boolean isDev = AppPreferences.isDeveloperMode(this);
        setDevModeVisible(isDev);

        btnToggleDevMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean currentlyDev = (layoutDevMode.getVisibility() == View.VISIBLE);
                boolean newDev = !currentlyDev;
                AppPreferences.setDeveloperMode(MainActivity.this, newDev);
                setDevModeVisible(newDev);
                Toast.makeText(MainActivity.this,
                        newDev ? "Developer mode enabled" : "Developer mode hidden",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Hero Master Switch Card
        masterSwitchLayout = findViewById(R.id.masterSwitchLayout);
        masterSwitchBadge = findViewById(R.id.masterSwitchBadge);
        masterSyncSwitch = findViewById(R.id.masterSyncSwitch);
        masterSwitchTitle = findViewById(R.id.masterSwitchTitle);
        masterSwitchSubtitle = findViewById(R.id.masterSwitchSubtitle);

        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        masterSyncSwitch.setChecked(isSyncOn);
        updateMasterSwitchUI(isSyncOn);

        masterSyncSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppPreferences.setSyncEnabled(MainActivity.this, isChecked);
                updateMasterSwitchUI(isChecked);
                AppLogger.log(">> Master Sync Engine: " + (isChecked ? "ACTIVATED" : "PAUSED (OFF)"));
            }
        });

        // Live Detection Card
        detectedSongText = findViewById(R.id.detectedSongText);
        spotifyMatchText = findViewById(R.id.spotifyMatchText);
        dispatchStatusText = findViewById(R.id.dispatchStatusText);

        // Setup Checklist Cards
        permissionStatusText = findViewById(R.id.permissionStatusText);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);
        sdkStatusText = findViewById(R.id.sdkStatusText);
        btnQuickConnectSdk = findViewById(R.id.btnQuickConnectSdk);
        selectedAppsStatusText = findViewById(R.id.selectedAppsStatusText);
        btnManageApps = findViewById(R.id.btnManageApps);

        // Developer Section Controls
        inputClientId = findViewById(R.id.inputClientId);
        btnConnectSdk = findViewById(R.id.btnConnectSdk);
        btnCopySha1 = findViewById(R.id.btnCopySha1);
        inputTestSong = findViewById(R.id.inputTestSong);
        btnTestResolve = findViewById(R.id.btnTestResolve);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        logScrollView = findViewById(R.id.logScrollView);
        logTextView = findViewById(R.id.logTextView);

        btnGrantPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            }
        });

        btnManageApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AppSelectionActivity.class);
                startActivity(intent);
            }
        });

        // Load saved Client ID
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

        btnQuickConnectSdk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String clientId = inputClientId.getText().toString().trim();
                if (TextUtils.isEmpty(clientId)) {
                    // Open Developer Mode so user can input Client ID
                    setDevModeVisible(true);
                    AppPreferences.setDeveloperMode(MainActivity.this, true);
                    Toast.makeText(MainActivity.this, "Please enter your Spotify Client ID below", Toast.LENGTH_LONG).show();
                    inputClientId.requestFocus();
                } else {
                    saveAndConnectSpotify();
                }
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

        AppLogger.log("NotiSpoti initialized. Ready for silent background sync.");
    }

    private void setDevModeVisible(boolean visible) {
        if (layoutDevMode != null) {
            layoutDevMode.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (btnToggleDevMode != null) {
            btnToggleDevMode.setText(visible ? "🛠 Dev: ON" : "🛠 Dev Mode");
            btnToggleDevMode.setTextColor(visible ? 0xFF1DB954 : 0xFFA0A0A0);
        }
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
        updateSourceAppsSummary();
        boolean isSyncOn = AppPreferences.isSyncEnabled(this);
        if (masterSyncSwitch != null) {
            masterSyncSwitch.setChecked(isSyncOn);
        }
        updateMasterSwitchUI(isSyncOn);
        AppLogger.setListener(this);
    }

    private void updateMasterSwitchUI(boolean enabled) {
        if (masterSwitchTitle == null || masterSwitchSubtitle == null || masterSwitchBadge == null || masterSwitchLayout == null) return;
        if (enabled) {
            masterSwitchLayout.setBackgroundResource(R.drawable.bg_m3_card_active);
            masterSwitchBadge.setText("● ACTIVE");
            masterSwitchBadge.setBackgroundResource(R.drawable.bg_m3_chip_active);
            masterSwitchBadge.setTextColor(0xFF1DB954);
            masterSwitchTitle.setText("Silent Sync Engine: Active");
            masterSwitchSubtitle.setText("Listening to selected apps & syncing to Spotify in the background.");
        } else {
            masterSwitchLayout.setBackgroundResource(R.drawable.bg_m3_card);
            masterSwitchBadge.setText("○ PAUSED");
            masterSwitchBadge.setBackgroundResource(R.drawable.bg_m3_chip_inactive);
            masterSwitchBadge.setTextColor(0xFFFF5252);
            masterSwitchTitle.setText("Silent Sync Engine: Paused");
            masterSwitchSubtitle.setText("Sync paused. Incoming notifications will be ignored.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLogger.setListener(null);
    }

    private void updateSourceAppsSummary() {
        if (selectedAppsStatusText == null) return;
        Set<String> selected = AppPreferences.getSelectedPackages(this);
        if (selected == null || selected.isEmpty()) {
            selectedAppsStatusText.setText("No source apps selected. Sync is paused.");
            selectedAppsStatusText.setTextColor(0xFFFFAB00);
            return;
        }

        android.content.pm.PackageManager pm = getPackageManager();
        List<String> names = new ArrayList<>();
        for (String pkg : selected) {
            try {
                android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(info);
                names.add((label != null) ? label.toString() : pkg);
            } catch (Exception e) {
                if (pkg.contains(".")) {
                    names.add(pkg.substring(pkg.lastIndexOf('.') + 1));
                } else {
                    names.add(pkg);
                }
            }
        }

        String preview = TextUtils.join(", ", names);
        if (preview.length() > 50) {
            preview = preview.substring(0, 47) + "...";
        }
        selectedAppsStatusText.setText("Syncing from " + selected.size() + " app" + (selected.size() == 1 ? "" : "s") + " (" + preview + ")");
        selectedAppsStatusText.setTextColor(0xFF00E676);
    }

    private void checkPermissions() {
        boolean enabled = isNotificationListenerEnabled();
        if (enabled) {
            permissionStatusText.setText("● Granted & Active");
            permissionStatusText.setTextColor(0xFF00E676);
            btnGrantPermission.setVisibility(View.GONE);
        } else {
            permissionStatusText.setText("▲ Action Required: Grant Access");
            permissionStatusText.setTextColor(0xFFFFAB00);
            btnGrantPermission.setVisibility(View.VISIBLE);
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
        if (detectedSongText != null && song != null) {
            detectedSongText.setText(song);
        }
        if (spotifyMatchText != null && spotifyUri != null) {
            spotifyMatchText.setText("Spotify URI: " + spotifyUri);
        }
        if (dispatchStatusText != null && dispatchStatus != null) {
            dispatchStatusText.setText(dispatchStatus);
        }
    }

    @Override
    public void onSdkStateUpdated(String statusText, int color) {
        if (sdkStatusText != null) {
            sdkStatusText.setText(statusText);
            sdkStatusText.setTextColor(color);
        }
        if (btnQuickConnectSdk != null) {
            if (statusText != null && statusText.contains("Connected")) {
                btnQuickConnectSdk.setText("Connected ✓");
                btnQuickConnectSdk.setEnabled(false);
            } else {
                btnQuickConnectSdk.setText("Connect");
                btnQuickConnectSdk.setEnabled(true);
            }
        }
    }
}
