package dev.nightfall.launcher;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.TextView;

/** Big clock text with a pale frosted-teal gradient and soft glow. */
public class ClockView extends TextView {
    public ClockView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // needed for the glow behind gradient text
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float top = getPaddingTop() + getTextSize() * 0.1f;
        float bottom = getPaddingTop() + getTextSize();
        getPaint().setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{0xFFF4FFFE, 0xFFD3F3EF, 0xFF8FCFC8},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }
}
