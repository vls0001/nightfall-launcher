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
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The night lake wallpaper. The still parts (sky, moon, rocks) are painted once into a bitmap;
 * twinkling stars and drifting water shimmer are drawn live on top at ~25 fps.
 */
public class SceneView extends View {
    private static final float HORIZON = 0.45f;
    private static final float MOON_X = 0.84f, MOON_Y = 0.13f;

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

    private Bitmap cache;
    private boolean running;
    private final Paint fx = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Twinkling stars
    private float[] sx, sy, sr, sPhase, sSpeed;
    private int[] sAlpha;
    // Drifting shimmer lines on the water
    private float[] lx, ly, lLen, lSpeed, lPhase;
    private int[] lAlpha;

    public SceneView(Context context) {
        super(context);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 40);
        }
    };

    public void setRunning(boolean run) {
        running = run;
        removeCallbacks(tick);
        if (run) post(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        if (cache != null) cache.recycle();
        cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        paintStill(new Canvas(cache), w, h);
        seedLive(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (cache == null || sx == null) return;
        c.drawBitmap(cache, 0, 0, null);
        float t = SystemClock.uptimeMillis() / 1000f;
        int w = getWidth();

        for (int i = 0; i < sx.length; i++) {
            float k = 0.5f + 0.5f * (float) Math.sin(t * sSpeed[i] + sPhase[i]);
            fx.setColor(((int) (sAlpha[i] * (0.15f + 0.85f * k * k)) << 24) | 0xF4FFFE);
            c.drawCircle(sx[i], sy[i], sr[i] * (0.8f + 0.4f * k), fx);
        }

        fx.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < lx.length; i++) {
            float span = w + lLen[i];
            float x = ((lx[i] + t * lSpeed[i]) % span + span) % span - lLen[i];
            float k = 0.5f + 0.5f * (float) Math.sin(t * 0.7f + lPhase[i]);
            fx.setStrokeWidth(Math.max(1f, getHeight() / 900f));
            fx.setColor(((int) (lAlpha[i] * (0.3f + 0.7f * k)) << 24) | 0xA8EEE4);
            c.drawLine(x, ly[i], x + lLen[i], ly[i], fx);
        }
    }

    private void seedLive(int w, int h) {
        Random r = new Random(42);
        float horizon = h * HORIZON;
        int n = 90;
        sx = new float[n]; sy = new float[n]; sr = new float[n];
        sPhase = new float[n]; sSpeed = new float[n]; sAlpha = new int[n];
        float unit = Math.max(1f, h / 1080f);
        for (int i = 0; i < n; i++) {
            sx[i] = r.nextFloat() * w;
            sy[i] = (float) Math.pow(r.nextFloat(), 1.4) * horizon * 0.85f;
            sr[i] = (0.9f + r.nextFloat() * 1.5f) * unit;
            sPhase[i] = r.nextFloat() * 6.28f;
            sSpeed[i] = 0.4f + r.nextFloat() * 1.6f;
            sAlpha[i] = 120 + r.nextInt(135);
        }

        int m = 70;
        lx = new float[m]; ly = new float[m]; lLen = new float[m];
        lSpeed = new float[m]; lPhase = new float[m]; lAlpha = new int[m];
        for (int i = 0; i < m; i++) {
            float depth = (float) Math.pow(r.nextFloat(), 1.5);
            ly[i] = horizon + h * 0.006f + depth * h * 0.36f;
            boolean moonPath = i < 22;
            lLen[i] = (moonPath ? 0.015f : 0.03f + r.nextFloat() * 0.07f) * w * (0.5f + depth);
            lx[i] = moonPath ? MOON_X * w + (r.nextFloat() - 0.5f) * w * 0.05f : r.nextFloat() * w;
            lSpeed[i] = moonPath ? 0f : (4f + r.nextFloat() * 10f) * unit * (r.nextBoolean() ? 1 : -1);
            lPhase[i] = r.nextFloat() * 6.28f;
            lAlpha[i] = moonPath ? 70 + r.nextInt(80) : 18 + r.nextInt(40);
        }
    }

    private static void paintStill(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = h * HORIZON;
        Random rnd = new Random(1987);
        float unit = Math.max(1f, h / 1080f);
        float mx = MOON_X * w, my = MOON_Y * h, mr = h * 0.032f;

        // Sky
        shade(p, new LinearGradient(0, 0, 0, horizon,
                new int[]{0xFF010306, 0xFF061017, 0xFF0F262B},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);

        // Faint background stars
        p.setShader(null);
        for (int i = 0; i < 220; i++) {
            float y = (float) Math.pow(rnd.nextFloat(), 1.6) * horizon * 0.92f;
            float x = rnd.nextFloat() * w;
            p.setColor(((30 + rnd.nextInt(110)) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (0.5f + rnd.nextFloat() * 0.9f) * unit, p);
        }

        // Horizon glow
        shade(p, new RadialGradient(w * 0.5f, horizon, w * 0.6f,
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
        shade(p, new LinearGradient(0, horizon, 0, h,
                new int[]{0xFF0C2126, 0xFF07161A, 0xFF020608},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);

        // Moonlight column on the water
        shade(p, new LinearGradient(0, horizon, 0, horizon + h * 0.4f,
                0x22BFEDE6, 0x00BFEDE6, Shader.TileMode.CLAMP));
        c.drawRect(mx - w * 0.035f, horizon, mx + w * 0.035f, horizon + h * 0.4f, p);

        // Still faint ripples
        p.setShader(null);
        p.setStrokeWidth(Math.max(1f, unit));
        for (int i = 0; i < 120; i++) {
            float d = (float) Math.pow(rnd.nextFloat(), 1.8);
            float y = horizon + d * (h - horizon);
            float len = (0.02f + rnd.nextFloat() * 0.08f) * w * (0.4f + d);
            float x = rnd.nextFloat() * w;
            p.setColor(((8 + rnd.nextInt(18)) << 24) | 0x9FE6DC);
            c.drawLine(x, y, x + len, y, p);
        }

        // Mist along the horizon
        shade(p, new LinearGradient(0, horizon - h * 0.05f, 0, horizon + h * 0.06f,
                new int[]{0x00BFEDE6, 0x1ABFEDE6, 0x00BFEDE6}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - h * 0.05f, w, horizon + h * 0.06f, p);

        for (float[] r : ROCKS) drawRock(c, p, rnd, r[0] * w, r[1] * h, r[2] * w, r[3] * h);

        // Vignette
        shade(p, new RadialGradient(w * 0.5f, h * 0.45f, (float) Math.hypot(w, h) * 0.62f,
                new int[]{0x00000000, 0x00000000, 0xB0000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Moon with a soft halo (after the vignette so it stays bright)
        shade(p, new RadialGradient(mx, my, mr * 7f,
                new int[]{0x66DFFBF6, 0x1FCFF5EF, 0x00CFF5EF}, new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr * 7f, p);
        shade(p, new RadialGradient(mx - mr * 0.3f, my - mr * 0.3f, mr * 1.3f,
                0xFFFFFFFF, 0xFFD6EEEA, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, mr, p);
    }

    /** Sets a gradient at full strength (a leftover colour alpha would otherwise fade it). */
    private static void shade(Paint p, Shader shader) {
        p.setColor(0xFF000000);
        p.setShader(shader);
    }

    private static void drawRock(Canvas c, Paint p, Random rnd, float cx, float base, float rw, float rh) {
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

        Path refl = new Path(rock);
        Matrix m = new Matrix();
        m.setScale(1f, -0.8f, cx, base);
        refl.transform(m);
        shade(p, new LinearGradient(0, base, 0, base + rh * 0.8f,
                0x55303E3B, 0x00303E3B, Shader.TileMode.CLAMP));
        c.drawPath(refl, p);

        // Lit from the moon (upper right)
        shade(p, new LinearGradient(cx + rw * 0.4f, base - rh, cx - rw * 0.3f, base,
                new int[]{0xFF7A8A84, 0xFF36433F, 0xFF101715},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(rock, p);

        p.setShader(null);
        p.setColor(0x339FE6DC);
        p.setStrokeWidth(Math.max(1f, rh * 0.03f));
        c.drawLine(cx - rw * 0.95f, base, cx + rw * 0.95f, base, p);
    }
}
