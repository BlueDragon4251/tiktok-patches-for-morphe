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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Color operations for native-owned controls; never infer a media image from its dimensions. */
final class ThemeViewColors {
    private static final WeakHashMap<Drawable, Boolean> MONOCHROME = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Method[]> BUBBLE_API = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Method> TINT_SETTERS = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Field> TINT_FIELDS = new WeakHashMap<>();
    private static final WeakHashMap<View, BubbleFill> BUBBLES = new WeakHashMap<>();
    private static final String BUBBLE = "com.ss.android.ugc.aweme.social.thought.view.SocialThoughtBaseBubbleBackgroundView";
    private ThemeViewColors() {}

    static boolean neutral(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.alpha(color) != 0 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 34;
    }

    /** Preserve native state selection and functional colors using public ColorStateList APIs. */
    static ColorStateList stateColors(ColorStateList original, int primary, int secondary) {
        return new StateColors(original, primary, secondary);
    }

    private static final class StateColors extends ColorStateList {
        final ColorStateList original;
        final int primary, secondary;
        StateColors(ColorStateList original, int primary, int secondary) {
            super(new int[][]{new int[0]}, new int[]{map(original.getDefaultColor(), primary, secondary)});
            this.original = original; this.primary = primary; this.secondary = secondary;
        }
        private static int map(int nativeColor, int primary, int secondary) {
            if (!neutral(nativeColor)) return nativeColor;
            int low = Math.min(Color.red(nativeColor), Math.min(Color.green(nativeColor), Color.blue(nativeColor)));
            int high = Math.max(Color.red(nativeColor), Math.max(Color.green(nativeColor), Color.blue(nativeColor)));
            int target = high > 60 && low < 220 ? secondary : primary;
            int alpha = Color.alpha(nativeColor) * Color.alpha(target) / 255;
            return (target & 0x00ffffff) | (alpha << 24);
        }
        @Override public boolean isStateful() { return original.isStateful(); }
        @Override public boolean isOpaque() { return original.isOpaque() && Color.alpha(primary) == 255 && Color.alpha(secondary) == 255; }
        @Override public int getColorForState(int[] states, int fallback) {
            return map(original.getColorForState(states, fallback), primary, secondary);
        }
        @Override public ColorStateList withAlpha(int alpha) {
            return stateColors(original.withAlpha(alpha), primary, secondary);
        }
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
        if (view.getDrawable() == null || view.getDrawable() instanceof BitmapDrawable) return false;
        NativeTint nativeTint = NativeTint.find(view);
        if (nativeTint != null) {
            ColorStateList colors = nativeTint.get(view.getDrawable());
            if (colors != null) return neutral(colors.getDefaultColor());
        }
        ColorStateList tint = view.getImageTintList();
        if (tint != null) return neutral(tint.getDefaultColor());
        Drawable drawable = view.getDrawable();
        if (drawable == null || drawable instanceof BitmapDrawable) return false;
        View control = view;
        boolean clickable = false;
        for (int i = 0; i < 3; i++) {
            clickable |= control.isClickable();
            if (!(control.getParent() instanceof View)) break;
            control = (View) control.getParent();
        }
        // TuxNavBar dispatches actions from its model; its child icons and their first
        // ancestors need not be clickable. The verified native tint API still owns them.
        if (!clickable && nativeTint == null) return false;
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

    static void bubble(View view, int color) {
        ThemeThoughtRenderer.style(view, color);
        try {
            BubbleFill fill = BUBBLES.get(view);
            if (fill == null) {
                Class<?> type = view.getClass();
                Method[] api = BUBBLE_API.get(type);
                if (!BUBBLE_API.containsKey(type)) {
                    BUBBLE_API.put(type, null);
                    Method getter = type.getMethod("getFillColor");
                    if (!getter.getDeclaringClass().getName().equals(BUBBLE) || getter.getReturnType() != int.class) return;
                    api = new Method[]{getter, type.getMethod("setFillColor", int.class)};
                    BUBBLE_API.put(type, api);
                }
                if (api == null) return;
                Method getter = api[0], setter = api[1];
                fill = new BubbleFill(getter, setter, (Integer) getter.invoke(view));
                BUBBLES.put(view, fill);
            }
            int current = (Integer) fill.getter.invoke(view);
            if (current != fill.applied) fill.original = current;
            if (!neutral(fill.original)) return;
            if (current != color) fill.setter.invoke(view, color);
            fill.applied = color;
        } catch (ReflectiveOperationException ignored) { }
    }

    static void restoreBubbles(View root) {
        ThemeThoughtRenderer.restore(root);
        java.util.Iterator<java.util.Map.Entry<View, BubbleFill>> entries = BUBBLES.entrySet().iterator();
        while (entries.hasNext()) {
            java.util.Map.Entry<View, BubbleFill> entry = entries.next();
            View view = entry.getKey();
            if (view.getRootView() != root) continue;
            BubbleFill fill = entry.getValue();
            try {
                if ((Integer) fill.getter.invoke(view) == fill.applied) fill.setter.invoke(view, fill.original);
            } catch (ReflectiveOperationException ignored) { }
            entries.remove();
        }
    }

    private static final class BubbleFill {
        final Method getter, setter;
        int original, applied;
        BubbleFill(Method getter, Method setter, int original) {
            this.getter = getter; this.setter = setter; this.original = original; applied = original;
        }
    }

    /** TuxIconView's public setter writes the sole ColorStateList consumed again by draw(). */
    static final class NativeTint {
        final Method setter;
        final Field colors;
        private NativeTint(Method setter, Field colors) { this.setter = setter; this.colors = colors; }
        static NativeTint find(ImageView view) {
            try {
                if (view.getDrawable() == null) return null;
                Class<?> type = view.getClass(), drawableType = view.getDrawable().getClass();
                Method setter = TINT_SETTERS.get(type);
                if (!TINT_SETTERS.containsKey(type)) {
                    TINT_SETTERS.put(type, null);
                    setter = type.getMethod("setTintColorStateList$tux_theme_release", ColorStateList.class);
                    TINT_SETTERS.put(type, setter);
                }
                if (setter == null) return null;
                Field field = TINT_FIELDS.get(drawableType);
                if (!TINT_FIELDS.containsKey(drawableType)) {
                    TINT_FIELDS.put(drawableType, null);
                    for (Field candidate : drawableType.getDeclaredFields()) {
                        if (candidate.getType() != ColorStateList.class || Modifier.isStatic(candidate.getModifiers())) continue;
                        if (field != null) return null;
                        field = candidate;
                    }
                    if (field != null) { field.setAccessible(true); TINT_FIELDS.put(drawableType, field); }
                }
                if (field == null) return null;
                return new NativeTint(setter, field);
            } catch (ReflectiveOperationException ignored) { return null; }
        }
        ColorStateList get(Drawable drawable) {
            try { return (ColorStateList) colors.get(drawable); }
            catch (ReflectiveOperationException ignored) { return null; }
        }
        void set(ImageView view, ColorStateList value) {
            try { setter.invoke(view, value); }
            catch (ReflectiveOperationException ignored) { }
        }
    }
}
