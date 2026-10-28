package dev.nightfall.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Simple drawn icons for the "All apps" and "Settings" tiles. */
public class Glyphs extends Drawable {
    public static final int GRID = 0;
    public static final int GEAR = 1;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Glyphs(int kind) {
        this.kind = kind;
        paint.setColor(0xFFF2FFFD);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        if (kind == GRID) {
            float cell = s / 3.4f, box = cell * 0.78f, r = box * 0.28f;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    float x = cx + (i - 1) * cell - box / 2f;
                    float y = cy + (j - 1) * cell - box / 2f;
                    c.drawRoundRect(new RectF(x, y, x + box, y + box), r, r, paint);
                }
            }
        } else {
            float outer = s * 0.34f, tooth = s * 0.14f;
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, cx, cy);
                c.drawRoundRect(new RectF(cx - tooth / 2f, cy - outer - tooth * 0.6f,
                        cx + tooth / 2f, cy - outer * 0.6f), tooth * 0.25f, tooth * 0.25f, paint);
                c.restore();
            }
            Paint ring = new Paint(paint);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(outer * 0.55f);
            c.drawCircle(cx, cy, outer * 0.72f, ring);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
