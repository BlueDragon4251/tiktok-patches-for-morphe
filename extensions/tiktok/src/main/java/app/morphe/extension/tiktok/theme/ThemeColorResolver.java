package app.morphe.extension.tiktok.theme;

import android.content.Context;
import android.util.TypedValue;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Maps TikTok/TUX semantic color tokens to the active BlueIT palette.
 *
 * TikTok 46.7.3 renders much of its normal UI through TUX/Compose, so changing the classic
 * Android View tree alone cannot theme the app. The bytecode patch hooks TUX's central attribute,
 * generic TypedValue and styled-attribute resolvers and calls this class before the stock resolver.
 *
 * Only well-known semantic color tokens are overridden. Unknown, media, warning, success and other
 * functional colors deliberately fall through to TikTok by returning null.
 */
@SuppressWarnings("unused")
public final class ThemeColorResolver {
    private static final int ROLE_NONE = 0;
    private static final int ROLE_BACKGROUND = 1;
    private static final int ROLE_SURFACE = 2;
    private static final int ROLE_TEXT = 3;
    private static final int ROLE_SECONDARY_TEXT = 4;
    private static final int ROLE_ACCENT = 5;
    private static final int ROLE_DIVIDER = 6;

    /** Resource ids are stable for the lifetime of one process; cache only their semantic role. */
    private static final ConcurrentHashMap<Integer, Integer> ROLE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> CONVERTER_METHOD_CACHE =
            new ConcurrentHashMap<>();
    private static final AtomicInteger TOKEN_LOG_BUDGET = new AtomicInteger(36);

    private static volatile boolean contextPrimed;

    private ThemeColorResolver() {}

    /**
     * @return BlueIT color override, or null to let TikTok's original TUX resolver continue.
     */
    public static Integer resolve(int tokenId, Context context, String patchDefaultPreset) {
        try {
            if (context == null || tokenId == 0) return null;

            // TUX can ask for colors before MainActivity.onCreate returns. Prime Morphe's context
            // from the real TUX Context first so settings are safe to read even on the first frame.
            primeContext(context);
            String preset = ThemeStateStore.initialize(context, patchDefaultPreset);
            if ("default".equals(preset)) return null;

            return resolveMappedColor(tokenId, context);
        } catch (Throwable ignored) {
            // A visual override must always fail open to TikTok's stock resolver.
            return null;
        }
    }

    /**
     * TUX 46.7.3's generic resolver accepts a Function1 that converts a resolved TypedValue to the
     * caller's requested type. Returning an Integer directly here would be verifier-safe but could
     * later ClassCastException when the caller requested a Drawable or Float. Instead create a real
     * color TypedValue and let TikTok's own converter produce exactly the expected return type.
     *
     * The converter is intentionally typed as Object so the Java extension does not need a compile
     * dependency on Kotlin's Function1 interface.
     */
    public static Object resolveGeneric(
            int tokenId,
            Context context,
            Object converter,
            String patchDefaultPreset
    ) {
        try {
            if (converter == null) return null;
            Integer color = resolve(tokenId, context, patchDefaultPreset);
            if (color == null) return null;

            Method invoke = converterMethod(converter);
            if (invoke == null) return null;

            TypedValue value = new TypedValue();
            value.type = TypedValue.TYPE_INT_COLOR_ARGB8;
            value.data = color;
            value.resourceId = 0;
            return invoke.invoke(converter, value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * TUX's styled-attribute helper receives an index plus the attribute array. Resolve the actual
     * attribute id before applying the same semantic mapping.
     */
    public static Integer resolveFromAttributeArray(
            int index,
            Context context,
            int[] attributes,
            String patchDefaultPreset
    ) {
        try {
            if (attributes == null || index < 0 || index >= attributes.length) return null;
            return resolve(attributes[index], context, patchDefaultPreset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer resolveMappedColor(int tokenId, Context context) {
        Integer cachedRole = ROLE_CACHE.get(tokenId);
        int role;
        String name = null;
        if (cachedRole != null) {
            role = cachedRole;
        } else {
            name = resourceName(tokenId, context);
            role = classifyName(name);
            ROLE_CACHE.put(tokenId, role);
        }

        if (name != null && !name.isEmpty()) {
            logTokenSample(name, role);
        }

        switch (role) {
            case ROLE_BACKGROUND:
                return ThemeEngine.backgroundColor(context);
            case ROLE_SURFACE:
                return ThemeEngine.surfaceColor(context);
            case ROLE_TEXT:
                return ThemeEngine.textColor(context);
            case ROLE_SECONDARY_TEXT:
                return ThemeEngine.secondaryTextColor(context);
            case ROLE_ACCENT:
                return ThemeEngine.accentColor(context);
            case ROLE_DIVIDER:
                return ThemeEngine.dividerColor(context);
            case ROLE_NONE:
            default:
                return null;
        }
    }

    private static Method converterMethod(Object converter) {
        Class<?> type = converter.getClass();
        Method cached = CONVERTER_METHOD_CACHE.get(type);
        if (cached != null) return cached;

        try {
            Method method = type.getMethod("invoke", Object.class);
            method.setAccessible(true);
            CONVERTER_METHOD_CACHE.put(type, method);
            return method;
        } catch (Throwable ignored) {
        }

        try {
            for (Method method : type.getMethods()) {
                if ("invoke".equals(method.getName()) && method.getParameterTypes().length == 1) {
                    method.setAccessible(true);
                    CONVERTER_METHOD_CACHE.put(type, method);
                    return method;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void primeContext(Context context) {
        if (contextPrimed) return;
        synchronized (ThemeColorResolver.class) {
            if (contextPrimed) return;
            try {
                Utils.setContext(context);
                contextPrimed = true;
            } catch (Throwable ignored) {
                // Leave false so a later TUX lookup can retry with a fully attached Context.
            }
        }
    }

    private static int classifyName(String name) {
        if (name == null || name.isEmpty()) return ROLE_NONE;

        String token = normalize(name);
        int tuxColorStart = token.indexOf("tuxcolor");
        if (tuxColorStart >= 0) {
            token = token.substring(tuxColorStart + "tuxcolor".length());
        }

        // Keep media overlays and semantic state colors (success/warning/info/error) native.
        if (containsAny(token,
                "imageoverlay", "brandtiktok", "success", "warning", "danger", "error", "info")) {
            return ROLE_NONE;
        }

        // Primary app/page backgrounds.
        if (token.equals("bgprimary")
                || token.equals("uipageflat1")
                || token.equals("uipagegrouped1")) {
            return ROLE_BACKGROUND;
        }

        // Raised pages, sheets, cards and neutral shapes all use the BlueIT surface color.
        if (token.equals("bgsecondary")
                || token.startsWith("uipageflat")
                || token.startsWith("uipagegrouped")
                || token.startsWith("uisheetflat")
                || token.startsWith("uishapeneutral")
                || token.startsWith("uishapesecondarymuted")) {
            return ROLE_SURFACE;
        }

        if (token.equals("uitext1")
                || token.equals("uitext1display")
                || token.equals("uitextprimary")
                || token.equals("uitextprimarydisplay")) {
            return ROLE_TEXT;
        }

        if (token.equals("uitext2")
                || token.equals("uitext3")
                || token.equals("uitext3color")
                || token.equals("uitextplaceholder")
                || token.contains("placeholder")
                || token.contains("textsecondary")) {
            return ROLE_SECONDARY_TEXT;
        }

        if (token.equals("uishapeprimary")
                || token.equals("accent")
                || token.equals("accentprimary")) {
            return ROLE_ACCENT;
        }

        if (containsAny(token, "divider", "separator", "border", "stroke")) {
            return ROLE_DIVIDER;
        }

        return ROLE_NONE;
    }

    private static String resourceName(int tokenId, Context context) {
        try {
            return context.getResources().getResourceEntryName(tokenId);
        } catch (Throwable ignored) {
        }

        try {
            return context.getResources().getResourceName(tokenId);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void logTokenSample(String name, int role) {
        try {
            if (TOKEN_LOG_BUDGET.getAndDecrement() <= 0) return;
            String roleName;
            switch (role) {
                case ROLE_BACKGROUND:
                    roleName = "background";
                    break;
                case ROLE_SURFACE:
                    roleName = "surface";
                    break;
                case ROLE_TEXT:
                    roleName = "text";
                    break;
                case ROLE_SECONDARY_TEXT:
                    roleName = "secondary-text";
                    break;
                case ROLE_ACCENT:
                    roleName = "accent";
                    break;
                case ROLE_DIVIDER:
                    roleName = "divider";
                    break;
                default:
                    roleName = "native";
                    break;
            }
            final String message = "[BlueIT Theme TUX] token=" + name + " role=" + roleName;
            Logger.printInfo(() -> message);
        } catch (Throwable ignored) {
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }
}
