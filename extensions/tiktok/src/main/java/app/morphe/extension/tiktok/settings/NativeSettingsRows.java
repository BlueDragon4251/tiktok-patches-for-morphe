package app.morphe.extension.tiktok.settings;

import java.util.ArrayList;
import java.util.List;

/** Supplies a distinct native heading followed by the existing, clickable BlueIT row. */
public final class NativeSettingsRows {
    private static Object header;
    private NativeSettingsRows() {}
    private static Object nativeHeader() { return null; } // Patched with the verified native enum constructor.
    private static Object nativeOpenDebug() { return null; } // Patched with its native enum field.
    public static synchronized List<?> prepare(List<?> original) {
        if (header == null) header = nativeHeader();
        return prepare(original, header, nativeOpenDebug());
    }
    static List<?> prepare(List<?> original, Object heading, Object entry) {
        if (original == null || heading == null || entry == null) return original;
        List<Object> result = new ArrayList<>(original.size() + 2);
        result.add(heading);
        result.add(entry);
        for (Object item : original) if (item != heading && item != entry) result.add(item);
        return result;
    }
    public static String headerTitle(Object item, String original) {
        return item instanceof Enum<?> && "BLUEIT_SERVICES".equals(((Enum<?>) item).name())
                ? "BlueIT Services" : original;
    }
}
