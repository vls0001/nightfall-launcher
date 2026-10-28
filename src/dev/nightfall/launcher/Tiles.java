package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the artwork inside an app tile. */
final class Tiles {
    private Tiles() {
    }

    /** Wide logo if the app has one; otherwise a poster made from its icon colours. */
    static View art(Context ctx, AppStore.App app, boolean small) {
        FrameLayout f = new FrameLayout(ctx);
        if (app.banner != null) {
            f.setBackgroundColor(Color.BLACK);
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            f.addView(iv, match());
        } else {
            Brands.Brand brand = Brands.find(app.pkg, app.label);
            if (brand != null) brandArt(ctx, f, brand, small);
            else posterArt(ctx, f, app, small);
        }
        // Glassy sheen across the top
        View sheen = new View(ctx);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x26FFFFFF, 0x00FFFFFF}));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slp.height = Ui.dp(small ? 26 : 44);
        f.addView(sheen, slp);
        return f;
    }

    /** Known brand: its logo and name on brand colours, or a styled name when there's no logo. */
    private static void brandArt(Context ctx, FrameLayout f, Brands.Brand b, boolean small) {
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, b.bg));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(Ui.dp(small ? 10 : 16), 0, Ui.dp(small ? 10 : 16), 0);

        boolean longName = b.name.length() > 10;
        TextView name;
        if (b.logoRes != 0 && b.wordmark) {
            // The logo spells the name, so show it alone and larger.
            ImageView logo = logoView(ctx, b);
            logo.setMaxWidth(Ui.dp(small ? 110 : 150));
            row.addView(logo, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(small ? 24 : 36)));
            f.addView(row, match());
            return;
        } else if (b.logoRes != 0) {
            ImageView logo = logoView(ctx, b);
            logo.setMaxWidth(Ui.dp(small ? 40 : 56));
            row.addView(logo, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(small ? 28 : 40)));
            name = Ui.text(ctx, b.name, small ? (longName ? 12 : 14) : (longName ? 17 : 21), b.textColor, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(small ? 8 : 12);
            name.setMaxLines(2);
            row.addView(name, lp);
        } else {
            name = Ui.text(ctx, b.name, small ? (longName ? 15 : 18) : (longName ? 22 : 27), b.textColor, true);
            name.setLetterSpacing(-0.01f);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setShadowLayer(10, 0, 1, 0x55000000);
        f.addView(row, match());
    }

    private static ImageView logoView(Context ctx, Brands.Brand b) {
        ImageView logo = new ImageView(ctx);
        logo.setImageResource(b.logoRes);
        logo.setColorFilter(b.logoColor);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return logo;
    }

    /** Unknown app: a poster made from its icon colours. */
    private static void posterArt(Context ctx, FrameLayout f, AppStore.App app, boolean small) {
        int[] pal = palette(app.icon);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, pal);
        f.setBackground(bg);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(Ui.dp(small ? 10 : 16), 0, Ui.dp(small ? 10 : 16), 0);

        // Icon on a soft light disc so dark icons still read
        FrameLayout disc = new FrameLayout(ctx);
        disc.setBackground(Ui.rounded(0x26FFFFFF, small ? 14 : 18));
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(app.icon);
        int inner = Ui.dp(small ? 30 : 42);
        disc.addView(iv, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));
        int outer = Ui.dp(small ? 42 : 58);
        row.addView(disc, new LinearLayout.LayoutParams(outer, outer));

        TextView name = Ui.text(ctx, app.label, small ? 13 : 17, Color.WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setShadowLayer(8, 0, 1, 0x66000000);
        name.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(small ? 8 : 12);
        row.addView(name, lp);
        f.addView(row, match());
    }

    /** Two colours for a gradient, based on the most vivid colour in the icon. */
    static int[] palette(Drawable icon) {
        int[] fallback = {0xFF1D4A4C, 0xFF0B1C20};
        if (icon == null) return fallback;
        try {
            int size = 24;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(b));
            float[] weight = new float[12];
            float[][] sum = new float[12][3];
            float[] hsv = new float[3];
            float grey = 0;
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 160) continue;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] < 0.25f || hsv[2] < 0.2f) {
                        grey++;
                        continue;
                    }
                    int bucket = Math.min(11, (int) (hsv[0] / 30f));
                    float wgt = hsv[1] * hsv[2];
                    weight[bucket] += wgt;
                    sum[bucket][0] += Color.red(px) * wgt;
                    sum[bucket][1] += Color.green(px) * wgt;
                    sum[bucket][2] += Color.blue(px) * wgt;
                }
            }
            b.recycle();
            int best = 0;
            for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
            if (weight[best] < 1.5f) return fallback;
            int c = Color.rgb((int) (sum[best][0] / weight[best]), (int) (sum[best][1] / weight[best]),
                    (int) (sum[best][2] / weight[best]));
            Color.colorToHSV(c, hsv);
            float h = hsv[0], s = Math.min(hsv[1], 0.8f);
            int top = Color.HSVToColor(new float[]{h, s, 0.62f});
            int bottom = Color.HSVToColor(new float[]{(h + 18) % 360, Math.min(1f, s + 0.1f), 0.2f});
            return new int[]{top, bottom};
        } catch (Exception e) {
            return fallback;
        }
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
