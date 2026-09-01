package app.morphe.extension.tiktok.theme;

import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.StringSetting;

/** Persistent settings for the BlueIT TikTok Theme Engine. */
public final class ThemeSettings {
    public static final StringSetting PRESET = new StringSetting(
            "blueit_theme_preset",
            "default"
    );

    public static final StringSetting CUSTOM_BACKGROUND = new StringSetting(
            "blueit_theme_custom_background",
            "#FF000000"
    );
    public static final StringSetting CUSTOM_SURFACE = new StringSetting(
            "blueit_theme_custom_surface",
            "#FF16161D"
    );
    public static final StringSetting CUSTOM_ACCENT = new StringSetting(
            "blueit_theme_custom_accent",
            "#FF2D55"
    );
    public static final StringSetting CUSTOM_TEXT = new StringSetting(
            "blueit_theme_custom_text",
            "#FFFFFFFF"
    );
    public static final StringSetting GLASS_TINT = new StringSetting(
            "blueit_theme_glass_tint",
            "#9916161D"
    );
    public static final IntegerSetting GLASS_CORNER_RADIUS_DP = new IntegerSetting(
            "blueit_theme_glass_corner_radius_dp",
            24,
            false
    );
    public static final IntegerSetting GLASS_OPACITY_PERCENT = new IntegerSetting(
            "blueit_theme_glass_opacity_percent",
            60,
            false
    );

    private ThemeSettings() {}
}
