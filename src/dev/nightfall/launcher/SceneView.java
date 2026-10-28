package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/** Draws the night lake wallpaper: starry sky, misty horizon, still water and rocks. */
public class SceneView extends View {
    private Bitmap cache;

    // Rocks as fractions of the screen: x, waterline y, half width, height.
    private static final float[][] ROCKS = {
            {0.42f, 0.455f, 0.025f, 0.030f},
            {0.47f, 0.452f, 0.040f, 0.050f},
            {0.53f, 0.458f, 0.028f, 0.030f},
            {0.585f, 0.455f, 0.020f, 0.020f},
            {0.66f, 0.460f, 0.035f, 0.040f},
            {0.80f, 0.470f, 0.085f, 0.120f},
            {0.94f, 0.500f, 0.110f, 0.180f},
            {0.12f, 0.465f, 0.050f, 0.040f},
            {0.20f, 0.458f, 0.020f, 0.020f},
            {0.06f, 0.970f, 0.140f, 0.150f},
            {0.97f, 0.990f, 0.120f, 0.140f},
    };

    public SceneView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        if (cache != null) cache.recycle();
        cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        paintScene(new Canvas(cache), w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cache != null) canvas.drawBitmap(cache, 0, 0, null);
    }

    private static void paintScene(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = h * 0.45f;
        Random rnd = new Random(1987);

        // Sky
        p.setShader(new LinearGradient(0, 0, 0, horizon,
                new int[]{0xFF010306, 0xFF061017, 0xFF0E2328},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);

        // Stars, denser near the top
        p.setShader(null);
        float unit = Math.max(1f, h / 1080f);
        for (int i = 0; i < 260; i++) {
            float y = (float) Math.pow(rnd.nextFloat(), 1.6) * horizon * 0.92f;
            float x = rnd.nextFloat() * w;
            float r = (0.5f + rnd.nextFloat() * 1.3f) * unit;
            int a = 50 + rnd.nextInt(190);
            p.setColor((a << 24) | (rnd.nextInt(4) == 0 ? 0xCFF5EF : 0xFFFFFF));
            c.drawCircle(x, y, r, p);
        }

        // Horizon glow
        p.setShader(new RadialGradient(w * 0.5f, horizon, w * 0.6f,
                0x3348B8A8, 0x0048B8A8, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Distant shoreline
        Path shore = new Path();
        shore.moveTo(0, horizon);
        for (int x = 0; x <= w; x += Math.max(4, w / 160)) {
            float t = x / (float) w;
            float hill = (float) (Math.sin(t * 9.0 + 1.3) * 0.5 + Math.sin(t * 23.0) * 0.25 + 0.8);
            shore.lineTo(x, horizon - hill * h * 0.018f);
        }
        shore.lineTo(w, horizon);
        shore.close();
        p.setShader(null);
        p.setColor(0xFF04090C);
        c.drawPath(shore, p);

        // Water
        p.setShader(new LinearGradient(0, horizon, 0, h,
                new int[]{0xFF0C2126, 0xFF07161A, 0xFF020608},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);

        // Shimmer lines on the water
        p.setShader(null);
        p.setStrokeWidth(Math.max(1f, unit * 1.2f));
        for (int i = 0; i < 180; i++) {
            float depth = (float) Math.pow(rnd.nextFloat(), 1.8);
            float y = horizon + depth * (h - horizon);
            float len = (0.02f + rnd.nextFloat() * 0.08f) * w * (0.4f + depth);
            float x = rnd.nextFloat() * w;
            int a = 10 + rnd.nextInt(30);
            p.setColor((a << 24) | 0x9FE6DC);
            c.drawLine(x, y, x + len, y, p);
        }

        // Mist along the horizon
        p.setShader(new LinearGradient(0, horizon - h * 0.05f, 0, horizon + h * 0.06f,
                new int[]{0x00BFEDE6, 0x1ABFEDE6, 0x00BFEDE6}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - h * 0.05f, w, horizon + h * 0.06f, p);

        // Rocks and their reflections
        for (float[] r : ROCKS) {
            drawRock(c, p, rnd, r[0] * w, r[1] * h, r[2] * w, r[3] * h);
        }

        // Vignette
        p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, (float) Math.hypot(w, h) * 0.62f,
                new int[]{0x00000000, 0x00000000, 0xB0000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void drawRock(Canvas c, Paint p, Random rnd, float cx, float base, float rw, float rh) {
        // Bumpy outline: random points around the top half of an ellipse, joined with curves.
        int n = 9;
        float[] xs = new float[n + 1], ys = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI + Math.PI * i / n;
            float jitter = (i == 0 || i == n) ? 1f : 0.82f + rnd.nextFloat() * 0.3f;
            xs[i] = cx + (float) Math.cos(a) * rw * jitter;
            ys[i] = Math.min(base, base + (float) Math.sin(a) * rh * jitter);
        }
        Path rock = new Path();
        rock.moveTo(xs[0], base);
        for (int i = 1; i < n; i++) {
            rock.quadTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2f, (ys[i] + ys[i + 1]) / 2f);
        }
        rock.lineTo(xs[n], base);
        rock.close();

        // Reflection: rock mirrored under the waterline, faded
        Path refl = new Path(rock);
        Matrix m = new Matrix();
        m.setScale(1f, -0.8f, cx, base);
        refl.transform(m);
        p.setShader(new LinearGradient(0, base, 0, base + rh * 0.8f,
                0x55303E3B, 0x00303E3B, Shader.TileMode.CLAMP));
        c.drawPath(refl, p);

        // Rock body: lit from above by the moon
        p.setShader(new LinearGradient(cx - rw * 0.3f, base - rh, cx + rw * 0.2f, base,
                new int[]{0xFF6F7E78, 0xFF34403C, 0xFF111816},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(rock, p);

        // Waterline
        p.setShader(null);
        p.setColor(0x339FE6DC);
        p.setStrokeWidth(Math.max(1f, rh * 0.03f));
        c.drawLine(cx - rw * 0.95f, base, cx + rw * 0.95f, base, p);
    }
}
