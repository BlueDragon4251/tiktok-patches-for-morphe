package app.morphe.extension.tiktok.theme;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import java.util.WeakHashMap;

/** Color operations for native-owned controls; never infer a media image from its dimensions. */
final class ThemeViewColors {
    private static final WeakHashMap<Drawable, Boolean> MONOCHROME = new WeakHashMap<>();
    private ThemeViewColors() {}

    static boolean neutral(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.alpha(color) != 0 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 34;
    }

    static Integer flatColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        if (Build.VERSION.SDK_INT >= 24 && drawable instanceof GradientDrawable) {
            ColorStateList fill = ((GradientDrawable) drawable).getColor();
            if (fill != null && !fill.isStateful()) return fill.getDefaultColor();
        }
        return null;
    }

    static void flatBackground(View view, int color) {
        Integer current = flatColor(view.getBackground());
        if (current == null || current != color) view.setBackgroundColor(color);
    }

    /** Explicit native image tints, or monochrome non-bitmap drawables in clickable controls. */
    static boolean icon(ImageView view) {
        ColorStateList tint = view.getImageTintList();
        if (tint != null) return !tint.isStateful() && neutral(tint.getDefaultColor());
        Drawable drawable = view.getDrawable();
        if (drawable == null || drawable instanceof BitmapDrawable) return false;
        View control = view;
        boolean clickable = false;
        for (int i = 0; i < 3; i++) {
            clickable |= control.isClickable();
            if (!(control.getParent() instanceof View)) break;
            control = (View) control.getParent();
        }
        if (!clickable) return false;
        Boolean cached = MONOCHROME.get(drawable);
        if (cached != null) return cached;
        android.graphics.Rect bounds = new android.graphics.Rect(drawable.getBounds());
        Bitmap sample = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        boolean monochrome = false;
        try {
            drawable.setBounds(0, 0, 24, 24);
            drawable.draw(new Canvas(sample));
            int[] pixels = new int[24 * 24];
            sample.getPixels(pixels, 0, 24, 0, 0, 24, 24);
            for (int pixel : pixels) {
                if (Color.alpha(pixel) < 32) continue;
                if (!neutral(pixel)) { monochrome = false; MONOCHROME.put(drawable, false); return false; }
                monochrome = true;
            }
            MONOCHROME.put(drawable, monochrome);
            return monochrome;
        } finally {
            drawable.setBounds(bounds);
            sample.recycle();
        }
    }
}
