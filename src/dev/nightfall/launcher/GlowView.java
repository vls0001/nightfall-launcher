package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A soft teal light that glides behind whichever tile has focus. */
public class GlowView extends View {
    final int blur = Ui.dp(22);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public GlowView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // blur needs software drawing; only redrawn on resize
        paint.setColor(0xAA70E0D2);
        paint.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    protected void onDraw(Canvas c) {
        rect.set(blur, blur, getWidth() - blur, getHeight() - blur);
        c.drawRoundRect(rect, Ui.dp(22), Ui.dp(22), paint);
    }
}
