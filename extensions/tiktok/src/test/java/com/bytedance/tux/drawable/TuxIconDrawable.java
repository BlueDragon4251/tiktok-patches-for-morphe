package com.bytedance.tux.drawable;
public final class TuxIconDrawable extends android.graphics.drawable.Drawable {
    public android.content.res.ColorStateList colors;
    @Override public void draw(android.graphics.Canvas canvas) {
        canvas.drawColor(colors == null ? android.graphics.Color.BLACK : colors.getColorForState(getState(), colors.getDefaultColor()));
    }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
    @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
}
