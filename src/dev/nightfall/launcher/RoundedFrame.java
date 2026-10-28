package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** A FrameLayout whose content is clipped to rounded corners. */
public class RoundedFrame extends FrameLayout {
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float radius;

    public RoundedFrame(Context context, float radiusDp) {
        super(context);
        radius = Ui.dp(radiusDp);
        setBackground(Ui.rounded(0xFF0B1A1D, radiusDp));
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true); // smooth corners when hardware drawing is on
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        path.reset();
        rect.set(0, 0, w, h);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        if (c.isHardwareAccelerated()) {
            super.dispatchDraw(c);
            return;
        }
        int save = c.save();
        c.clipPath(path);
        super.dispatchDraw(c);
        c.restoreToCount(save);
    }
}
