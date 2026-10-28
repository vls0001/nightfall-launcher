package dev.nightfall.launcher;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ShotTest {
    static final String OUT = System.getProperty("shots", "/tmp");

    // name, package, colour, has wide banner
    static final Object[][] FAKE = {
            {"YouTube", "com.amazon.firetv.youtube", 0xFFFF0033, true},
            {"Spotify", "com.spotify.tv.android", 0xFF1DB954, false},
            {"Stremio", "com.stremio.one", 0xFF6C4DF5, false},
            {"Netflix", "com.netflix.ninja", 0xFFE50914, true},
            {"Prime Video", "com.amazon.avod", 0xFF1A98FF, false},
            {"Disney+", "com.disney.disneyplus", 0xFF113CCF, false},
            {"Plex", "com.plexapp.android", 0xFFE5A00D, false},
            {"Kodi", "org.xbmc.kodi", 0xFF17B2E7, false},
            {"Twitch", "tv.twitch.android.app", 0xFF9146FF, false},
            {"Downloader", "com.esaba.downloader", 0xFFF07B13, false},
    };

    @Test
    public void screens() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        for (Object[] f : FAKE) {
            ResolveInfo ri = new ResolveInfo();
            ActivityInfo ai = new ActivityInfo();
            ai.packageName = (String) f[1];
            ai.name = f[1] + ".Main";
            ai.nonLocalizedLabel = (String) f[0];
            ai.applicationInfo = new ApplicationInfo();
            ai.applicationInfo.packageName = (String) f[1];
            ri.activityInfo = ai;
            ri.nonLocalizedLabel = (String) f[0];
            shadowOf(ctx.getPackageManager()).addResolveInfoForIntent(
                    new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER"), ri);
        }
        ctx.getSharedPreferences("weather", 0).edit().putInt("temp", 11).putString("unit", "C")
                .putString("icon", "⛅").putString("summary", "Partly cloudy").putString("city", "Blackpool")
                .putLong("at", System.currentTimeMillis()).apply();
        ctx.getSharedPreferences("settings", 0).edit()
                .putString("recent", "com.netflix.ninja,com.stremio.one,org.xbmc.kodi,com.amazon.avod").apply();

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        fakeArt(a);
        LinearLayout dock = (LinearLayout) get(a, "dockRow");
        dock.getChildAt(2).requestFocusFromTouch();
        settle();
        shot(a, "home.png");

        dock.getChildAt(0).requestFocus();
        settle();
        View recent = ((ViewGroup) get(a, "recentRow")).getChildAt(1);
        recent.requestFocusFromTouch();
        settle();
        shot(a, "home-recent.png");

        call(a, "showApps");
        settle();
        shot(a, "apps.png");
        a.onBackPressed();
        settle();

        call(a, "showSettings");
        settle();
        shot(a, "settings.png");
        call(a, "homeOptions");
        settle();
        shot(a, "home-options.png");
        a.onBackPressed();
        a.onBackPressed();
        settle();
        shot(a, "back-home.png");
    }

    /** Give fake apps real-looking icons and, for some, wide banners. */
    @SuppressWarnings("unchecked")
    void fakeArt(MainActivity a) throws Exception {
        List<AppStore.App> apps = (List<AppStore.App>) get(a, "apps");
        for (AppStore.App app : apps) {
            for (Object[] f : FAKE) {
                if (!f[1].equals(app.pkg)) continue;
                int color = (Integer) f[2];
                Bitmap ic = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(ic);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(color);
                c.drawRoundRect(new RectF(4, 4, 92, 92), 22, 22, p);
                p.setColor(Color.WHITE);
                p.setTextSize(52);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(((String) f[0]).substring(0, 1), 48, 66, p);
                app.icon = new BitmapDrawable(a.getResources(), ic);
                if ((Boolean) f[3]) {
                    Bitmap b = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
                    Canvas bc = new Canvas(b);
                    bc.drawColor(f[0].equals("YouTube") ? Color.WHITE : Color.BLACK);
                    p.setColor(color);
                    p.setTextSize(110);
                    bc.drawText(((String) f[0]).toUpperCase(), 320, 220, p);
                    app.banner = new BitmapDrawable(a.getResources(), b);
                }
            }
        }
        Method m = MainActivity.class.getDeclaredMethod("rebuildDock");
        m.setAccessible(true);
        m.invoke(a);
        m = MainActivity.class.getDeclaredMethod("rebuildRecents");
        m.setAccessible(true);
        m.invoke(a);
        settle();
    }

    static Object get(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900));
    }

    static void shot(MainActivity a, String name) throws Exception {
        View root = a.getWindow().getDecorView();
        Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(OUT + "/" + name)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        System.out.println("SHOT " + name + " " + root.getWidth() + "x" + root.getHeight());
    }
}
