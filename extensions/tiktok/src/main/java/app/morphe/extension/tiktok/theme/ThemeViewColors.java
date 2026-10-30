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
    private static final WeakHashMap<Drawable, Boolean> BITMAP_GLYPHS = new WeakHashMap<>();
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
        return new StateColors(original, primary, secondary, null);
    }

    static ColorStateList stateColors(ColorStateList original, int primary, int secondary, Integer background) {
        return new StateColors(original, primary, secondary, background);
    }

    private static final class StateColors extends ColorStateList {
        final ColorStateList original;
        final int primary, secondary;
        final Integer background;
        StateColors(ColorStateList original, int primary, int secondary, Integer background) {
            super(new int[][]{new int[0]}, new int[]{map(original.getDefaultColor(), primary, secondary, background)});
            this.original = original; this.primary = primary; this.secondary = secondary; this.background = background;
        }
        private static int map(int nativeColor, int primary, int secondary, Integer background) {
            if (!neutral(nativeColor)) return background == null ? nativeColor : readableFunctionalColor(nativeColor, background);
            int low = Math.min(Color.red(nativeColor), Math.min(Color.green(nativeColor), Color.blue(nativeColor)));
            int high = Math.max(Color.red(nativeColor), Math.max(Color.green(nativeColor), Color.blue(nativeColor)));
            int target = high > 60 && low < 220 ? secondary : primary;
            int alpha = Color.alpha(nativeColor) * Color.alpha(target) / 255;
            return (target & 0x00ffffff) | (alpha << 24);
        }
        @Override public boolean isStateful() { return original.isStateful(); }
        @Override public boolean isOpaque() { return original.isOpaque() && Color.alpha(primary) == 255 && Color.alpha(secondary) == 255; }
        @Override public int getColorForState(int[] states, int fallback) {
            return map(original.getColorForState(states, fallback), primary, secondary, background);
        }
        @Override public ColorStateList withAlpha(int alpha) {
            return stateColors(original.withAlpha(alpha), primary, secondary, background);
        }
    }

    static Integer flatColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        if (Build.VERSION.SDK_INT >= 24 && drawable instanceof GradientDrawable) {
            ColorStateList fill = ((GradientDrawable) drawable).getColor();
            if (fill != null) return fill.getDefaultColor();
        }
        if (drawable instanceof android.graphics.drawable.InsetDrawable)
            return flatColor(((android.graphics.drawable.InsetDrawable) drawable).getDrawable());
        if (drawable instanceof android.graphics.drawable.StateListDrawable && drawable.getCurrent() != drawable)
            return flatColor(drawable.getCurrent());
        if (drawable instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (layers.getId(i) == android.R.id.mask) continue;
                Integer fill = flatColor(layers.getDrawable(i));
                if (fill != null && Color.alpha(fill) == 255) return fill;
            }
        }
        return null;
    }

    /** Copy native wrappers instead of flattening their rounded content, masks or ripple states. */
    static Drawable copyFlatFill(Drawable nativeDrawable, int color) {
        if (nativeDrawable == null || nativeDrawable.getConstantState() == null) return null;
        Drawable copy = nativeDrawable.getConstantState().newDrawable().mutate();
        if (copy instanceof ColorDrawable) { ((ColorDrawable) copy).setColor(color); return copy; }
        if (copy instanceof GradientDrawable && flatColor(copy) != null) {
            GradientDrawable shape = (GradientDrawable) copy;
            ColorStateList states = shape.getColor();
            shape.setColor(states != null && states.isStateful() ? stateColors(states, color, color) : ColorStateList.valueOf(color));
            return shape;
        }
        if (Build.VERSION.SDK_INT >= 23 && copy instanceof android.graphics.drawable.InsetDrawable) {
            android.graphics.drawable.InsetDrawable inset = (android.graphics.drawable.InsetDrawable) copy;
            Drawable child = copyFlatFill(inset.getDrawable(), color);
            if (child != null) { inset.setDrawable(child); return inset; }
        }
        if (Build.VERSION.SDK_INT >= 29 && copy instanceof android.graphics.drawable.StateListDrawable) {
            android.graphics.drawable.StateListDrawable source = (android.graphics.drawable.StateListDrawable) copy;
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            for (int i = 0; i < source.getStateCount(); i++) {
                Drawable child = source.getStateDrawable(i);
                Integer fill = flatColor(child);
                Drawable mapped = fill != null && neutral(fill) ? copyFlatFill(child, color) : null;
                states.addState(source.getStateSet(i), mapped == null ? child : mapped);
            }
            states.setState(nativeDrawable.getState());
            return states;
        }
        if (copy instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers = (android.graphics.drawable.LayerDrawable) copy;
            boolean changed = false;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (layers.getId(i) == android.R.id.mask) continue;
                Integer fill = flatColor(layers.getDrawable(i));
                if (fill == null || !neutral(fill)) continue;
                Drawable child = copyFlatFill(layers.getDrawable(i), color);
                if (child != null && Build.VERSION.SDK_INT >= 23) { layers.setDrawable(i, child); changed = true; }
            }
            if (changed) return layers;
        }
        return null;
    }

    /** Preserve functional filled buttons: light theme text can be unreadable on a cyan action. */
    static int contrastForeground(View view, int candidate) {
        View current = view;
        for (int i = 0; i < 8; i++) {
            Drawable drawable = current.getBackground();
            Integer fill = flatColor(drawable);
            // An unused tint cannot paint a missing/fully transparent Drawable.
            if (fill != null && Color.alpha(fill) != 0 && current.getBackgroundTintList() != null)
                fill = current.getBackgroundTintList().getColorForState(current.getDrawableState(), 0);
            if (fill != null && Color.alpha(fill) == 255) {
                if (neutral(fill)) return candidate;
                double backdrop = luminance(fill), foreground = luminance(candidate);
                double ratio = (Math.max(backdrop, foreground) + 0.05) / (Math.min(backdrop, foreground) + 0.05);
                if (ratio >= 4.5) return candidate;
                return (backdrop + 0.05) / 0.05 > 1.05 / (backdrop + 0.05) ? Color.BLACK : Color.WHITE;
            }
            if (current.getBackground() != null && fill == null) return candidate;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return candidate;
    }

    /** Keep a functional hue while making an opaque label readable on its themed page. */
    static int readableFunctionalColor(int color, int background) {
        if (Color.alpha(color) < 255) return color; // Preserve disabled/translucent states.
        double back = luminance(background);
        int end = back < 0.5 ? 255 : 0;
        for (int step = 0; step <= 20; step++) {
            float amount = step / 20f;
            int mapped = Color.rgb(Math.round(Color.red(color) + (end - Color.red(color)) * amount),
                    Math.round(Color.green(color) + (end - Color.green(color)) * amount),
                    Math.round(Color.blue(color) + (end - Color.blue(color)) * amount));
            double fore = luminance(mapped);
            if ((Math.max(back, fore) + 0.05) / (Math.min(back, fore) + 0.05) >= 4.5) return mapped;
        }
        return color;
    }
    private static double luminance(int color) {
        double[] channels = {Color.red(color) / 255d, Color.green(color) / 255d, Color.blue(color) / 255d};
        for (int i = 0; i < channels.length; i++) channels[i] = channels[i] <= 0.04045
                ? channels[i] / 12.92 : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
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
            if (colors != null) return neutral(colors.getColorForState(view.getDrawableState(), colors.getDefaultColor())) || neutral(colors.getDefaultColor());
        }
        ColorStateList tint = view.getImageTintList();
        if (tint != null) return neutral(tint.getColorForState(view.getDrawableState(), tint.getDefaultColor())) || neutral(tint.getDefaultColor());
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
        return monochrome(drawable);
    }

    /** Only called for verified navigation/header/sheet controls; photos retain their pixels. */
    static boolean flatBitmapGlyph(ImageView view) {
        Drawable drawable = view.getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return false;
        Boolean cached = BITMAP_GLYPHS.get(drawable);
        if (cached != null) return cached;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled() || (long) bitmap.getWidth() * bitmap.getHeight() > 65536) return false;
        boolean ink = false, transparent = false, eligible = true;
        int low = 255, high = 0;
        for (int y = 0; y < bitmap.getHeight() && eligible; y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int pixel = bitmap.getPixel(x, y);
            if (Color.alpha(pixel) < 32) { transparent = true; continue; }
            if (!neutral(pixel)) { eligible = false; break; }
            ink = true;
            low = Math.min(low, Math.min(Color.red(pixel), Math.min(Color.green(pixel), Color.blue(pixel))));
            high = Math.max(high, Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel))));
            if (high - low > 34) { eligible = false; break; }
        }
        eligible &= ink && transparent;
        BITMAP_GLYPHS.put(drawable, eligible);
        return eligible;
    }

    static boolean monochrome(Drawable drawable) {
        if (drawable == null || drawable instanceof BitmapDrawable) return false;
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
            // Native async icon resources may not draw any pixels on the first bind.
            // An empty sample is unresolved, not permanently classified as a media image.
            if (monochrome) MONOCHROME.put(drawable, true);
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
