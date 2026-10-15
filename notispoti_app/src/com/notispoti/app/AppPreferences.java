package com.notispoti.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AppPreferences {

    public static final String PREFS_NAME = "NotiSpotiPrefs";
    public static final String KEY_SELECTED_PACKAGES = "selected_packages";

    public static final Set<String> DEFAULT_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.youtube.music",
            "md.obsidian",
            "com.example.obsidian"
    ));

    public static Set<String> getSelectedPackages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SELECTED_PACKAGES)) {
            // First run: save and return defaults
            saveSelectedPackages(context, DEFAULT_PACKAGES);
            return new HashSet<>(DEFAULT_PACKAGES);
        }
        Set<String> saved = prefs.getStringSet(KEY_SELECTED_PACKAGES, null);
        if (saved == null) {
            return new HashSet<>(DEFAULT_PACKAGES);
        }
        return new HashSet<>(saved);
    }

    public static void saveSelectedPackages(Context context, Set<String> packages) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(packages)).apply();
    }

    public static boolean isPackageWhitelisted(Context context, String pkg) {
        if (pkg == null) return false;
        Set<String> selected = getSelectedPackages(context);
        return selected.contains(pkg);
    }

    public static final String KEY_SYNC_ENABLED = "sync_enabled";

    public static boolean isSyncEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SYNC_ENABLED, true);
    }

    public static void setSyncEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply();
    }
}
