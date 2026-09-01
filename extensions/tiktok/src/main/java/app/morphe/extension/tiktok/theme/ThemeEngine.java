package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * Runtime theme engine for TikTok.
 *
 * The first implementation intentionally targets window chrome and a small set of identifiable
 * navigation/sheet surfaces. It never walks the video renderer with broad color replacement rules.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeEngine {
    private static final int DEFAULT_TIKTOK_ACCENT = Color.rgb(254, 44, 85);
    private static volatile boolean installed;
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);

    private ThemeEngine() {}

    /** Called from the bytecode patch after TikTok MainActivity.onCreate completes. */
    public static void onMainActivityCreated(Activity activity) {
        if (activity == null) return;
        installed = true;
        activityRef = new WeakReference<>(activity);

        // TikTok inflates important surfaces asynchronously. Apply once immediately after onCreate
        // and twice more after short delays. No permanent global-layout listener is installed.
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> applyActivity(activity));
        decor.postDelayed(() -> applyActivity(activity), 450L);
        decor.postDelayed(() -> applyActivity(activity), 1400L);
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** Best-effort live preview for settings changes. Restart remains the authoritative refresh. */
    public static void requestReapply() {
        Activity activity = activityRef.get();
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> applyActivity(activity), 80L);
    }

    public static int accentColor(Context context) {
        return resolvePalette(context).accent;
    }

    public static int surfaceColor(Context context) {
        return resolvePalette(context).surface;
    }

    public static int backgroundColor(Context context) {
        return resolvePalette(context).background;
    }

    public static int textColor(Context context) {
        return resolvePalette(context).text;
    }

    private static void applyActivity(Activity activity) {
        try {
            String preset = normalizedPreset();
            if ("default".equals(preset)) {
                return;
            }

            Palette palette = resolvePalette(activity);
            Window window = activity.getWindow();
            window.setStatusBarColor(palette.background);
            window.setNavigationBarColor(palette.surface);

            View decor = window.getDecorView();
            updateSystemBarIconContrast(decor, palette);
            applyTargetedSurfaces(decor, palette, "liquid_glass".equals(preset));
        } catch (Throwable throwable) {
            // Theme failures must never stop TikTok from opening.
            Logger.printDebug(() -> "BlueIT Theme Engine apply failed", throwable);
        }
    }

    private static void updateSystemBarIconContrast(View decor, Palette palette) {
        boolean light = luminance(palette.background) > 0.60;
        int visibility = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (light) visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean lightNavigation = luminance(palette.surface) > 0.60;
            if (lightNavigation) visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(visibility);
    }

    private static void applyTargetedSurfaces(View view, Palette palette, boolean liquidGlass) {
        if (!(view instanceof ViewGroup)) return;

        ViewGroup group = (ViewGroup) view;
        String resourceName = resourceEntryName(view);
        String className = view.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        boolean bottomNavigation =
                (resourceName.contains("bottom") && (resourceName.contains("nav") || resourceName.contains("tab")))
                        || className.contains("bottomnavigation")
                        || className.contains("bottomtab");
        boolean sheetSurface = resourceName.contains("bottom_sheet")
                || resourceName.contains("comments_panel")
                || resourceName.contains("comment_panel")
                || resourceName.contains("share_panel")
                || className.contains("bottomsheet");

        if (bottomNavigation || sheetSurface) {
            if (liquidGlass) {
                group.setBackground(glassDrawable(group.getContext(), palette));
                group.setElevation(dp(group.getContext(), 8));
            } else {
                group.setBackgroundTintList(ColorStateList.valueOf(palette.surface));
            }
        }

        for (int index = 0; index < group.getChildCount(); index++) {
            applyTargetedSurfaces(group.getChildAt(index), palette, liquidGlass);
        }
    }

    private static GradientDrawable glassDrawable(Context context, Palette palette) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(palette.surface);
        drawable.setCornerRadius(dp(context, clamped(ThemeSettings.GLASS_CORNER_RADIUS_DP.get(), 0, 48)));
        int borderAlpha = isDark(context) ? 90 : 70;
        int border = Color.argb(
                borderAlpha,
                Color.red(palette.text),
                Color.green(palette.text),
                Color.blue(palette.text)
        );
        drawable.setStroke(Math.max(1, Math.round(dp(context, 1))), border);
        return drawable;
    }

    private static Palette resolvePalette(Context context) {
        String preset = normalizedPreset();
        boolean dark = isDark(context);

        switch (preset) {
            case "material_you": {
                int background = dynamicColor(context, dark ? "system_neutral1_900" : "system_neutral1_50",
                        dark ? Color.rgb(18, 18, 20) : Color.rgb(248, 248, 250));
                int surface = dynamicColor(context, dark ? "system_neutral1_800" : "system_neutral1_100",
                        dark ? Color.rgb(35, 35, 40) : Color.rgb(239, 239, 244));
                int accent = dynamicColor(context, dark ? "system_accent1_400" : "system_accent1_600",
                        DEFAULT_TIKTOK_ACCENT);
                int text = dynamicColor(context, dark ? "system_neutral1_50" : "system_neutral1_900",
                        dark ? Color.WHITE : Color.rgb(20, 20, 22));
                return new Palette(background, surface, accent, text);
            }
            case "material_you_amoled": {
                int accent = dynamicColor(context, dark ? "system_accent1_400" : "system_accent1_600",
                        DEFAULT_TIKTOK_ACCENT);
                int surface = dynamicColor(context, "system_neutral1_900", Color.rgb(15, 15, 18));
                return new Palette(Color.BLACK, surface, accent, Color.WHITE);
            }
            case "oled_black":
                return new Palette(Color.BLACK, Color.rgb(8, 8, 10), DEFAULT_TIKTOK_ACCENT, Color.WHITE);
            case "liquid_glass": {
                int accent = dynamicColor(context, dark ? "system_accent1_400" : "system_accent1_600",
                        DEFAULT_TIKTOK_ACCENT);
                int baseTint = parseColor(ThemeSettings.GLASS_TINT.get(), dark ? 0x9916161D : 0x99FFFFFF);
                int opacity = clamped(ThemeSettings.GLASS_OPACITY_PERCENT.get(), 10, 95);
                int surface = Color.argb(
                        Math.round(255f * opacity / 100f),
                        Color.red(baseTint),
                        Color.green(baseTint),
                        Color.blue(baseTint)
                );
                return new Palette(
                        dark ? Color.BLACK : Color.rgb(248, 248, 250),
                        surface,
                        accent,
                        dark ? Color.WHITE : Color.rgb(20, 20, 22)
                );
            }
            case "custom":
                return new Palette(
                        parseColor(ThemeSettings.CUSTOM_BACKGROUND.get(), Color.BLACK),
                        parseColor(ThemeSettings.CUSTOM_SURFACE.get(), Color.rgb(22, 22, 29)),
                        parseColor(ThemeSettings.CUSTOM_ACCENT.get(), DEFAULT_TIKTOK_ACCENT),
                        parseColor(ThemeSettings.CUSTOM_TEXT.get(), Color.WHITE)
                );
            case "default":
            default:
                return new Palette(
                        dark ? Color.BLACK : Color.WHITE,
                        dark ? Color.rgb(22, 22, 24) : Color.rgb(248, 248, 248),
                        DEFAULT_TIKTOK_ACCENT,
                        dark ? Color.WHITE : Color.BLACK
                );
        }
    }

    private static String normalizedPreset() {
        String preset = ThemeSettings.PRESET.get();
        if (preset == null) return "default";
        switch (preset) {
            case "material_you":
            case "material_you_amoled":
            case "oled_black":
            case "liquid_glass":
            case "custom":
                return preset;
            default:
                return "default";
        }
    }

    private static int dynamicColor(Context context, String name, int fallback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback;
        try {
            int id = context.getResources().getIdentifier(name, "color", "android");
            return id == 0 ? fallback : context.getColor(id);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String resourceEntryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID || id == 0) return "";
        try {
            return view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean isDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int clamped(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float dp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    private static double luminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int accent;
        final int text;

        Palette(int background, int surface, int accent, int text) {
            this.background = background;
            this.surface = surface;
            this.accent = accent;
            this.text = text;
        }
    }
}
