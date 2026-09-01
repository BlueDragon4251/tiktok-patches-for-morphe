package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.view.View;

import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.theme.ThemeEngine;
import app.morphe.extension.tiktok.theme.ThemeSettings;

@SuppressWarnings("deprecation")
public final class ThemePresetPreference extends ListPreference {
    public ThemePresetPreference(Context context) {
        super(context);
        setTitle("Theme preset");
        setEntries(new CharSequence[]{
                "TikTok default",
                "Material You",
                "Material You AMOLED",
                "OLED black",
                "Liquid Glass",
                "Custom"
        });
        setEntryValues(new CharSequence[]{
                "default",
                "material_you",
                "material_you_amoled",
                "oled_black",
                "liquid_glass",
                "custom"
        });
        setKey(ThemeSettings.PRESET.key);
        setValue(ThemeSettings.PRESET.get());
        updateSummary(ThemeSettings.PRESET.get());
        setOnPreferenceChangeListener((preference, newValue) -> {
            String value = String.valueOf(newValue);
            ThemeSettings.PRESET.save(value);
            updateSummary(value);
            ThemeEngine.requestReapply();
            return true;
        });
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
    }

    private void updateSummary(String value) {
        int index = findIndexOfValue(value);
        String selected = index >= 0 ? String.valueOf(getEntries()[index]) : "TikTok default";
        String extra;
        switch (value) {
            case "material_you":
                extra = "Uses Android 12+ wallpaper/system colors with a safe fallback on older Android versions.";
                break;
            case "material_you_amoled":
                extra = "Material You accents with a true black base for OLED displays.";
                break;
            case "oled_black":
                extra = "True black surfaces with the classic TikTok accent.";
                break;
            case "liquid_glass":
                extra = "Translucent rounded glass-like navigation and sheet surfaces. Blur-heavy video rendering is intentionally avoided.";
                break;
            case "custom":
                extra = "Uses the custom background, surface, accent, and text colors below.";
                break;
            default:
                extra = "Leaves TikTok's own colors and surfaces unchanged.";
                break;
        }
        setSummary(selected + ". " + extra);
    }
}
