package app.morphe.extension.tiktok.theme;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;

/** Native TextView compound icons have their own draw-time Tux color writer. */
final class ThemeCompoundIcons {
    private static final Map<Class<?>, Field> FIELDS = new WeakHashMap<>();
    private static final Map<TextView, Map<Drawable, Fill>> FILLS = new WeakHashMap<>();
    private ThemeCompoundIcons() { }
    static void style(TextView view, int primary) {
        for (Drawable drawable : view.getCompoundDrawablesRelative()) {
            if (drawable == null) continue;
            Field field = field(drawable.getClass());
            if (field == null) continue;
            try {
                ColorStateList current = (ColorStateList) field.get(drawable);
                Map<Drawable, Fill> icons = FILLS.get(view);
                if (icons == null) { icons = new WeakHashMap<>(); FILLS.put(view, icons); }
                Fill fill = icons.get(drawable);
                if (fill == null || current != fill.applied) {
                    if (current == null ? !ThemeViewColors.monochrome(drawable)
                            : !ThemeViewColors.neutral(current.getColorForState(
                            view.getDrawableState(), current.getDefaultColor()))) continue;
                    fill = new Fill(field, current); icons.put(drawable, fill);
                }
                if (fill.applied == null || fill.primary != primary) {
                    fill.primary = primary;
                    fill.applied = fill.original == null ? ColorStateList.valueOf(primary)
                            : ThemeViewColors.stateColors(fill.original, primary, primary);
                    field.set(drawable, fill.applied);
                    drawable.invalidateSelf();
                }
            } catch (ReflectiveOperationException ignored) { }
        }
    }
    static void restore(View root) {
        java.util.Iterator<Map.Entry<TextView, Map<Drawable, Fill>>> it = FILLS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TextView, Map<Drawable, Fill>> entry = it.next();
            if (entry.getKey().getRootView() != root.getRootView()) continue;
            for (Map.Entry<Drawable, Fill> icon : entry.getValue().entrySet()) {
                try {
                    if (icon.getValue().field.get(icon.getKey()) == icon.getValue().applied) {
                        icon.getValue().field.set(icon.getKey(), icon.getValue().original);
                        icon.getKey().invalidateSelf();
                    }
                } catch (ReflectiveOperationException ignored) { }
            }
            it.remove();
        }
    }
    private static Field field(Class<?> type) {
        if (FIELDS.containsKey(type)) return FIELDS.get(type);
        FIELDS.put(type, null);
        if (!type.getName().equals("com.bytedance.tux.drawable.TuxIconDrawable")) return null;
        Field found = null;
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() != ColorStateList.class || Modifier.isStatic(field.getModifiers())) continue;
            if (found != null) return null;
            found = field;
        }
        if (found != null) { found.setAccessible(true); FIELDS.put(type, found); }
        return found;
    }
    private static final class Fill {
        final Field field;
        final ColorStateList original;
        ColorStateList applied;
        int primary;
        Fill(Field field, ColorStateList original) { this.field = field; this.original = original; }
    }
}
