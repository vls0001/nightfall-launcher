package dev.nightfall.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private static final String[] TRACKERS = {
            "com.amazon.device.crashmanager",
            "com.amazon.client.metrics",
            "com.amazon.dp.logger",
    };

    private AppStore store;
    private List<AppStore.App> apps = new ArrayList<AppStore.App>();
    private List<String> dockPkgs = new ArrayList<String>();

    private FrameLayout root;
    private SceneView scene;
    private LinearLayout home;
    private TextView dateView;
    private ClockView clockView;
    private TextView labelView;
    private GlowView glow;
    private View glowTarget;
    private HorizontalScrollView dockScroll;
    private LinearLayout dockRow;
    private LinearLayout recentRow;
    private String focusAfterRebuild;

    // Overlays (all apps, settings, cards) stack on top of the home screen.
    private final List<ViewGroup> overlays = new ArrayList<ViewGroup>();
    private final List<View> returnFocus = new ArrayList<View>();
    private BaseAdapter appsAdapter;
    private LinearLayout settingsList;

    private Weather.Now weather;
    private boolean weatherBusy;
    private long weatherTriedAt;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            maybeFetchWeather();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    // ---------- Lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        store = new AppStore(this);

        root = new FrameLayout(this);
        scene = new SceneView(this);
        root.addView(scene, match());
        home = buildHome();
        root.addView(home, match());
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
        weather = store.weatherOn() ? Weather.last(this) : null;
        updateClock();
        refresh();
        maybeFetchWeather();
        scene.setRunning(overlays.isEmpty());
        if (overlays.isEmpty()) {
            home.setAlpha(0f);
            home.setTranslationY(Ui.dp(12));
            home.animate().alpha(1f).translationY(0).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    @Override
    protected void onPause() {
        scene.setRunning(false);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: close everything and go to the first tile.
        while (!overlays.isEmpty()) popOverlay();
        dockScroll.smoothScrollTo(0, 0);
        if (dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (!overlays.isEmpty()) popOverlay();
        // On the home screen itself, Back does nothing.
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

    private LinearLayout buildHome() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Ui.dp(44), 0, 0);
        col.setClipChildren(false);

        dateView = Ui.text(this, "", 20, 0xEEFFFFFF, false);
        dateView.setShadowLayer(10, 0, 2, 0xAA000000);
        col.addView(dateView, wrap());

        clockView = new ClockView(this);
        clockView.setTypeface(Ui.bold);
        clockView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 124);
        clockView.setIncludeFontPadding(false);
        clockView.setLetterSpacing(-0.02f);
        clockView.setShadowLayer(Ui.dp(16), 0, 0, 0x8870E0D2);
        clockView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = wrap();
        clp.topMargin = Ui.dp(6);
        col.addView(clockView, clp);

        FrameLayout shelf = new FrameLayout(this);
        shelf.setBackground(Ui.glass(30));
        shelf.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        // Clips the scrolling row to the shelf; the glow and focus grow still fit because
        // each child is only clipped to its own bounds.
        shelf.setClipToPadding(false);

        glow = new GlowView(this);
        glow.setAlpha(0f);
        shelf.addView(glow, new FrameLayout.LayoutParams(1, 1));

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        dockScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                if (glowTarget != null) placeGlow(glowTarget, false);
            }
        });

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(0, Ui.dp(10), 0, Ui.dp(10));
        dockScroll.addView(dockRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shelf.addView(dockScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Fixed height: tile + room for the focus grow + shelf padding
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(112 + 20 + 8));
        slp.topMargin = Ui.dp(24);
        slp.leftMargin = Ui.dp(36);
        slp.rightMargin = Ui.dp(36);
        col.addView(shelf, slp);

        labelView = Ui.text(this, "", 18, 0xF2FFFFFF, false);
        labelView.setShadowLayer(10, 0, 2, 0xCC000000);
        LinearLayout.LayoutParams llp = wrap();
        llp.topMargin = Ui.dp(14);
        col.addView(labelView, llp);

        recentRow = new LinearLayout(this);
        recentRow.setOrientation(LinearLayout.HORIZONTAL);
        recentRow.setGravity(Gravity.CENTER);
        recentRow.setClipChildren(false);
        LinearLayout.LayoutParams rlp = wrap();
        rlp.topMargin = Ui.dp(14);
        col.addView(recentRow, rlp);
        return col;
    }

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        String mode = store.clockMode();
        boolean h24 = mode.equals("24") || (mode.equals("system") && DateFormat.is24HourFormat(this));
        String time = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        if (!time.contentEquals(clockView.getText())) {
            clockView.setText(time);
            clockView.setAlpha(0.35f);
            clockView.animate().alpha(1f).setDuration(600).start();
        }
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        if (weather != null) date += "     " + weather.icon + "  " + weather.temp + "°";
        dateView.setText(date);
    }

    private void maybeFetchWeather() {
        if (!store.weatherOn() || weatherBusy) return;
        Weather.Now fresh = Weather.cached(this);
        if (fresh != null) {
            weather = fresh;
            updateClock();
            return;
        }
        if (weatherTriedAt != 0 && SystemClock.elapsedRealtime() - weatherTriedAt < 10 * 60 * 1000L) return;
        weatherBusy = true;
        weatherTriedAt = SystemClock.elapsedRealtime();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Weather.Now n = null;
                try {
                    n = Weather.fetch(MainActivity.this);
                } catch (Exception ignored) {
                }
                final Weather.Now result = n;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        weatherBusy = false;
                        if (result != null && store.weatherOn()) {
                            weather = result;
                            updateClock();
                            rebuildSettings();
                        }
                    }
                });
            }
        }).start();
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
        rebuildRecents();
        if (appsAdapter != null) appsAdapter.notifyDataSetChanged();
    }

    private void rebuildDock() {
        String want = focusAfterRebuild;
        focusAfterRebuild = null;
        View cur = getCurrentFocus();
        boolean onRecents = cur != null && cur.getParent() == recentRow;
        if (want == null && cur != null && cur.getParent() == dockRow) want = (String) cur.getTag();

        glowTarget = null;
        dockRow.removeAllViews();
        for (String p : dockPkgs) {
            AppStore.App app = AppStore.find(apps, p);
            if (app != null) dockRow.addView(appTile(app), tileParams(Ui.dp(200)));
        }
        dockRow.addView(glyphTile(TAG_ALL, "All apps", Glyphs.GRID,
                new int[]{0xFF3D7E76, 0xFF163634}), tileParams(Ui.dp(112)));
        dockRow.addView(glyphTile(TAG_SETTINGS, "Settings", Glyphs.GEAR,
                new int[]{0xFF2C504C, 0xFF0E2120}), tileParams(Ui.dp(112)));

        if (!overlays.isEmpty() || (onRecents && want == null)) return;
        View target = want == null ? null : dockRow.findViewWithTag(want);
        if (target == null) target = dockRow.getChildAt(0);
        target.requestFocus();
    }

    private LinearLayout.LayoutParams tileParams(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, Ui.dp(112));
        lp.setMargins(Ui.dp(8), 0, Ui.dp(8), 0);
        return lp;
    }

    private View appTile(final AppStore.App app) {
        FrameLayout tile = baseTile(app.pkg, app.label);
        tile.addView(Tiles.art(this, app, false), 0, match());
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
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_UP) dockMenu(app);
                    return true;
                }
                return false;
            }
        });
        return tile;
    }

    private View glyphTile(final String tag, String label, int glyph, int[] colors) {
        FrameLayout tile = baseTile(tag, label);
        View bg = new View(this);
        bg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors));
        tile.addView(bg, 0, match());
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyphs(glyph));
        tile.addView(iv, 1, new FrameLayout.LayoutParams(Ui.dp(44), Ui.dp(44), Gravity.CENTER));
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TAG_ALL.equals(tag)) showApps();
                else showSettings();
            }
        });
        return tile;
    }

    private FrameLayout baseTile(String tag, final String label) {
        final FrameLayout tile = new RoundedFrame(this, 18);
        tile.setTag(tag);
        tile.setFocusable(true);
        tile.setClickable(true);
        final GradientDrawable ring = Ui.rounded(Color.TRANSPARENT, 18);
        ring.setStroke(Ui.dp(2.5f), Color.TRANSPARENT);
        tile.setForeground(ring);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Ui.focusPop(v, hasFocus, 1.1f);
                ring.setStroke(Ui.dp(2.5f), hasFocus ? 0xFFE8FFFB : Color.TRANSPARENT);
                if (hasFocus) {
                    labelView.setText(label);
                    placeGlow(v, true);
                }
            }
        });
        return tile;
    }

    /** Moves the soft glow behind a dock tile. */
    private void placeGlow(final View tile, final boolean animate) {
        glowTarget = tile;
        if (tile.getWidth() == 0) {
            tile.post(new Runnable() {
                @Override
                public void run() {
                    if (glowTarget == tile) placeGlow(tile, false);
                }
            });
            return;
        }
        int w = Math.round(tile.getWidth() * 1.1f) + glow.blur * 2;
        int h = Math.round(tile.getHeight() * 1.1f) + glow.blur * 2;
        ViewGroup.LayoutParams lp = glow.getLayoutParams();
        if (lp.width != w || lp.height != h) {
            lp.width = w;
            lp.height = h;
            glow.setLayoutParams(lp);
        }
        float cx = dockScroll.getLeft() + dockRow.getLeft() + tile.getLeft() + tile.getWidth() / 2f
                - dockScroll.getScrollX();
        float cy = dockScroll.getTop() + dockRow.getTop() + tile.getTop() + tile.getHeight() / 2f;
        float tx = cx - w / 2f, ty = cy - h / 2f;
        glow.animate().cancel();
        if (animate && glow.getAlpha() > 0.5f) {
            glow.animate().translationX(tx).translationY(ty).alpha(1f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        } else {
            glow.setTranslationX(tx);
            glow.setTranslationY(ty);
            glow.animate().alpha(1f).setDuration(250).start();
        }
    }

    private void rebuildRecents() {
        recentRow.removeAllViews();
        if (!store.recentsOn()) return;
        int shown = 0;
        for (String pkg : store.recents()) {
            final AppStore.App app = AppStore.find(apps, pkg);
            if (app == null) continue;
            final FrameLayout chip = new FrameLayout(this);
            chip.setFocusable(true);
            chip.setClickable(true);
            final GradientDrawable bg = Ui.rounded(0x1AFFFFFF, 16);
            bg.setStroke(Math.max(1, Ui.dp(1)), 0x3399E8DE);
            chip.setBackground(bg);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            chip.addView(iv, new FrameLayout.LayoutParams(Ui.dp(30), Ui.dp(30), Gravity.CENTER));
            chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    Ui.focusPop(v, hasFocus, 1.18f);
                    bg.setColor(hasFocus ? 0x5548B8A8 : 0x1AFFFFFF);
                    bg.setStroke(Math.max(1, Ui.dp(hasFocus ? 2 : 1)), hasFocus ? Ui.MINT : 0x3399E8DE);
                    if (hasFocus) {
                        labelView.setText("Recent · " + app.label);
                        glowTarget = null;
                        glow.animate().cancel();
                        glow.animate().alpha(0f).setDuration(200).start();
                    }
                }
            });
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(app);
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    togglePinned(app);
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
            lp.setMargins(Ui.dp(7), 0, Ui.dp(7), 0);
            recentRow.addView(chip, lp);
            if (++shown >= 6) break;
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
            store.noteLaunch(app.pkg);
        } catch (Exception e) {
            toast("Couldn't open " + app.label);
        }
    }

    private void dockMenu(final AppStore.App app) {
        final int idx = dockPkgs.indexOf(app.pkg);
        if (idx < 0) return;
        showCard(app.label, null, new String[]{"Move left", "Move right", "Remove from home", "App info"}, 2,
                new Choice() {
                    @Override
                    public void pick(int which) {
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
                            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + app.pkg)));
                            return;
                        } else {
                            return;
                        }
                        store.saveDock(dockPkgs);
                        rebuildDock();
                    }
                });
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
        if (appsAdapter != null) appsAdapter.notifyDataSetChanged();
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------- Overlays ----------

    private void pushOverlay(ViewGroup v, View focus) {
        returnFocus.add(getCurrentFocus());
        if (v.getTag() != null) home.animate().alpha(0f).setDuration(200).start();
        ViewGroup below = overlays.isEmpty() ? home : overlays.get(overlays.size() - 1);
        below.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        overlays.add(v);
        root.addView(v, match());
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(200).start();
        if (focus != null) focus.requestFocus();
        else v.requestFocus();
        scene.setRunning(false);
    }

    private void popOverlay() {
        if (overlays.isEmpty()) return;
        ViewGroup v = overlays.remove(overlays.size() - 1);
        View back = returnFocus.remove(returnFocus.size() - 1);
        root.removeView(v);
        forget(v);
        ViewGroup below = overlays.isEmpty() ? home : overlays.get(overlays.size() - 1);
        below.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (back != null && back.isAttachedToWindow()) back.requestFocus();
        else if (overlays.isEmpty() && dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
        if (overlays.isEmpty()) scene.setRunning(true);
        fadeHomeBack();
    }

    /** Shows the home content again once no full-screen panel is open. */
    private void fadeHomeBack() {
        for (ViewGroup o : overlays) if (o.getTag() != null) return;
        home.animate().alpha(1f).setDuration(220).start();
    }

    /** Removes an overlay that may no longer be on top (e.g. a "Working…" card). */
    private void removeOverlay(ViewGroup v) {
        int i = overlays.indexOf(v);
        if (i < 0) return;
        if (i == overlays.size() - 1) {
            popOverlay();
            return;
        }
        // Hand its saved focus to the overlay above it, then drop it.
        returnFocus.set(i + 1, returnFocus.get(i));
        overlays.remove(i);
        returnFocus.remove(i);
        root.removeView(v);
        forget(v);
        fadeHomeBack();
    }

    private void forget(ViewGroup v) {
        if ("apps".equals(v.getTag())) appsAdapter = null;
        if ("settings".equals(v.getTag())) settingsList = null;
    }

    interface Choice {
        void pick(int which);
    }

    /** A centred glass card with a title, optional message and a list of choices. */
    private ViewGroup showCard(String title, String message, String[] options, int dangerIndex, final Choice choice) {
        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setClickable(true);
        scrim.setFocusable(options == null); // lets a message-only card hold focus

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.rounded(0xF20B171B, 28);
        bg.setStroke(Math.max(1, Ui.dp(1.5f)), 0x6699E8DE);
        card.setBackground(bg);
        card.setPadding(Ui.dp(28), Ui.dp(26), Ui.dp(28), Ui.dp(22));
        card.setElevation(Ui.dp(24));

        card.addView(Ui.text(this, title, 22, Color.WHITE, true), wrap());
        if (message != null) {
            TextView m = Ui.text(this, message, 15, 0xCCFFFFFF, false);
            m.setLineSpacing(Ui.dp(3), 1f);
            LinearLayout.LayoutParams mlp = wrap();
            mlp.topMargin = Ui.dp(12);
            card.addView(m, mlp);
        }
        View first = null;
        if (options != null) {
            for (int i = 0; i < options.length; i++) {
                final int which = i;
                View row = button(options[i], i == dangerIndex, new Runnable() {
                    @Override
                    public void run() {
                        removeOverlay(scrim);
                        if (choice != null) choice.pick(which);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(50));
                lp.topMargin = Ui.dp(i == 0 ? 18 : 8);
                card.addView(row, lp);
                if (first == null) first = row;
            }
        }
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(480),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        scrim.addView(card, clp);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        pushOverlay(scrim, first);
        return scrim;
    }

    private void showInfo(String title, String message) {
        showCard(title, message, new String[]{"OK"}, -1, null);
    }

    private View button(String label, boolean danger, final Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(Ui.rowBackground(14, danger));
        TextView t = Ui.oneLine(Ui.text(this, label, 16, danger ? 0xFFFFD2CD : Color.WHITE, false));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        lp.leftMargin = Ui.dp(18);
        lp.rightMargin = Ui.dp(18);
        b.addView(t, lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return b;
    }

    // ---------- All apps ----------

    private void showApps() {
        FrameLayout panel = new FrameLayout(this);
        panel.setTag("apps");
        panel.setBackgroundColor(0xF2040A0D);
        panel.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(48), Ui.dp(34), Ui.dp(48), 0);

        col.addView(Ui.text(this, "All apps", 28, Color.WHITE, true), wrap());
        TextView hint = Ui.text(this, apps.size() + " apps  ·  hold OK or press ☰ to pin or unpin",
                14, 0x99FFFFFF, false);
        LinearLayout.LayoutParams hlp = wrap();
        hlp.topMargin = Ui.dp(6);
        col.addView(hint, hlp);

        final GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(6));
        grid.setHorizontalSpacing(Ui.dp(10));
        grid.setPadding(0, Ui.dp(16), 0, Ui.dp(30));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        GradientDrawable sel = Ui.rounded(0x3348B8A8, 16);
        sel.setStroke(Ui.dp(2), Ui.MINT);
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(false);
        appsAdapter = new BaseAdapter() {
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
                return appCell(apps.get(position));
            }
        };
        grid.setAdapter(appsAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(apps.get(position));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                togglePinned(apps.get(position));
                return true;
            }
        });
        grid.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_MENU) return false;
                int pos = grid.getSelectedItemPosition();
                if (event.getAction() == KeyEvent.ACTION_UP && pos >= 0 && pos < apps.size()) {
                    togglePinned(apps.get(pos));
                }
                return true;
            }
        });
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(col, match());
        pushOverlay(panel, grid);
        grid.setSelection(0);
    }

    private View appCell(AppStore.App app) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));

        FrameLayout art = new RoundedFrame(this, 12);
        art.addView(Tiles.art(this, app, true), match());
        cell.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(80)));

        boolean pinned = dockPkgs.contains(app.pkg);
        TextView tv = Ui.oneLine(Ui.text(this, pinned ? "★ " + app.label : app.label, 13,
                pinned ? Ui.MINT : 0xDDFFFFFF, false));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(8);
        cell.addView(tv, lp);
        return cell;
    }

    // ---------- Settings ----------

    private void showSettings() {
        FrameLayout panel = new FrameLayout(this);
        panel.setTag("settings");
        panel.setBackgroundColor(0xF0040A0D);
        panel.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(34), 0, 0);

        col.addView(Ui.text(this, "Settings", 28, Color.WHITE, true), wrap());
        TextView ver = Ui.text(this, "Nightfall " + Updater.installedName(this), 14, 0x99FFFFFF, false);
        LinearLayout.LayoutParams vlp = wrap();
        vlp.topMargin = Ui.dp(6);
        col.addView(ver, vlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Ui.dp(14), 0, Ui.dp(30));
        settingsList = new LinearLayout(this);
        settingsList.setOrientation(LinearLayout.VERTICAL);
        settingsList.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        settingsList.setClipChildren(false);
        scroll.addView(settingsList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(640),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        panel.addView(col, clp);
        rebuildSettings();
        pushOverlay(panel, settingsList.getChildAt(0));
    }

    private void rebuildSettings() {
        if (settingsList == null) return;
        int focused = -1;
        for (int i = 0; i < settingsList.getChildCount(); i++) {
            if (settingsList.getChildAt(i).hasFocus()) focused = i;
        }
        settingsList.removeAllViews();

        String clock = store.clockMode();
        String clockText = clock.equals("24") ? "24-hour" : clock.equals("12") ? "12-hour" : "Follows the TV's setting";
        String weatherText = !store.weatherOn() ? "Off"
                : weather == null ? "On · waiting for the forecast"
                : "On · " + weather.icon + " " + weather.temp + "°" + weather.unit + " " + weather.summary
                + (weather.city.length() > 0 ? " in " + weather.city : "");
        boolean cleaned = getSharedPreferences("state", MODE_PRIVATE).getBoolean("trackers_removed", false);

        addSetting("Fire TV settings", "Network, display, remotes and apps", new Runnable() {
            @Override
            public void run() {
                safeStart(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        addSetting("Home screen", HomeControl.describe(this), new Runnable() {
            @Override
            public void run() {
                homeOptions();
            }
        });
        addSetting("Amazon tracking", cleaned ? "Removed: no crash reports or usage logs"
                : "On: Amazon collects crash reports and usage logs", new Runnable() {
            @Override
            public void run() {
                confirmTrackers();
            }
        });
        addSetting("Clock", clockText, new Runnable() {
            @Override
            public void run() {
                String m = store.clockMode();
                store.setClockMode(m.equals("system") ? "24" : m.equals("24") ? "12" : "system");
                updateClock();
                rebuildSettings();
            }
        });
        addSetting("Weather", weatherText, new Runnable() {
            @Override
            public void run() {
                boolean on = !store.weatherOn();
                store.setWeatherOn(on);
                weather = on ? Weather.last(MainActivity.this) : null;
                weatherTriedAt = 0;
                updateClock();
                maybeFetchWeather();
                rebuildSettings();
            }
        });
        addSetting("Recently opened row", store.recentsOn() ? "On" : "Off", new Runnable() {
            @Override
            public void run() {
                store.setRecentsOn(!store.recentsOn());
                rebuildRecents();
                rebuildSettings();
            }
        });
        addSetting("Check for updates", "Installed: version " + Updater.installedName(this), new Runnable() {
            @Override
            public void run() {
                checkForUpdates();
            }
        });
        addSetting("Reset home row", "Go back to the default pinned apps", new Runnable() {
            @Override
            public void run() {
                showCard("Reset home row?", "Your pinned apps and their order go back to the defaults.",
                        new String[]{"Reset", "Cancel"}, 0, new Choice() {
                            @Override
                            public void pick(int which) {
                                if (which != 0) return;
                                getSharedPreferences("nightfall", MODE_PRIVATE).edit().clear().apply();
                                refresh();
                            }
                        });
            }
        });

        if (focused >= 0) settingsList.getChildAt(Math.min(focused, settingsList.getChildCount() - 1)).requestFocus();
    }

    private void addSetting(String title, String subtitle, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(Ui.rowBackground(18, false));
        row.setPadding(Ui.dp(22), Ui.dp(14), Ui.dp(18), Ui.dp(14));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Ui.text(this, title, 18, Color.WHITE, true), wrap());
        TextView sub = Ui.oneLine(Ui.text(this, subtitle, 14, 0xA6FFFFFF, false));
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = Ui.dp(5);
        text.addView(sub, slp);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, "›", 26, 0x80FFFFFF, false), wrap());

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(10);
        settingsList.addView(row, lp);
    }

    private void homeOptions() {
        HomeControl.State s = HomeControl.state(this);
        if (s == HomeControl.State.REPLACED) {
            showCard("Home screen", "Nightfall is the TV's home screen. Amazon's home screen is switched off.",
                    new String[]{"Restore Amazon home", "Close"}, -1, new Choice() {
                        @Override
                        public void pick(int which) {
                            if (which != 0) return;
                            runWithAdb(new AdbJob() {
                                @Override
                                public String run(AdbShell adb) throws Exception {
                                    return HomeControl.restoreAmazon(adb);
                                }
                            });
                        }
                    });
            return;
        }
        final boolean linked = s == HomeControl.State.LINKED;
        String msg = linked
                ? "The Home button already opens Nightfall, and Amazon's home is kept as a hidden backup."
                : "Choose how Nightfall takes over the Home button.";
        String[] opts = linked
                ? new String[]{"Replace Amazon home completely", "Close"}
                : new String[]{"Link the Home button (keeps Amazon home as backup)", "Replace Amazon home completely", "Close"};
        final int replaceIdx = linked ? 0 : 1;
        showCard("Home screen", msg, opts, replaceIdx, new Choice() {
            @Override
            public void pick(int which) {
                if (which == replaceIdx) {
                    confirmReplace();
                } else if (!linked && which == 0) {
                    runWithAdb(new AdbJob() {
                        @Override
                        public String run(AdbShell adb) throws Exception {
                            return HomeControl.linkHelper(MainActivity.this, adb);
                        }
                    });
                }
            }
        });
    }

    private void confirmReplace() {
        showCard("Replace Amazon home?",
                "This switches off Amazon's home screen so Nightfall is the only one. "
                        + "Fire TV settings stay reachable from Nightfall's Settings.\n\n"
                        + "You can undo it from this screen. If the TV ever gets stuck, hold Back + Right on the remote "
                        + "for 10 seconds to factory reset.",
                new String[]{"Replace it", "Cancel"}, 0, new Choice() {
                    @Override
                    public void pick(int which) {
                        if (which != 0) return;
                        runWithAdb(new AdbJob() {
                            @Override
                            public String run(AdbShell adb) throws Exception {
                                return HomeControl.replaceAmazon(MainActivity.this, adb);
                            }
                        });
                    }
                });
    }

    private void confirmTrackers() {
        final boolean remove = !getSharedPreferences("state", MODE_PRIVATE).getBoolean("trackers_removed", false);
        String msg = remove
                ? "This removes Amazon's crash reporting and usage logging apps from your profile. "
                + "Your streaming apps, Alexa and the Appstore aren't touched, and you can undo it here."
                : "This puts Amazon's crash reporting and usage logging apps back.";
        showCard(remove ? "Remove Amazon tracking?" : "Restore Amazon tracking?", msg,
                new String[]{remove ? "Remove" : "Restore", "Cancel"}, -1, new Choice() {
                    @Override
                    public void pick(int which) {
                        if (which != 0) return;
                        runWithAdb(new AdbJob() {
                            @Override
                            public String run(AdbShell adb) throws Exception {
                                StringBuilder out = new StringBuilder();
                                for (String pkg : TRACKERS) {
                                    String r = remove
                                            ? adb.shell("pm uninstall --user 0 " + pkg)
                                            : adb.shell("cmd package install-existing " + pkg);
                                    boolean ok = r.contains("Success") || r.contains("installed for user");
                                    out.append(pkg.substring(pkg.lastIndexOf('.') + 1)).append(": ")
                                            .append(ok ? (remove ? "removed" : "restored")
                                                    : (remove ? "already gone" : "not available"))
                                            .append('\n');
                                }
                                getSharedPreferences("state", MODE_PRIVATE).edit()
                                        .putBoolean("trackers_removed", remove).apply();
                                return out.toString().trim();
                            }
                        });
                    }
                });
    }

    private void checkForUpdates() {
        final ViewGroup wait = showCard("Checking for updates…", null, null, -1, null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Updater.Release r = null;
                String error = null;
                try {
                    r = Updater.latest();
                } catch (Exception e) {
                    error = e.getMessage();
                }
                final Updater.Release rel = r;
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        removeOverlay(wait);
                        if (rel == null) {
                            showInfo("Couldn't check", "Check the TV's internet connection. (" + err + ")");
                        } else if (rel.code <= Updater.installedCode(MainActivity.this)) {
                            showInfo("You're up to date", "Nightfall " + Updater.installedName(MainActivity.this)
                                    + " is the latest version.");
                        } else {
                            showCard("Version " + rel.name + " is available",
                                    (rel.notes.length() > 0 ? rel.notes + "\n\n" : "")
                                            + "Nightfall will close and reopen while it updates.",
                                    new String[]{"Install now", "Later"}, -1, new Choice() {
                                        @Override
                                        public void pick(int which) {
                                            if (which != 0) return;
                                            runWithAdb(new AdbJob() {
                                                @Override
                                                public String run(AdbShell adb) throws Exception {
                                                    return Updater.install(MainActivity.this, adb, rel);
                                                }
                                            });
                                        }
                                    });
                        }
                    }
                });
            }
        }).start();
    }

    // ---------- Running commands on the TV ----------

    private interface AdbJob {
        String run(AdbShell adb) throws Exception;
    }

    private void runWithAdb(final AdbJob job) {
        final ViewGroup wait = showCard("Working…",
                "If the TV asks \"Allow USB debugging?\", tick \"Always allow from this computer\" and choose OK.",
                null, -1, null);
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
                        removeOverlay(wait);
                        rebuildSettings();
                        refresh();
                        if (showAdbHelp) adbHelp();
                        else showInfo("Nightfall", result);
                    }
                });
            }
        }).start();
    }

    private void adbHelp() {
        showCard("Turn on ADB debugging first",
                "1. Settings → My Fire TV → Developer options → ADB debugging → On.\n\n"
                        + "No Developer options? Go to Settings → My Fire TV → About and click your "
                        + "device name 7 times, then go back.\n\n"
                        + "2. Come back here and try again.",
                new String[]{"Open Fire TV settings", "Close"}, -1, new Choice() {
                    @Override
                    public void pick(int which) {
                        if (which != 0) return;
                        if (!safeStart(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
                            safeStart(new Intent(Settings.ACTION_SETTINGS));
                        }
                    }
                });
    }

    // ---------- Helpers ----------

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
