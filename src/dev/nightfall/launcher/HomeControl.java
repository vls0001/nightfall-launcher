package dev.nightfall.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Works out who owns the Home button, and swaps Amazon's home screen out or back in. */
final class HomeControl {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";

    enum State { REPLACED, LINKED, NOT_LINKED }

    private HomeControl() {
    }

    static boolean amazonHomeActive(Context ctx) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(AMAZON_HOME, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean helperLinked(Context ctx) {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(ctx.getPackageName());
    }

    static State state(Context ctx) {
        if (!amazonHomeActive(ctx)) return State.REPLACED;
        return helperLinked(ctx) ? State.LINKED : State.NOT_LINKED;
    }

    static String describe(Context ctx) {
        switch (state(ctx)) {
            case REPLACED:
                return "Nightfall is your only home screen";
            case LINKED:
                return "Home button opens Nightfall · Amazon home kept as backup";
            default:
                return "Not linked: the Home button still goes to Amazon";
        }
    }

    static String linkHelper(Context ctx, AdbShell adb) throws Exception {
        String me = ctx.getPackageName() + "/" + HomeButtonService.class.getName();
        String cur = adb.shell("settings get secure enabled_accessibility_services");
        String value;
        if (cur.length() == 0 || cur.equals("null")) value = me;
        else if (cur.contains(me)) value = cur;
        else value = cur + ":" + me;
        adb.shell("settings put secure enabled_accessibility_services '" + value + "'");
        adb.shell("settings put secure accessibility_enabled 1");
        Thread.sleep(500);
        return helperLinked(ctx)
                ? "Done. Pressing Home now brings you back to Nightfall."
                : "The setting was sent but didn't stick. Restart the TV and try again.";
    }

    /** Turns Amazon's home screen off so Nightfall becomes the real home screen. */
    static String replaceAmazon(Context ctx, AdbShell adb) throws Exception {
        // Keep the helper on too, as a safety net if Fire OS ever brings Amazon's home back.
        if (!helperLinked(ctx)) linkHelper(ctx, adb);

        String me = ctx.getPackageName() + "/" + MainActivity.class.getName();
        String out = adb.shell("pm disable-user --user 0 " + AMAZON_HOME);
        String how = "disabled";
        if (!out.contains("disabled")) {
            out = adb.shell("pm uninstall -k --user 0 " + AMAZON_HOME);
            how = "removed";
            if (!out.contains("Success")) {
                return "Fire OS refused to switch off Amazon's home screen (" + out.trim()
                        + "). Nothing was changed, and the Home button helper still keeps you in Nightfall.";
            }
        }
        adb.shell("cmd package set-home-activity " + me);
        return "Amazon's home screen is " + how + ". Nightfall is now the TV's home screen.\n\n"
                + "If anything ever goes wrong, choose Restore Amazon home here. "
                + "Last resort: hold Back + Right on the remote for 10 seconds to factory reset.";
    }

    static String restoreAmazon(AdbShell adb) throws Exception {
        adb.shell("cmd package install-existing " + AMAZON_HOME);
        adb.shell("pm enable --user 0 " + AMAZON_HOME);
        return "Amazon's home screen is back. Nightfall still opens when you press Home while the helper is linked.";
    }
}
