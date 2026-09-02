package app.morphe.extension.tiktok.theme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Maps TikTok 46.7.3 Compose packed sRGB colors to the active BlueIT palette.
 *
 * SettingsComposeRvmpFragment renders its real background/cards inside LX/0VGt from LX/05Pc
 * packed Color longs, after the outer Android ComposeView has already been styled. This resolver is
 * injected immediately after those palette reads. Non-sRGB/extended-color-space longs and colorful
 * functional/status colors deliberately fall through unchanged.
 */
@SuppressWarnings("unused")
public final class ThemeComposeColorResolver {
    private static final int TIKTOK_ACCENT = Color.rgb(254, 44, 85);
    private static final int TIKTOK_LIGHT_TEXT = Color.rgb(22, 24, 35);
    private static final AtomicInteger LOG_BUDGET = new AtomicInteger(18);

    private ThemeComposeColorResolver() {}

    public static long mapColor(long packed) {
        try {
            // Compose's common sRGB Color(Int) representation keeps ARGB in the high 32 bits and
            // zero in the low 32 bits. Do not touch extended color-space encodings.
            if ((packed & 0xFFFFFFFFL) != 0L) return packed;

            int stock = (int) (packed >>> 32);
            int alpha = Color.alpha(stock);
            if (alpha == 0) return packed;

            Context context = Utils.getContext();
            if (context == null || ThemeEngine.isDefaultPreset()) return packed;

            Integer mapped = mapArgb(stock, context);
            if (mapped == null || mapped == stock) return packed;

            int stockAlpha = Color.alpha(stock);
            int mappedAlpha = Color.alpha(mapped);
            if (stockAlpha < mappedAlpha) {
                mapped = Color.argb(
                        stockAlpha,
                        Color.red(mapped),
                        Color.green(mapped),
                        Color.blue(mapped)
                );
            }

            if (LOG_BUDGET.getAndDecrement() > 0) {
                final int from = stock;
                final int to = mapped;
                Logger.printInfo(() -> String.format(
                        "[BlueIT Theme Compose] mapped #%08X -> #%08X",
                        from,
                        to
                ));
            }

            return (Integer.toUnsignedLong(mapped) << 32);
        } catch (Throwable ignored) {
            return packed;
        }
    }

    private static Integer mapArgb(int stock, Context context) {
        int opaque = Color.rgb(Color.red(stock), Color.green(stock), Color.blue(stock));

        if (distance(opaque, TIKTOK_ACCENT) <= 34.0) {
            return ThemeEngine.accentColor(context);
        }

        int r = Color.red(opaque);
        int g = Color.green(opaque);
        int b = Color.blue(opaque);
        int spread = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));

        // Preserve colorful semantic/status/media colors.
        if (spread > 34) return null;

        double y = lightness(opaque);
        int alpha = Color.alpha(stock);
        boolean dark = stockDarkMode(context);

        // Settings & privacy on 46.7.3 uses #000000 for the dark page and roughly
        // #1E1E1E/#252525/#2C2C2C for grouped cards. These boundaries mirror the proven TUX
        // stock-color inference while operating directly on Compose packed palette values.
        if (dark) {
            if (y <= 0.045) return ThemeEngine.backgroundColor(context);
            if (y <= 0.22) return ThemeEngine.surfaceColor(context);
            if (y >= 0.82) return ThemeEngine.textColor(context);
            if (alpha < 100) return ThemeEngine.dividerColor(context);
            if (y >= 0.34) return ThemeEngine.secondaryTextColor(context);
            return ThemeEngine.dividerColor(context);
        }

        if (distance(opaque, TIKTOK_LIGHT_TEXT) <= 48.0 || y <= 0.12) {
            return ThemeEngine.textColor(context);
        }
        if (y >= 0.965) return ThemeEngine.backgroundColor(context);
        if (y >= 0.80) return ThemeEngine.surfaceColor(context);
        if (alpha < 100) return ThemeEngine.dividerColor(context);
        if (y <= 0.64) return ThemeEngine.secondaryTextColor(context);
        return ThemeEngine.dividerColor(context);
    }

    private static boolean stockDarkMode(Context context) {
        try {
            int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static double lightness(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double distance(int first, int second) {
        int dr = Color.red(first) - Color.red(second);
        int dg = Color.green(first) - Color.green(second);
        int db = Color.blue(first) - Color.blue(second);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
