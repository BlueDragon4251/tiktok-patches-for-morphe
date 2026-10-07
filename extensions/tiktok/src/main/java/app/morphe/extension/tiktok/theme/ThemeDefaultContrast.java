package app.morphe.extension.tiktok.theme;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Repair native foreground/background mismatches inside verified page roots in Default. */
final class ThemeDefaultContrast {
    private static final Map<TextView, TextFill> TEXTS = new WeakHashMap<>();
    private static final Map<ImageView, IconFill> ICONS = new WeakHashMap<>();
    private ThemeDefaultContrast() { }
    static void apply(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 1800) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || view != root && ThemeNativeTargets.isOwned(view)) continue;
            Integer backdrop = backdrop(view);
            if (backdrop != null) {
                int primary = light(backdrop) ? Color.BLACK : Color.WHITE;
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    TextFill fill = TEXTS.get(text);
                    if (fill == null || text.getTextColors() != fill.applied) {
                        fill = new TextFill(text.getTextColors()); TEXTS.put(text, fill);
                    }
                    int nativeColor = fill.original.getColorForState(text.getDrawableState(), fill.original.getDefaultColor());
                    if (ThemeViewColors.neutral(nativeColor) && light(nativeColor) == light(backdrop)) {
                        if (fill.applied == null || fill.primary != primary) {
                            fill.primary = primary;
                            fill.applied = ThemeViewColors.stateColors(fill.original, primary, primary);
                        }
                        if (text.getTextColors() != fill.applied) text.setTextColor(fill.applied);
                    } else if (text.getTextColors() == fill.applied) text.setTextColor(fill.original);
                } else if (view instanceof ImageView) {
                    ImageView icon = (ImageView) view;
                    ThemeViewColors.NativeTint nativeTint = ThemeViewColors.NativeTint.find(icon);
                    if (nativeTint != null && ThemeViewColors.icon(icon)) {
                        IconFill fill = ICONS.get(icon);
                        Drawable drawable = icon.getDrawable();
                        ColorStateList current = nativeTint.get(drawable);
                        if (fill == null || fill.drawable.get() != drawable || current != fill.applied) {
                            fill = new IconFill(drawable, nativeTint, current); ICONS.put(icon, fill);
                        }
                        // Null native tint resolves from TikTok's resource; a verified monochrome
                        // icon still needs a foreground contrasting with this actual flat backdrop.
                        if (fill.applied == null || fill.primary != primary) {
                            fill.primary = primary;
                            fill.applied = fill.original == null ? ColorStateList.valueOf(primary)
                                    : ThemeViewColors.stateColors(fill.original, primary, primary);
                        }
                        if (current != fill.applied) fill.tint.set(icon, fill.applied);
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
    }
    private static boolean light(int color) {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) > 128000;
    }
    private static Integer backdrop(View view) {
        for (int i = 0; i < 20; i++) {
            Drawable drawable = view.getBackground();
            Integer color = ThemeViewColors.flatColor(drawable);
            if (color != null && Color.alpha(color) == 255) return color;
            if (drawable != null && (color == null || Color.alpha(color) != 0)) return null;
            if (!(view.getParent() instanceof View)) break;
            view = (View) view.getParent();
        }
        return null;
    }
    static void restore(View root) {
        java.util.Iterator<Map.Entry<TextView, TextFill>> texts = TEXTS.entrySet().iterator();
        while (texts.hasNext()) {
            Map.Entry<TextView, TextFill> entry = texts.next();
            if (entry.getKey().getRootView() != root.getRootView()) continue;
            if (entry.getKey().getTextColors() == entry.getValue().applied)
                entry.getKey().setTextColor(entry.getValue().original);
            texts.remove();
        }
        java.util.Iterator<Map.Entry<ImageView, IconFill>> icons = ICONS.entrySet().iterator();
        while (icons.hasNext()) {
            Map.Entry<ImageView, IconFill> entry = icons.next();
            ImageView view = entry.getKey(); IconFill fill = entry.getValue();
            if (view.getRootView() != root.getRootView()) continue;
            if (view.getDrawable() == fill.drawable.get() && fill.tint.get(view.getDrawable()) == fill.applied)
                fill.tint.set(view, fill.original);
            icons.remove();
        }
    }
    private static final class TextFill {
        final ColorStateList original;
        ColorStateList applied;
        int primary;
        TextFill(ColorStateList original) { this.original = original; }
    }
    private static final class IconFill {
        final java.lang.ref.WeakReference<Drawable> drawable;
        final ThemeViewColors.NativeTint tint;
        final ColorStateList original;
        ColorStateList applied;
        int primary;
        IconFill(Drawable drawable, ThemeViewColors.NativeTint tint, ColorStateList original) {
            this.drawable = new java.lang.ref.WeakReference<>(drawable); this.tint = tint; this.original = original;
        }
    }
}
