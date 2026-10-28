package dev.nightfall.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire TV always sends the Home button to Amazon's home screen. When that screen
 * appears, this immediately brings Nightfall to the front instead.
 */
public class HomeButtonService extends AccessibilityService {
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private long lastLaunch;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !AMAZON_HOME.contentEquals(pkg)) return;

        // Let Amazon's search and profile screens through; only replace the home screen.
        CharSequence cls = event.getClassName();
        String c = cls == null ? "" : cls.toString().toLowerCase();
        if (c.contains("search") || c.contains("profile") || c.contains("dialog")) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunch < 800) return;
        lastLaunch = now;

        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    @Override
    public void onInterrupt() {
    }
}
