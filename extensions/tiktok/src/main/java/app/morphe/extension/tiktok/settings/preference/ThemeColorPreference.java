package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Color;
import android.preference.EditTextPreference;
import android.text.InputFilter;
import android.text.InputType;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.tiktok.theme.ThemeEngine;

/**
 * BlueIT theme color editor backed by a StringSetting.
 *
 * This intentionally does not extend the shared ColorPickerPreference. TikTok patch bundles do not
 * contain the shared Morphe color-picker XML resources, and loading that class would therefore throw
 * Resources.NotFoundException before the Interface section can even open. A plain programmatic hex
 * editor keeps the Theme Engine self-contained and safe on every supported TikTok APK.
 */
@SuppressWarnings("deprecation")
public final class ThemeColorPreference extends EditTextPreference {
    private final StringSetting setting;
    private final boolean allowOpacity;

    public ThemeColorPreference(
            Context context,
            String title,
            String summary,
            StringSetting setting,
            boolean allowOpacity
    ) {
        super(context);
        this.setting = setting;
        this.allowOpacity = allowOpacity;

        setTitle(title);
        setSummary(summary + " Enter " + (allowOpacity ? "#AARRGGBB" : "#RRGGBB") + ".");
        setKey(setting.key);

        getEditText().setSingleLine(true);
        getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(allowOpacity ? 9 : 7)});
        super.setText(setting.get());

        setOnPreferenceChangeListener((preference, value) -> {
            String normalized = normalize(String.valueOf(value));
            if (normalized == null) {
                Utils.showToastShort("Invalid color. Use " + (allowOpacity ? "#AARRGGBB" : "#RRGGBB"));
                return false;
            }
            setting.save(normalized);
            super.setText(normalized);
            ThemeEngine.requestReapply();
            return false;
        });
    }

    private String normalize(String input) {
        if (input == null) return null;
        String value = input.trim().toUpperCase();
        if (!value.startsWith("#")) value = "#" + value;

        int expectedLength = allowOpacity ? 9 : 7;
        if (value.length() != expectedLength) return null;
        try {
            Color.parseColor(value);
            return value;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
