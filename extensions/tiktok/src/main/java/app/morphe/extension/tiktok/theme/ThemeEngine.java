package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;

import app.morphe.extension.shared.Logger;

/** Runtime theme engine for TikTok. */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeEngine {
    private static final int DEFAULT_TIKTOK_ACCENT = Color.rgb(254, 44, 85);
    private static final int PATCH_DEFAULT_SCHEMA = 2;

    private static volatile boolean installed;
    private static volatile boolean applying;
    private static volatile boolean lifecycleCallbacksRegistered;
    private static volatile long lastLayoutApplyRequest;

    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static WeakReference<View> decorRef = new WeakReference<>(null);
    private static View.OnLayoutChangeListener layoutListener;
    private static Application.ActivityLifecycleCallbacks lifecycleCallbacks;

    private ThemeEngine() {}

    /**
     * Applies the patch-time preset exactly once to a fresh BlueIT settings data set.
     * Runtime selections always win after initialization.
     */
    public static void initializePatchDefault(String preset) {
        try {
            int appliedSchema = ThemeSettings.PATCH_DEFAULT_APPLIED.get();
            if (appliedSchema >= PATCH_DEFAULT_SCHEMA) return;

            String currentPreset = normalizedPreset();
            String patchPreset = normalizePresetValue(preset);
            if ("default".equals(currentPreset) && !"default".equals(patchPreset)) {
                ThemeSettings.PRESET.save(patchPreset);
                currentPreset = patchPreset;
            }

            ThemeSettings.PATCH_DEFAULT_APPLIED.save(PATCH_DEFAULT_SCHEMA);
            final String effectivePreset = currentPreset;
            Logger.printInfo(() -> "[BlueIT Theme Engine] initialized patch default: " + effectivePreset);
        } catch (Exception exception) {
            Logger.printDebug(() -> "BlueIT Theme Engine patch default initialization failed", exception);
        }
    }

    /** Called from the bytecode patch after TikTok MainActivity.onCreate completes. */
    public static void onMainActivityCreated(Activity activity) {
        if (activity == null) return;

        installed = true;
        registerActivityLifecycleCallbacks(activity.getApplication());
        attachActivity(activity);

        Logger.printInfo(() -> "[BlueIT Theme Engine] runtime installed, preset=" + normalizedPreset());
    }

    private static synchronized void registerActivityLifecycleCallbacks(Application application) {
        if (application == null || lifecycleCallbacksRegistered) return;

        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                scheduleApply(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
                scheduleApply(activity);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                attachActivity(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                Activity current = activityRef.get();
                if (current == activity) {
                    activityRef = new WeakReference<>(null);
                }

                View currentDecor = decorRef.get();
                if (currentDecor != null && activity.getWindow() != null
                        && currentDecor == activity.getWindow().getDecorView()) {
                    detachLayoutListener();
                }
            }
        };

        application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
        lifecycleCallbacksRegistered = true;
    }

    private static void attachActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        activityRef = new WeakReference<>(activity);

        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;

        View previousDecor = decorRef.get();
        if (previousDecor != decor) {
            detachLayoutListener();
            decorRef = new WeakReference<>(decor);

            if (layoutListener == null) {
                layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if ("default".equals(normalizedPreset())) return;

                    long now = SystemClock.uptimeMillis();
                    if (now - lastLayoutApplyRequest < 120L) return;
                    lastLayoutApplyRequest = now;

                    Activity current = activityRef.get();
                    if (current != null && !current.isFinishing()) {
                        v.postDelayed(() -> applyActivity(current), 55L);
                    }
                };
            }

            decor.addOnLayoutChangeListener(layoutListener);
        }

        scheduleApply(activity);
    }

    private static void detachLayoutListener() {
        View previousDecor = decorRef.get();
        if (previousDecor != null && layoutListener != null) {
            try {
                previousDecor.removeOnLayoutChangeListener(layoutListener);
            } catch (Throwable ignored) {
            }
        }
        decorRef = new WeakReference<>(null);
    }

    private static void scheduleApply(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        if (decor == null) return;

        decor.post(() -> applyActivity(activity));
        decor.postDelayed(() -> applyActivity(activity), 250L);
        decor.postDelayed(() -> applyActivity(activity), 750L);
        decor.postDelayed(() -> applyActivity(activity), 1600L);
        decor.postDelayed(() -> applyActivity(activity), 3200L);
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static boolean isDefaultPreset() {
        return "default".equals(normalizedPreset());
    }

    /** Live preview for BlueIT setting changes. */
    public static void requestReapply() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return;

        View decor = activity.getWindow().getDecorView();
        decor.post(() -> applyActivity(activity));
        decor.postDelayed(() -> applyActivity(activity), 180L);
        decor.postDelayed(() -> applyActivity(activity), 650L);
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

    public static int secondaryTextColor(Context context) {
        return resolvePalette(context).secondaryText;
    }

    public static int dividerColor(Context context) {
        return resolvePalette(context).divider;
    }

    public static boolean isDarkUi(Context context) {
        return luminance(opaque(resolvePalette(context).background)) < 0.48;
    }

    private static void applyActivity(Activity activity) {
        if (applying || activity == null || activity.isFinishing()) return;
        applying = true;

        try {
            String preset = normalizedPreset();
            if ("default".equals(preset)) return;

            Palette palette = resolvePalette(activity);
            Window window = activity.getWindow();
            if (window == null) return;

            int opaqueBackground = opaque(palette.background);
            int opaqueSurface = compositeOver(palette.surface, opaqueBackground);

            window.setStatusBarColor(opaqueBackground);
            window.setNavigationBarColor(opaqueSurface);

            View decor = window.getDecorView();
            updateSystemBarIconContrast(decor, opaqueBackground, opaqueSurface);

            ThemeSurfaceStyler.apply(
                    activity,
                    preset,
                    palette.background,
                    palette.surface,
                    palette.accent,
                    palette.text,
                    palette.secondaryText,
                    palette.divider,
                    palette.cornerRadiusDp
            );
        } catch (Exception exception) {
            Logger.printDebug(() -> "BlueIT Theme Engine apply failed", exception);
        } finally {
            applying = false;
        }
    }

    private static void updateSystemBarIconContrast(
            View decor,
            int background,
            int navigationSurface
    ) {
        int visibility = decor.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (luminance(background) > 0.58) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (luminance(navigationSurface) > 0.58) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }

        decor.setSystemUiVisibility(visibility);
    }

    private static Palette resolvePalette(Context context) {
        String preset = normalizedPreset();
        boolean dark = appDark(context);

        switch (preset) {
            case "material_you": {
                int background = dynamicColor(
                        context,
                        dark ? "system_neutral1_900" : "system_neutral1_50",
                        dark ? Color.rgb(18, 18, 20) : Color.rgb(248, 248, 250)
                );
                int surface = dynamicColor(
                        context,
                        dark ? "system_neutral1_800" : "system_neutral1_100",
                        dark ? Color.rgb(35, 35, 40) : Color.rgb(239, 239, 244)
                );
                int accent = dynamicColor(
                        context,
                        dark ? "system_accent1_400" : "system_accent1_600",
                        DEFAULT_TIKTOK_ACCENT
                );
                int preferredText = dark ? Color.WHITE : Color.rgb(20, 20, 22);
                return readablePalette(background, surface, accent, preferredText, 18);
            }

            case "material_you_amoled": {
                int accent = dynamicColor(
                        context,
                        dark ? "system_accent1_400" : "system_accent1_600",
                        DEFAULT_TIKTOK_ACCENT
                );
                int surface = dynamicColor(
                        context,
                        "system_neutral1_900",
                        Color.rgb(15, 15, 18)
                );
                return readablePalette(Color.BLACK, surface, accent, Color.WHITE, 18);
            }

            case "oled_black":
                return readablePalette(
                        Color.BLACK,
                        Color.rgb(8, 8, 10),
                        DEFAULT_TIKTOK_ACCENT,
                        Color.WHITE,
                        16
                );

            case "liquid_glass": {
                int accent = dynamicColor(
                        context,
                        "system_accent1_400",
                        DEFAULT_TIKTOK_ACCENT
                );
                int baseTint = parseColor(ThemeSettings.GLASS_TINT.get(), 0x9916161D);
                int opacity = clamped(ThemeSettings.GLASS_OPACITY_PERCENT.get(), 10, 95);
                int surface = Color.argb(
                        Math.round(255f * opacity / 100f),
                        Color.red(baseTint),
                        Color.green(baseTint),
                        Color.blue(baseTint)
                );

                boolean tintDark = luminance(opaque(baseTint)) < 0.52;
                int background = tintDark ? Color.rgb(5, 5, 8) : Color.rgb(246, 247, 250);
                int preferredText = tintDark ? Color.WHITE : Color.rgb(14, 14, 18);

                return readablePalette(
                        background,
                        surface,
                        accent,
                        preferredText,
                        clamped(ThemeSettings.GLASS_CORNER_RADIUS_DP.get(), 0, 48)
                );
            }

            case "frosted_graphite":
                return readablePalette(
                        0xFF090A0C,
                        0xD9292B31,
                        0xFFB8C0CF,
                        0xFFF7F8FA,
                        22
                );

            case "midnight_neon":
                return readablePalette(
                        0xFF03050B,
                        0xD90A1020,
                        0xFF00E5FF,
                        0xFFF4FBFF,
                        20
                );

            case "rose_noir":
                return readablePalette(
                        0xFF090407,
                        0xD9230C18,
                        0xFFFF4F91,
                        0xFFFFF5FA,
                        22
                );

            case "arctic_blue":
                return readablePalette(
                        0xFF06121A,
                        0xD90D2636,
                        0xFF6EDBFF,
                        0xFFF2FBFF,
                        20
                );

            case "aurora_violet":
                return readablePalette(
                        0xFF090714,
                        0xD91B1433,
                        0xFFA78BFA,
                        0xFFF9F7FF,
                        24
                );

            case "sunset_ember":
                return readablePalette(
                        0xFF120806,
                        0xD9321710,
                        0xFFFF7849,
                        0xFFFFF7F2,
                        22
                );

            case "custom":
                return readablePalette(
                        parseColor(ThemeSettings.CUSTOM_BACKGROUND.get(), Color.BLACK),
                        parseColor(ThemeSettings.CUSTOM_SURFACE.get(), Color.rgb(22, 22, 29)),
                        parseColor(ThemeSettings.CUSTOM_ACCENT.get(), DEFAULT_TIKTOK_ACCENT),
                        parseColor(ThemeSettings.CUSTOM_TEXT.get(), Color.WHITE),
                        20
                );

            case "default":
            default:
                return readablePalette(
                        dark ? Color.BLACK : Color.WHITE,
                        dark ? Color.rgb(22, 22, 24) : Color.rgb(248, 248, 248),
                        DEFAULT_TIKTOK_ACCENT,
                        dark ? Color.WHITE : Color.BLACK,
                        0
                );
        }
    }

    private static Palette readablePalette(
            int background,
            int surface,
            int accent,
            int preferredText,
            int cornerRadiusDp
    ) {
        int opaqueBackground = opaque(background);
        int effectiveSurface = compositeOver(surface, opaqueBackground);
        int text = ensureContrast(preferredText, effectiveSurface, 4.5);

        int secondaryCandidate = blend(text, effectiveSurface, 0.72f);
        int secondary = ensureContrast(secondaryCandidate, effectiveSurface, 3.0);
        int divider = blend(text, effectiveSurface, 0.20f);

        return new Palette(
                background,
                surface,
                accent,
                text,
                secondary,
                divider,
                cornerRadiusDp
        );
    }

    private static String normalizedPreset() {
        return normalizePresetValue(ThemeSettings.PRESET.get());
    }

    private static String normalizePresetValue(String preset) {
        if (preset == null) return "default";

        switch (preset) {
            case "material_you":
            case "material_you_amoled":
            case "oled_black":
            case "liquid_glass":
            case "frosted_graphite":
            case "midnight_neon":
            case "rose_noir":
            case "arctic_blue":
            case "aurora_violet":
            case "sunset_ember":
            case "custom":
                return preset;
            default:
                return "default";
        }
    }

    private static boolean appDark(Context context) {
        try {
            int night = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (night == Configuration.UI_MODE_NIGHT_YES) return true;
        } catch (Throwable ignored) {
        }

        try {
            return app.morphe.extension.shared.Utils.isDarkModeEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int dynamicColor(Context context, String name, int fallback) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback;

        try {
            int id = context.getResources().getIdentifier(name, "color", "android");
            return id == 0 ? fallback : context.getColor(id);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int ensureContrast(int candidate, int background, double minimumRatio) {
        int opaqueCandidate = opaque(candidate);
        if (contrastRatio(opaqueCandidate, background) >= minimumRatio) {
            return opaqueCandidate;
        }

        int black = Color.BLACK;
        int white = Color.WHITE;
        return contrastRatio(white, background) >= contrastRatio(black, background)
                ? white
                : black;
    }

    private static double contrastRatio(int foreground, int background) {
        double lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
        double darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int color) {
        double r = linearChannel(Color.red(color) / 255.0);
        double g = linearChannel(Color.green(color) / 255.0);
        double b = linearChannel(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearChannel(double value) {
        return value <= 0.03928
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int blend(int foreground, int background, float foregroundAmount) {
        float amount = Math.max(0f, Math.min(1f, foregroundAmount));
        float inverse = 1f - amount;

        return Color.rgb(
                Math.round(Color.red(foreground) * amount + Color.red(background) * inverse),
                Math.round(Color.green(foreground) * amount + Color.green(background) * inverse),
                Math.round(Color.blue(foreground) * amount + Color.blue(background) * inverse)
        );
    }

    private static int compositeOver(int foreground, int background) {
        int alpha = Color.alpha(foreground);
        if (alpha >= 255) return opaque(foreground);
        if (alpha <= 0) return opaque(background);

        float a = alpha / 255f;
        float inverse = 1f - a;

        return Color.rgb(
                Math.round(Color.red(foreground) * a + Color.red(background) * inverse),
                Math.round(Color.green(foreground) * a + Color.green(background) * inverse),
                Math.round(Color.blue(foreground) * a + Color.blue(background) * inverse)
        );
    }

    private static int opaque(int color) {
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int clamped(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double luminance(int color) {
        return 0.2126 * (Color.red(color) / 255.0)
                + 0.7152 * (Color.green(color) / 255.0)
                + 0.0722 * (Color.blue(color) / 255.0);
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int accent;
        final int text;
        final int secondaryText;
        final int divider;
        final int cornerRadiusDp;

        Palette(
                int background,
                int surface,
                int accent,
                int text,
                int secondaryText,
                int divider,
                int cornerRadiusDp
        ) {
            this.background = background;
            this.surface = surface;
            this.accent = accent;
            this.text = text;
            this.secondaryText = secondaryText;
            this.divider = divider;
            this.cornerRadiusDp = cornerRadiusDp;
        }
    }
}
