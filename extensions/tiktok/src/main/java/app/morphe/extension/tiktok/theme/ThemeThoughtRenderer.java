package app.morphe.extension.tiktok.theme;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import app.morphe.extension.shared.Logger;

/** The native profile note is a TextView with a separate path painter, not a background Drawable. */
final class ThemeThoughtRenderer {
    private static final WeakHashMap<Class<?>, Adapter> ADAPTERS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, Fill> FILLS = new WeakHashMap<>();
    private ThemeThoughtRenderer() { }
    static void style(View view, int color) {
        if (!(view instanceof TextView)) return;
        TextView text = (TextView) view;
        try {
            Adapter adapter = adapter(view.getClass());
            if (adapter == null) return;
            Object painter = adapter.painter.get(view);
            // Native gradient images and stickers retain their complete rendering and colors.
            if (painter == null || adapter.gradient.invoke(view) != null
                    || Boolean.TRUE.equals(adapter.sticker.invoke(view))
                    || adapter.shader.get(painter) != null || adapter.image.get(painter) != null) return;
            Fill fill = FILLS.get(text);
            if (fill == null || fill.painter.get() != painter) {
                fill = new Fill(painter, adapter, text.getHintTextColors());
                FILLS.put(text, fill);
            }
            for (int i = 0; i < fill.paints.length; i++) {
                Paint paint = fill.paints[i];
                if (paint == null || paint.getShader() != null) continue;
                int current = paint.getColor();
                if (current != fill.applied[i]) fill.original[i] = current;
                if (!ThemeViewColors.neutral(fill.original[i])) continue;
                int mapped = (color & 0xffffff) | (Color.alpha(fill.original[i]) << 24);
                if (current != mapped) paint.setColor(mapped);
                fill.applied[i] = mapped;
            }
            if (text.getHintTextColors() != fill.appliedHint) fill.originalHint = text.getHintTextColors();
            if (ThemeViewColors.neutral(fill.originalHint.getDefaultColor())) {
                int hint = ThemeEngine.secondaryTextColor(text.getContext());
                if (fill.appliedHint == null || fill.appliedHint.getDefaultColor() != hint)
                    fill.appliedHint = ColorStateList.valueOf(hint);
                if (text.getHintTextColors() != fill.appliedHint) text.setHintTextColor(fill.appliedHint);
            }
        } catch (ReflectiveOperationException ignored) { }
    }
    static void restore(View root) {
        java.util.Iterator<java.util.Map.Entry<TextView, Fill>> it = FILLS.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<TextView, Fill> entry = it.next();
            TextView view = entry.getKey();
            if (view.getRootView() != root) continue;
            Fill fill = entry.getValue();
            for (int i = 0; i < fill.paints.length; i++) if (fill.paints[i] != null
                    && fill.paints[i].getColor() == fill.applied[i]) fill.paints[i].setColor(fill.original[i]);
            if (view.getHintTextColors() == fill.appliedHint) view.setHintTextColor(fill.originalHint);
            it.remove();
        }
    }
    private static Adapter adapter(Class<?> type) throws ReflectiveOperationException {
        if (ADAPTERS.containsKey(type)) return ADAPTERS.get(type);
        ADAPTERS.put(type, null);
        // Public native note APIs plus the reviewed painter shape avoid obfuscated class names.
        Method gradient = type.getMethod("getBubbleBackgroundGradientImgData");
        Method sticker = type.getMethod("getEnableStarSticker");
        type.getMethod("getBubbleStyle");
        if (sticker.getReturnType() != boolean.class) return null;
        Adapter found = null;
        for (Field candidate : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(candidate.getModifiers())) continue;
            ArrayList<Field> paints = new ArrayList<>();
            Field shader = null, image = null;
            int paths = 0;
            for (Field field : candidate.getType().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() == Paint.class) paints.add(field);
                else if (field.getType() == Path.class) paths++;
                else if (field.getType() == Shader.class) { if (shader != null) return null; shader = field; }
                else if (field.getType() == Drawable.class) { if (image != null) return null; image = field; }
            }
            if (paints.size() != 3 || paths != 4 || shader == null || image == null) continue;
            if (found != null) return null;
            candidate.setAccessible(true); shader.setAccessible(true); image.setAccessible(true);
            for (Field paint : paints) paint.setAccessible(true);
            found = new Adapter(candidate, paints.toArray(new Field[0]), shader, image, gradient, sticker);
        }
        ADAPTERS.put(type, found);
        if (found != null) Logger.printInfo(() -> "[BlueIT Note Painter v1] reviewed public note API and native path painter resolved");
        return found;
    }
    private static final class Adapter {
        final Field painter, shader, image;
        final Field[] paints;
        final Method gradient, sticker;
        Adapter(Field painter, Field[] paints, Field shader, Field image, Method gradient, Method sticker) {
            this.painter = painter; this.paints = paints; this.shader = shader; this.image = image;
            this.gradient = gradient; this.sticker = sticker;
        }
    }
    private static final class Fill {
        final java.lang.ref.WeakReference<Object> painter;
        final Paint[] paints;
        final int[] original, applied;
        ColorStateList originalHint, appliedHint;
        Fill(Object painter, Adapter adapter, ColorStateList hint) throws IllegalAccessException {
            this.painter = new java.lang.ref.WeakReference<>(painter); originalHint = hint;
            paints = new Paint[adapter.paints.length]; original = new int[paints.length]; applied = new int[paints.length];
            for (int i = 0; i < paints.length; i++) {
                paints[i] = (Paint) adapter.paints[i].get(painter);
                if (paints[i] != null) original[i] = applied[i] = paints[i].getColor();
            }
        }
    }
}
