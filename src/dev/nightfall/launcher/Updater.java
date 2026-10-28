package dev.nightfall.launcher;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;

/** Checks GitHub for a newer Nightfall and installs it over the TV's own debugging connection. */
final class Updater {
    static final String VERSION_URL =
            "https://raw.githubusercontent.com/vls0001/nightfall-launcher/main/dist/version.json";

    static final class Release {
        int code;
        String name;
        String url;
        String notes;
    }

    private Updater() {
    }

    static int installedCode(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String installedName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Latest release info. Call off the main thread. */
    static Release latest() throws Exception {
        JSONObject j = new JSONObject(Net.get(VERSION_URL + "?t=" + System.currentTimeMillis()));
        Release r = new Release();
        r.code = j.getInt("versionCode");
        r.name = j.optString("versionName", String.valueOf(r.code));
        r.url = j.getString("url");
        r.notes = j.optString("notes", "");
        return r;
    }

    /** Downloads and installs. On success this app restarts, so the return is rarely seen. */
    static String install(Context ctx, AdbShell adb, Release r) throws Exception {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) throw new Exception("No storage available for the download");
        File apk = new File(dir, "update.apk");
        Net.download(r.url, apk);
        apk.setReadable(true, false);
        String tmp = "/data/local/tmp/nightfall-update.apk";
        adb.shell("cp '" + apk.getAbsolutePath() + "' " + tmp);
        String out = adb.shell("pm install -r " + tmp + " ; rm -f " + tmp);
        if (!out.contains("Success")) throw new Exception(out.length() > 0 ? out : "install failed");
        return "Updated to " + r.name + ".";
    }
}
