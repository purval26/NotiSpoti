package com.notispoti.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSelectionActivity extends Activity {

    public static class AppItem {
        public final String label;
        public final String packageName;
        public final Drawable icon;

        public AppItem(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private Button btnBack;
    private Button btnSave;
    private Button btnResetDefaults;
    private Button btnClearAll;
    private EditText inputSearchApp;
    private TextView selectedCountText;
    private ProgressBar loadingProgress;
    private ListView appListView;

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();

    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        btnResetDefaults = findViewById(R.id.btnResetDefaults);
        btnClearAll = findViewById(R.id.btnClearAll);
        inputSearchApp = findViewById(R.id.inputSearchApp);
        selectedCountText = findViewById(R.id.selectedCountText);
        loadingProgress = findViewById(R.id.loadingProgress);
        appListView = findViewById(R.id.appListView);

        // Load initially selected packages
        selectedPackages.addAll(AppPreferences.getSelectedPackages(this));
        updateCountDisplay();

        adapter = new AppAdapter(this, filteredApps, selectedPackages);
        appListView.setAdapter(adapter);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndFinish();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAndFinish();
            }
        });

        btnResetDefaults.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedPackages.clear();
                selectedPackages.addAll(AppPreferences.DEFAULT_PACKAGES);
                AppPreferences.saveSelectedPackages(AppSelectionActivity.this, selectedPackages);
                updateCountDisplay();
                sortAndFilter(inputSearchApp.getText().toString());
                Toast.makeText(AppSelectionActivity.this, "Reset to default music sources", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedPackages.clear();
                AppPreferences.saveSelectedPackages(AppSelectionActivity.this, selectedPackages);
                updateCountDisplay();
                adapter.notifyDataSetChanged();
            }
        });

        inputSearchApp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadInstalledApps();
    }

    private void updateCountDisplay() {
        if (selectedCountText != null) {
            selectedCountText.setText("Selected: " + selectedPackages.size());
        }
    }

    private void saveAndFinish() {
        AppPreferences.saveSelectedPackages(this, selectedPackages);
        AppLogger.log("Updated source app whitelist: " + selectedPackages.size() + " apps selected.");
        finish();
    }

    private void loadInstalledApps() {
        loadingProgress.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                // Collect launcher apps to identify user-facing apps
                Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
                launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> launcherList = pm.queryIntentActivities(launcherIntent, 0);
                Set<String> launcherPackages = new HashSet<>();
                for (ResolveInfo ri : launcherList) {
                    if (ri.activityInfo != null) {
                        launcherPackages.add(ri.activityInfo.packageName);
                    }
                }

                final List<AppItem> items = new ArrayList<>();
                for (ApplicationInfo appInfo : installed) {
                    String pkg = appInfo.packageName;
                    if (pkg.equals(getPackageName()) || pkg.equals("com.spotify.music")) {
                        // Exclude self and Spotify from source apps list
                        continue;
                    }

                    boolean isUserApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                    boolean isLauncherApp = launcherPackages.contains(pkg);
                    boolean isSelected = selectedPackages.contains(pkg);
                    boolean isLikelyMedia = pkg.contains("music") || pkg.contains("audio") || pkg.contains("youtube") || pkg.contains("obsidian");

                    // Show launcher apps, user installed apps, or selected/media apps
                    if (isLauncherApp || isUserApp || isSelected || isLikelyMedia) {
                        CharSequence labelCs = appInfo.loadLabel(pm);
                        String label = (labelCs != null) ? labelCs.toString() : pkg;
                        Drawable icon = appInfo.loadIcon(pm);
                        items.add(new AppItem(label, pkg, icon));
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingProgress.setVisibility(View.GONE);
                        allApps.clear();
                        allApps.addAll(items);
                        sortAndFilter(inputSearchApp.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void sortAndFilter(String query) {
        // Sort: selected apps first, then alphabetically by app name
        Collections.sort(allApps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem o1, AppItem o2) {
                boolean s1 = selectedPackages.contains(o1.packageName);
                boolean s2 = selectedPackages.contains(o2.packageName);
                if (s1 && !s2) return -1;
                if (!s1 && s2) return 1;
                return o1.label.compareToIgnoreCase(o2.label);
            }
        });

        filterApps(query);
    }

    private void filterApps(String query) {
        filteredApps.clear();
        String q = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (AppItem item : allApps) {
            if (q.isEmpty() ||
                item.label.toLowerCase(Locale.ROOT).contains(q) ||
                item.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                filteredApps.add(item);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppItem> items;
        private final Set<String> selectedPackages;
        private final LayoutInflater inflater;

        public AppAdapter(Context context, List<AppItem> items, Set<String> selectedPackages) {
            this.context = context;
            this.items = items;
            this.selectedPackages = selectedPackages;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_app_select, parent, false);
                holder = new ViewHolder();
                holder.checkBox = convertView.findViewById(R.id.appCheckBox);
                holder.icon = convertView.findViewById(R.id.appIcon);
                holder.name = convertView.findViewById(R.id.appName);
                holder.pkg = convertView.findViewById(R.id.appPackage);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final AppItem item = items.get(position);
            holder.name.setText(item.label);
            holder.pkg.setText(item.packageName);
            if (item.icon != null) {
                holder.icon.setImageDrawable(item.icon);
            }

            final boolean isChecked = selectedPackages.contains(item.packageName);
            holder.checkBox.setChecked(isChecked);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedPackages.contains(item.packageName)) {
                        selectedPackages.remove(item.packageName);
                    } else {
                        selectedPackages.add(item.packageName);
                    }
                    AppPreferences.saveSelectedPackages(context, selectedPackages);
                    if (context instanceof AppSelectionActivity) {
                        ((AppSelectionActivity) context).updateCountDisplay();
                    }
                    notifyDataSetChanged();
                }
            });

            return convertView;
        }

        private static class ViewHolder {
            CheckBox checkBox;
            ImageView icon;
            TextView name;
            TextView pkg;
        }
    }
}
