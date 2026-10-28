package dev.nightfall.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds installed apps and remembers which ones are pinned to the home row. */
public class AppStore {

    public static class App {
        public String pkg;
        public String label;
        public ComponentName component;
        public Drawable banner;
        public Drawable icon;
    }

    // Apps pinned first on a fresh install, if present.
    private static final String[] PREFERRED = {
            "com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged",
            "com.spotify.tv.android", "com.stremio.one", "com.netflix.ninja", "com.amazon.avod",
            "com.disney.disneyplus", "com.hbo.hbonow", "com.wbd.stream", "com.hulu.plus",
            "com.plexapp.android", "org.jellyfin.androidtv", "org.xbmc.kodi", "tv.twitch.android.app",
    };

    // Amazon/system screens that shouldn't appear as apps.
    private static final String[] HIDDEN = {
            "com.amazon.tv.launcher", "com.amazon.tv.settings", "com.amazon.tv.settings.v2",
            "com.amazon.venezia", "com.android.tv.settings", "com.amazon.tv.oobe",
    };

    private static final String PREFS = "nightfall";
    private static final String KEY_DOCK = "dock";

    private final Context ctx;
    private final SharedPreferences prefs;

    public AppStore(Context ctx) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<App> loadApps() {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<String, App>();
        addFrom(pm, byPkg, "android.intent.category.LEANBACK_LAUNCHER");
        addFrom(pm, byPkg, Intent.CATEGORY_LAUNCHER);
        List<App> list = new ArrayList<App>(byPkg.values());
        Collections.sort(list, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }

    private void addFrom(PackageManager pm, Map<String, App> out, String category) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || out.containsKey(ai.packageName)) continue;
            if (ai.packageName.equals(ctx.getPackageName()) || isHidden(ai.packageName)) continue;
            App app = new App();
            app.pkg = ai.packageName;
            app.component = new ComponentName(ai.packageName, ai.name);
            CharSequence l = ri.loadLabel(pm);
            app.label = l == null ? ai.packageName : l.toString().trim();
            try {
                app.banner = ai.loadBanner(pm);
                if (app.banner == null) app.banner = pm.getApplicationBanner(ai.packageName);
            } catch (Exception ignored) {
                app.banner = null;
            }
            app.icon = ri.loadIcon(pm);
            out.put(app.pkg, app);
        }
    }

    private static boolean isHidden(String pkg) {
        for (String h : HIDDEN) if (h.equals(pkg)) return true;
        return false;
    }

    /** Pinned package names, in order. Seeds a sensible default the first time. */
    public List<String> dock(List<App> apps) {
        List<String> out = new ArrayList<String>();
        String saved = prefs.getString(KEY_DOCK, null);
        if (saved != null) {
            if (saved.length() > 0) Collections.addAll(out, saved.split(","));
            return out;
        }
        for (String p : PREFERRED) {
            if (find(apps, p) != null && !out.contains(p)) out.add(p);
        }
        for (App a : apps) {
            if (out.size() >= 6) break;
            if (!out.contains(a.pkg)) out.add(a.pkg);
        }
        saveDock(out);
        return out;
    }

    public void saveDock(List<String> pkgs) {
        prefs.edit().putString(KEY_DOCK, TextUtils.join(",", pkgs)).apply();
    }

    public static App find(List<App> apps, String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }
}
