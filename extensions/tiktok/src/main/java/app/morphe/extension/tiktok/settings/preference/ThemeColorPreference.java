package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.graphics.Color;

import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.ColorPickerPreference;
import app.morphe.extension.tiktok.theme.ThemeEngine;

/** Programmatic BlueIT color picker backed by a StringSetting. */
@SuppressWarnings("deprecation")
public final class ThemeColorPreference extends ColorPickerPreference {
    private final StringSetting setting;

    public ThemeColorPreference(
            Context context,
            String title,
            String summary,
            StringSetting setting,
            boolean allowOpacity
    ) {
        super(context);
        this.setting = setting;
        setTitle(title);
        setSummary(summary);
        setKey(setting.key);
        setOpacitySliderEnabled(allowOpacity);
        setText(setting.get());
    }

    @Override
    public void setText(String colorString) {
        super.setText(colorString);
        if (setting != null) {
            setting.save(colorString);
            ThemeEngine.requestReapply();
        }
    }

    @Override
    protected void onDialogNeutralClicked() {
        try {
            int defaultColor = Color.parseColor(setting.defaultValue);
            dialogColorPickerView.setColor(defaultColor);
        } catch (Throwable ignored) {
            super.onDialogNeutralClicked();
        }
    }
}
