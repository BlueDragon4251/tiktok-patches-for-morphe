package app.morphe.extension.tiktok.theme;

import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A ReplacementSpan can draw a detached native View instead of the host TextView's paint. */
final class ThemeInlineContent {
    private static final Map<Class<?>, List<Field>> FIELDS = new WeakHashMap<>();
    private ThemeInlineContent() { }

    static <T> List<T> objects(CharSequence text, Class<T> kind) {
        List<T> result = new ArrayList<>();
        if (!(text instanceof Spanned)) return result;
        Spanned spanned = (Spanned) text;
        ReplacementSpan[] spans = spanned.getSpans(0, text.length(), ReplacementSpan.class);
        for (int i = 0; i < Math.min(spans.length, 64); i++) {
            for (Field field : fields(spans[i].getClass())) {
                if (!kind.isAssignableFrom(field.getType())) continue;
                try {
                    Object value = field.get(spans[i]);
                    if (kind.isInstance(value) && !result.contains(value)) result.add(kind.cast(value));
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return result;
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> cached = FIELDS.get(type);
        if (cached != null) return cached;
        List<Field> found = new ArrayList<>();
        for (Class<?> owner = type; owner != null && owner != ReplacementSpan.class;
                owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || found.size() >= 16) continue;
                if (!View.class.isAssignableFrom(field.getType())
                        && !Drawable.class.isAssignableFrom(field.getType())) continue;
                try { field.setAccessible(true); found.add(field); }
                catch (RuntimeException ignored) { }
            }
        }
        FIELDS.put(type, found);
        return found;
    }
}
