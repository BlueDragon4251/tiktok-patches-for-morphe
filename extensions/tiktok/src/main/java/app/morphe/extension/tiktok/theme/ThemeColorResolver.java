package app.morphe.extension.tiktok.theme;

import android.content.Context;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Utils;

/**
 * Maps TikTok/TUX semantic color tokens to the active BlueIT palette.
 *
 * TikTok 46.7.3 renders much of its normal UI through TUX/Compose, so changing the classic
 * Android View tree alone cannot theme the app. The bytecode patch hooks TUX's central integer
 * color resolvers (LX/0547.LIZ/LIZJ) and calls this class before the stock resolver.
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

    private static volatile boolean contextPrimed;
    private static volatile boolean patchDefaultChecked;

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
            initializePatchDefaultOnce(patchDefaultPreset);

            if (ThemeEngine.isDefaultPreset()) return null;

            Integer cachedRole = ROLE_CACHE.get(tokenId);
            int role;
            if (cachedRole != null) {
                role = cachedRole;
            } else {
                role = classify(tokenId, context);
                ROLE_CACHE.put(tokenId, role);
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
        } catch (Throwable ignored) {
            // A visual override must always fail open to TikTok's stock resolver.
            return null;
        }
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

    private static void initializePatchDefaultOnce(String patchDefaultPreset) {
        if (patchDefaultChecked || !contextPrimed) return;
        synchronized (ThemeColorResolver.class) {
            if (patchDefaultChecked || !contextPrimed) return;
            try {
                ThemeEngine.initializePatchDefault(
                        patchDefaultPreset == null || patchDefaultPreset.isEmpty()
                                ? "default"
                                : patchDefaultPreset
                );
                patchDefaultChecked = true;
            } catch (Throwable ignored) {
                // Retry on a later resolver call. ThemeEngine itself is also fail-open.
            }
        }
    }

    private static int classify(int tokenId, Context context) {
        String name = resourceName(tokenId, context);
        if (name.isEmpty()) return ROLE_NONE;

        String token = normalize(name);
        if (token.startsWith("tuxcolor")) token = token.substring("tuxcolor".length());

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

        // Do not replace text specifically defined as "on primary/neutral"; TikTok may need white
        // there for accessibility on badges/buttons. Generic semantic text is safe to theme.
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
