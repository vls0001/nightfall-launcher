package dev.nightfall.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG_ALL = "__all";
    private static final String TAG_SETTINGS = "__settings";

    private AppStore store;
    private List<AppStore.App> apps = new ArrayList<AppStore.App>();
    private List<String> dockPkgs = new ArrayList<String>();

    private TextView dateView;
    private ClockView clockView;
    private TextView labelView;
    private HorizontalScrollView dockScroll;
    private LinearLayout dockRow;
    private FrameLayout appsPanel;
    private GridView appsGrid;
    private AppsAdapter appsAdapter;

    private Typeface clockFace = Typeface.DEFAULT_BOLD;
    private Typeface uiFace = Typeface.DEFAULT;
    private float density;
    private String focusAfterRebuild;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        store = new AppStore(this);
        try {
            clockFace = Typeface.createFromAsset(getAssets(), "clock.ttf");
            uiFace = Typeface.createFromAsset(getAssets(), "ui.ttf");
        } catch (Exception ignored) {
        }

        FrameLayout root = new FrameLayout(this);
        root.addView(new SceneView(this), match());
        root.addView(buildHome(), match());
        appsPanel = buildAppsPanel();
        appsPanel.setVisibility(View.GONE);
        root.addView(appsPanel, match());
        setContentView(root);

        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);

        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packageReceiver, pkgs);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(timeReceiver);
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        updateClock();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: go back to the start of the row.
        if (appsPanel.getVisibility() == View.VISIBLE) hideApps();
        dockScroll.smoothScrollTo(0, 0);
        if (dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (appsPanel.getVisibility() == View.VISIBLE) hideApps();
        // Otherwise stay on the home screen.
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------- Home screen ----------

    private View buildHome() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, dp(58), 0, 0);
        col.setClipChildren(false);

        dateView = new TextView(this);
        dateView.setTypeface(uiFace);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        dateView.setTextColor(0xEEFFFFFF);
        dateView.setShadowLayer(10, 0, 2, 0xAA000000);
        col.addView(dateView, wrap());

        clockView = new ClockView(this);
        clockView.setTypeface(clockFace);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 130);
        clockView.setIncludeFontPadding(false);
        clockView.setLetterSpacing(-0.02f);
        clockView.setShadowLayer(dp(14), 0, 0, 0x8870E0D2);
        clockView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = dp(2);
        col.addView(clockView, clp);

        // Frosted glass shelf holding the app tiles
        FrameLayout shelf = new FrameLayout(this);
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(dp(30));
        glass.setColor(0x332F6A63);
        glass.setStroke(Math.max(1, dp(1.5f)), 0x8899E8DE);
        shelf.setBackground(glass);
        shelf.setPadding(dp(4), dp(4), dp(4), dp(4));
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false);

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setPadding(dp(14), 0, dp(14), 0);
        dockScroll.setSmoothScrollingEnabled(true);

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(0, dp(10), 0, dp(10));
        dockScroll.addView(dockRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shelf.addView(dockScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(26);
        slp.leftMargin = dp(36);
        slp.rightMargin = dp(36);
        col.addView(shelf, slp);

        labelView = new TextView(this);
        labelView.setTypeface(uiFace);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        labelView.setTextColor(0xF2FFFFFF);
        labelView.setShadowLayer(10, 0, 2, 0xCC000000);
        LinearLayout.LayoutParams llp = wrap();
        llp.topMargin = dp(12);
        col.addView(labelView, llp);
        return col;
    }

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        clockView.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
    }

    private void refresh() {
        apps = store.loadApps();
        dockPkgs = store.dock(apps);
        List<String> present = new ArrayList<String>();
        for (String p : dockPkgs) if (AppStore.find(apps, p) != null) present.add(p);
        if (present.size() != dockPkgs.size()) {
            dockPkgs = present;
            store.saveDock(dockPkgs);
        }
        rebuildDock();
        if (appsAdapter != null) appsAdapter.notifyDataSetChanged();
    }

    private void rebuildDock() {
        String want = focusAfterRebuild;
        focusAfterRebuild = null;
        View cur = getCurrentFocus();
        if (want == null && cur != null && cur.getParent() == dockRow) want = (String) cur.getTag();

        dockRow.removeAllViews();
        for (String p : dockPkgs) {
            AppStore.App app = AppStore.find(apps, p);
            if (app != null) dockRow.addView(appTile(app), tileParams(dp(200)));
        }
        dockRow.addView(glyphTile(TAG_ALL, "All apps", Glyphs.GRID, 0xFF2E625C), tileParams(dp(112)));
        dockRow.addView(glyphTile(TAG_SETTINGS, "Settings", Glyphs.GEAR, 0xFF1E3C3A), tileParams(dp(112)));

        if (appsPanel.getVisibility() == View.VISIBLE) return;
        View target = want == null ? null : dockRow.findViewWithTag(want);
        if (target == null) target = dockRow.getChildAt(0);
        target.requestFocus();
    }

    private LinearLayout.LayoutParams tileParams(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(112));
        lp.setMargins(dp(8), 0, dp(8), 0);
        return lp;
    }

    private View appTile(final AppStore.App app) {
        final FrameLayout tile = baseTile(app.pkg, app.label);
        if (app.banner != null) {
            ((GradientDrawable) tile.getBackground()).setColor(0xFF000000);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            tile.addView(iv, match());
        } else {
            ((GradientDrawable) tile.getBackground()).setColor(tintFor(app.icon));
            tile.addView(iconWithLabel(app, 46, 15), match());
        }
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(app);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                dockMenu(app);
                return true;
            }
        });
        tile.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                    dockMenu(app);
                    return true;
                }
                return false;
            }
        });
        return tile;
    }

    private View glyphTile(final String tag, String label, int glyph, int color) {
        FrameLayout tile = baseTile(tag, label);
        ((GradientDrawable) tile.getBackground()).setColor(color);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyphs(glyph));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER);
        tile.addView(iv, lp);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TAG_ALL.equals(tag)) showApps(); else settingsMenu();
            }
        });
        return tile;
    }

    private FrameLayout baseTile(String tag, final String label) {
        final FrameLayout tile = new FrameLayout(this);
        tile.setTag(tag);
        tile.setFocusable(true);
        tile.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        tile.setBackground(bg);
        tile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tile.setClipToOutline(true);
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(18));
        ring.setStroke(dp(3), Color.TRANSPARENT);
        tile.setForeground(ring);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(16) : 0).setDuration(170).start();
                ring.setStroke(dp(3), hasFocus ? 0xFFE8FFFB : Color.TRANSPARENT);
                if (hasFocus) labelView.setText(label);
            }
        });
        return tile;
    }

    private View iconWithLabel(AppStore.App app, int iconDp, int textSp) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(app.icon);
        box.addView(iv, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        TextView tv = new TextView(this);
        tv.setText(app.label);
        tv.setTypeface(uiFace);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        box.addView(tv, lp);
        return box;
    }

    /** A dark background colour picked from the app's icon. */
    private int tintFor(Drawable icon) {
        int fallback = 0xFF173236;
        if (icon == null) return fallback;
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            icon.setBounds(0, 0, 16, 16);
            icon.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    r += Color.red(px);
                    g += Color.green(px);
                    bl += Color.blue(px);
                    n++;
                }
            }
            b.recycle();
            if (n == 0) return fallback;
            int cr = (int) (r / n), cg = (int) (g / n), cb = (int) (bl / n);
            // Mix 45% icon colour with the dark teal theme so text stays readable.
            return Color.rgb((int) (cr * 0.45 + 0x10 * 0.55), (int) (cg * 0.45 + 0x26 * 0.55),
                    (int) (cb * 0.45 + 0x2A * 0.55));
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---------- Actions ----------

    private void launch(AppStore.App app) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(app.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(app.pkg);
        if (i == null) {
            i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(app.component);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open " + app.label);
        }
    }

    private void dockMenu(final AppStore.App app) {
        final int idx = dockPkgs.indexOf(app.pkg);
        if (idx < 0) return;
        String[] items = {"Move left", "Move right", "Remove from home", "App info"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(app.label)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0 && idx > 0) {
                            Collections.swap(dockPkgs, idx, idx - 1);
                            focusAfterRebuild = app.pkg;
                        } else if (which == 1 && idx < dockPkgs.size() - 1) {
                            Collections.swap(dockPkgs, idx, idx + 1);
                            focusAfterRebuild = app.pkg;
                        } else if (which == 2) {
                            dockPkgs.remove(idx);
                            if (!dockPkgs.isEmpty()) {
                                focusAfterRebuild = dockPkgs.get(Math.min(idx, dockPkgs.size() - 1));
                            }
                        } else if (which == 3) {
                            openAppInfo(app.pkg);
                            return;
                        } else {
                            return;
                        }
                        store.saveDock(dockPkgs);
                        rebuildDock();
                    }
                })
                .show();
    }

    private void togglePinned(AppStore.App app) {
        if (dockPkgs.remove(app.pkg)) {
            toast(app.label + " removed from home");
        } else {
            dockPkgs.add(app.pkg);
            toast(app.label + " pinned to home");
        }
        store.saveDock(dockPkgs);
        rebuildDock();
        appsAdapter.notifyDataSetChanged();
    }

    private static final String[] TRACKERS = {
            "com.amazon.device.crashmanager",
            "com.amazon.client.metrics",
            "com.amazon.dp.logger",
    };

    private void settingsMenu() {
        final boolean linked = homeButtonLinked();
        final boolean cleaned = getSharedPreferences("state", MODE_PRIVATE).getBoolean("trackers_removed", false);
        String[] items = {
                "Fire TV settings",
                linked ? "Home button: linked \u2713" : "Link the Home button",
                cleaned ? "Restore Amazon tracking apps" : "Remove Amazon tracking apps",
                "Reset home row",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Nightfall")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                        } else if (which == 1) {
                            if (linked) toast("The Home button already opens Nightfall");
                            else linkHomeButton();
                        } else if (which == 2) {
                            confirmTrackers(!cleaned);
                        } else {
                            getSharedPreferences("nightfall", MODE_PRIVATE).edit().clear().apply();
                            focusAfterRebuild = TAG_SETTINGS;
                            refresh();
                        }
                    }
                })
                .show();
    }

    /** Something to run over the TV's own debugging connection. Returns a message for the person. */
    private interface AdbJob {
        String run(AdbShell adb) throws Exception;
    }

    private void runWithAdb(final AdbJob job) {
        final AlertDialog wait = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Working\u2026")
                .setMessage("If the TV asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose OK.")
                .setCancelable(false)
                .show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                boolean adbOff = false;
                AdbShell adb = null;
                try {
                    adb = AdbShell.connect(MainActivity.this);
                    msg = job.run(adb);
                } catch (AdbShell.NotEnabledException e) {
                    adbOff = true;
                    msg = null;
                } catch (Exception e) {
                    msg = "That didn't work: " + e.getMessage();
                } finally {
                    if (adb != null) adb.close();
                }
                final String result = msg;
                final boolean showAdbHelp = adbOff;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wait.dismiss();
                        if (showAdbHelp) adbHelp();
                        else infoDialog(result);
                    }
                });
            }
        }).start();
    }

    private void linkHomeButton() {
        runWithAdb(new AdbJob() {
            @Override
            public String run(AdbShell adb) throws Exception {
                String me = getPackageName() + "/" + HomeButtonService.class.getName();
                String cur = adb.shell("settings get secure enabled_accessibility_services");
                String value;
                if (cur.length() == 0 || cur.equals("null")) value = me;
                else if (cur.contains(me)) value = cur;
                else value = cur + ":" + me;
                adb.shell("settings put secure enabled_accessibility_services '" + value + "'");
                adb.shell("settings put secure accessibility_enabled 1");
                Thread.sleep(500);
                return homeButtonLinked()
                        ? "Done. Pressing Home now brings you back to Nightfall."
                        : "The setting was sent but didn't stick. Try restarting the TV, then run this again.";
            }
        });
    }

    private void confirmTrackers(final boolean remove) {
        String msg = remove
                ? "This removes Amazon's crash reporting and usage logging apps from your profile. "
                + "Your streaming apps, Alexa and the Appstore aren't touched, and you can undo it from this menu."
                : "This puts Amazon's crash reporting and usage logging apps back.";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(remove ? "Remove Amazon tracking?" : "Restore Amazon tracking?")
                .setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(remove ? "Remove" : "Restore", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        runWithAdb(new AdbJob() {
                            @Override
                            public String run(AdbShell adb) throws Exception {
                                StringBuilder out = new StringBuilder();
                                for (String pkg : TRACKERS) {
                                    String r = remove
                                            ? adb.shell("pm uninstall --user 0 " + pkg)
                                            : adb.shell("cmd package install-existing " + pkg);
                                    String state = r.toLowerCase().contains("success") || r.contains("installed for user")
                                            ? (remove ? "removed" : "restored")
                                            : (remove ? "not found (already gone)" : "not available");
                                    out.append(pkg).append(": ").append(state).append("\n");
                                }
                                getSharedPreferences("state", MODE_PRIVATE).edit()
                                        .putBoolean("trackers_removed", remove).apply();
                                return out.toString().trim();
                            }
                        });
                    }
                })
                .show();
    }

    private void adbHelp() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Turn on ADB debugging first")
                .setMessage("1. Settings \u2192 My Fire TV \u2192 Developer options \u2192 ADB debugging \u2192 On.\n\n"
                        + "No Developer options? Go to Settings \u2192 My Fire TV \u2192 About and click your "
                        + "device name 7 times, then go back.\n\n"
                        + "2. Come back here and try again.")
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (!safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void infoDialog(String msg) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Nightfall")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean homeButtonLinked() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    private void openAppInfo(String pkg) {
        safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private boolean safeStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- All apps ----------

    private FrameLayout buildAppsPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(0xF2040A0D);
        panel.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(34), dp(48), 0);

        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTypeface(uiFace);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        col.addView(title, wrap());

        TextView hint = new TextView(this);
        hint.setText("Hold OK or press ☰ to pin or unpin an app on the home screen");
        hint.setTypeface(uiFace);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        col.addView(hint, wrap());

        appsGrid = new GridView(this);
        appsGrid.setNumColumns(5);
        appsGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        appsGrid.setVerticalSpacing(dp(10));
        appsGrid.setHorizontalSpacing(dp(12));
        appsGrid.setPadding(0, dp(18), 0, dp(30));
        appsGrid.setClipToPadding(false);
        appsGrid.setVerticalScrollBarEnabled(false);
        GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(16));
        sel.setColor(0x3348B8A8);
        sel.setStroke(dp(2), 0xFFBFF3EC);
        appsGrid.setSelector(sel);
        appsGrid.setDrawSelectorOnTop(false);
        appsAdapter = new AppsAdapter();
        appsGrid.setAdapter(appsAdapter);
        appsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(apps.get(position));
            }
        });
        appsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                togglePinned(apps.get(position));
                return true;
            }
        });
        appsGrid.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_MENU) return false;
                int pos = appsGrid.getSelectedItemPosition();
                if (event.getAction() == KeyEvent.ACTION_UP && pos >= 0 && pos < apps.size()) {
                    togglePinned(apps.get(pos));
                }
                return true;
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        col.addView(appsGrid, glp);
        panel.addView(col, match());
        return panel;
    }

    private void showApps() {
        appsAdapter.notifyDataSetChanged();
        appsPanel.setAlpha(0f);
        appsPanel.setVisibility(View.VISIBLE);
        appsPanel.animate().alpha(1f).setDuration(180).start();
        appsGrid.setSelection(0);
        appsGrid.requestFocus();
    }

    private void hideApps() {
        appsPanel.setVisibility(View.GONE);
        View all = dockRow.findViewWithTag(TAG_ALL);
        if (all != null) all.requestFocus();
    }

    private class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppStore.App app = apps.get(position);
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));

            FrameLayout art = new FrameLayout(MainActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            art.setBackground(bg);
            art.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            art.setClipToOutline(true);
            if (app.banner != null) {
                bg.setColor(0xFF000000);
                ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageDrawable(app.banner);
                art.addView(iv, match());
            } else {
                bg.setColor(tintFor(app.icon));
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageDrawable(app.icon);
                art.addView(iv, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
            }
            cell.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));

            TextView tv = new TextView(MainActivity.this);
            boolean pinned = dockPkgs.contains(app.pkg);
            tv.setText(pinned ? "★ " + app.label : app.label);
            tv.setTypeface(uiFace);
            tv.setTextColor(pinned ? 0xFFBFF3EC : 0xDDFFFFFF);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            cell.addView(tv, lp);
            return cell;
        }
    }

    // ---------- Helpers ----------

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
