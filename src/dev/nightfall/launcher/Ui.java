package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** Shared sizes, fonts and styles. */
final class Ui {
    static float density = 1f;
    static Typeface bold = Typeface.DEFAULT_BOLD;
    static Typeface regular = Typeface.DEFAULT;

    static final int TEAL = 0xFF7FE3D6;
    static final int MINT = 0xFFBFF3EC;
    static final int GLOW = 0xFF70E0D2;

    private Ui() {
    }

    static void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
        try {
            bold = Typeface.createFromAsset(ctx.getAssets(), "clock.ttf");
            regular = Typeface.createFromAsset(ctx.getAssets(), "ui.ttf");
        } catch (Exception ignored) {
        }
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        return d;
    }

    static GradientDrawable glass(float radiusDp) {
        GradientDrawable d = rounded(0x332F6A63, radiusDp);
        d.setStroke(Math.max(1, dp(1.5f)), 0x8899E8DE);
        return d;
    }

    /** Background for list rows and buttons: faint at rest, lit teal when focused. */
    static Drawable rowBackground(float radiusDp, boolean danger) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = rounded(danger ? 0x55E0706A : 0x4448B8A8, radiusDp);
        on.setStroke(dp(2), danger ? 0xFFFFC2BC : MINT);
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, rounded(0x14FFFFFF, radiusDp));
        return s;
    }

    static TextView text(Context ctx, CharSequence s, float sp, int color, boolean isBold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(isBold ? bold : regular);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Springy grow-on-focus. */
    static void focusPop(View v, boolean focused, float scale) {
        float s = focused ? scale : 1f;
        v.animate().scaleX(s).scaleY(s).translationZ(focused ? dp(14) : 0)
                .setInterpolator(new OvershootInterpolator(focused ? 2.2f : 1f))
                .setDuration(focused ? 320 : 200).start();
    }
}
