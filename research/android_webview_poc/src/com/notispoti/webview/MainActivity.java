package com.notispoti.webview;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements JsBridge.BridgeListener {

    private static final String TAG = "NotiSpoti";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private WebView webView;
    private TextView statusText;
    private TextView logText;
    private ScrollView logScrollView;
    private File logFile;
    private String defaultMobileUa;
    private boolean isDesktopUaActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        logScrollView = findViewById(R.id.logScrollView);
        webView = findViewById(R.id.invisibleWebView);

        Button btnMobileUa = findViewById(R.id.btnMobileUa);
        Button btnDesktopUa = findViewById(R.id.btnDesktopUa);

        initLogFile();
        appendLog("INIT", "NotiSpoti Invisible WebView POC Initializing...");

        setupWebView();

        btnMobileUa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendLog("USER_ACTION", "Switching to Default Android Mobile UA...");
                isDesktopUaActive = false;
                webView.getSettings().setUserAgentString(defaultMobileUa);
                appendLog("USER_AGENT", "UA: " + defaultMobileUa);
                loadSpotify();
            }
        });

        btnDesktopUa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendLog("USER_ACTION", "Switching to Desktop UA (Linux Chrome)...");
                isDesktopUaActive = true;
                webView.getSettings().setUserAgentString(DESKTOP_UA);
                appendLog("USER_AGENT", "UA: " + DESKTOP_UA);
                loadSpotify();
            }
        });

        // Test normal Android WebView UA first (Requirement 12)
        appendLog("USER_AGENT", "Initial test using Default Android Mobile UA.");
        loadSpotify();
    }

    private void initLogFile() {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File notispotiDir = new File(downloadDir, "NotiSpoti");
            if (!notispotiDir.exists()) {
                notispotiDir.mkdirs();
            }
            logFile = new File(notispotiDir, "webview_poc_log.txt");
            FileWriter fw = new FileWriter(logFile, false);
            fw.write("=== NotiSpoti WebView POC Log Started " + getTimestamp() + " ===\n");
            fw.close();
            Log.d(TAG, "Log file initialized at: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to init log file in Downloads, using internal files dir", e);
            logFile = new File(getFilesDir(), "webview_poc_log.txt");
        }
    }

    private void appendLog(final String tag, final String message) {
        final String formatted = "[" + getTimestamp() + "][" + tag + "] " + message;
        Log.d(TAG, formatted);

        // Write to file
        if (logFile != null) {
            try {
                FileWriter fw = new FileWriter(logFile, true);
                fw.write(formatted + "\n");
                fw.close();
            } catch (IOException ignored) {}
        }

        // Update UI
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (logText != null) {
                    logText.append(formatted + "\n");
                    if (logScrollView != null) {
                        logScrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                logScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                }
            }
        });
    }

    private String getTimestamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Requirements 3: JavaScript, DOM storage, cookies, media playback
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        defaultMobileUa = settings.getUserAgentString();
        appendLog("CONFIG", "Default Mobile UA: " + defaultMobileUa);

        // Expose JavaScript bridge (Requirement 46)
        JsBridge jsBridge = new JsBridge(this);
        webView.addJavascriptInterface(jsBridge, "NotiSpotiBridge");
        appendLog("CONFIG", "Exposed JavaScript interface: window.NotiSpotiBridge");

        // WebChromeClient for Console, Permissions & Media state
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                appendLog("DRM_PERMISSION", "Permission requested: " + java.util.Arrays.toString(request.getResources()));
                for (String resource : request.getResources()) {
                    if (resource.equals(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                        appendLog("DRM_PERMISSION", "Granting RESOURCE_PROTECTED_MEDIA_ID for Widevine DRM.");
                        request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                        return;
                    }
                }
                request.grant(request.getResources());
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String level = consoleMessage.messageLevel().name();
                String msg = consoleMessage.message();
                appendLog("CONSOLE_" + level, msg + " (" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + ")");

                // Proactively detect key protocol / failure indicators
                if (msg.contains("cdmerror") || msg.contains("CDM_ERROR")) {
                    appendLog("ALERT_DRM", ">>> CDM / DRM ERROR DETECTED in Web Player: " + msg);
                }
                if (msg.contains("PREMIUM_REQUIRED")) {
                    appendLog("ALERT_AUTH", ">>> PREMIUM_REQUIRED restriction logged by Spotify Web Player!");
                }
                if (msg.contains("TP_REGISTRATION_FAILED")) {
                    appendLog("ALERT_REG", ">>> Track-Playback device registration failed!");
                }
                return true;
            }
        });

        // WebViewClient for URL changes, navigation redirects, errors
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                appendLog("NAV_REDIRECT", "Navigation request: " + uri.toString());

                if (scheme != null && (scheme.equals("intent") || scheme.equals("market") || scheme.equals("spotify"))) {
                    appendLog("APP_RESTRICTION", ">>> Spotify attempted native redirect to: " + uri.toString());
                    return true; // Block native app jump to keep session in WebView
                }
                if (uri.getHost() != null && uri.getHost().contains("play.google.com")) {
                    appendLog("APP_RESTRICTION", ">>> Spotify redirected to Google Play Store: " + uri.toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                statusText.setText("Status: Loading " + url);
                appendLog("PAGE_STARTED", "Loading URL: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                statusText.setText("Status: Loaded (" + (isDesktopUaActive ? "Desktop UA" : "Mobile UA") + ")");
                appendLog("PAGE_FINISHED", "Page finished loading: " + url);

                // Inject comprehensive DOM & DRM audit script
                injectAuditScript();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    appendLog("WEB_ERROR", "Main Frame Error (" + error.getErrorCode() + "): " + error.getDescription());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (request.isForMainFrame()) {
                    appendLog("HTTP_ERROR", "HTTP " + errorResponse.getStatusCode() + " " + errorResponse.getReasonPhrase() + " on " + request.getUrl());
                }
            }
        });
    }

    private void loadSpotify() {
        appendLog("NAVIGATE", "Navigating to https://open.spotify.com ...");
        webView.loadUrl("https://open.spotify.com");
    }

    private void injectAuditScript() {
        appendLog("AUDIT", "Injecting client-side inspection script into Web Player...");

        String js = 
            "(function() {\n" +
            "  try {\n" +
            "    var title = document.title || 'Untitled';\n" +
            "    var currentUrl = window.location.href;\n" +
            "    var hasAppBanner = !!(document.querySelector('button[data-testid=\"download-button\"]') || document.querySelector('[data-testid=\"app-banner\"]') || document.querySelector('a[href*=\"spotify.com/download\"]'));\n" +
            "    var hasPlayButton = !!(document.querySelector('button[data-testid=\"play-button\"]') || document.querySelector('button[data-testid=\"control-button-play\"]'));\n" +
            "    var hasPlayerBar = !!(document.querySelector('[data-testid=\"now-playing-bar\"]') || document.querySelector('[data-testid=\"now-playing-widget\"]'));\n" +
            "    var details = 'UA: ' + navigator.userAgent.substring(0, 50);\n" +
            "    if (window.NotiSpotiBridge) {\n" +
            "      window.NotiSpotiBridge.reportDomAudit(title, currentUrl, hasAppBanner, hasPlayButton, hasPlayerBar, details);\n" +
            "    }\n" +
            "    // Test Widevine DRM support\n" +
            "    if (navigator.requestMediaKeySystemAccess) {\n" +
            "      var config = [{\n" +
            "        initDataTypes: ['cenc'],\n" +
            "        audioCapabilities: [{ contentType: 'audio/mp4; codecs=\"mp4a.40.2\"' }]\n" +
            "      }];\n" +
            "      navigator.requestMediaKeySystemAccess('com.widevine.alpha', config)\n" +
            "        .then(function(access) {\n" +
            "          if (window.NotiSpotiBridge) window.NotiSpotiBridge.reportDrmSupport(true, true, access.keySystem, 'Widevine KeySystem successfully acquired!');\n" +
            "        })\n" +
            "        .catch(function(err) {\n" +
            "          if (window.NotiSpotiBridge) window.NotiSpotiBridge.reportDrmSupport(true, false, 'com.widevine.alpha', 'Widevine rejected: ' + err.message);\n" +
            "        });\n" +
            "    } else {\n" +
            "      if (window.NotiSpotiBridge) window.NotiSpotiBridge.reportDrmSupport(false, false, 'none', 'navigator.requestMediaKeySystemAccess is missing');\n" +
            "    }\n" +
            "  } catch(e) {\n" +
            "    if (window.NotiSpotiBridge) window.NotiSpotiBridge.log('Audit exception: ' + e.message);\n" +
            "  }\n" +
            "})();";

        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                appendLog("AUDIT", "Audit script evaluation complete: " + value);
            }
        });
    }

    @Override
    public void onBridgeMessage(String tag, String message) {
        appendLog(tag, message);
    }
}
