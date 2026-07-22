package app.morphe.extension.tiktok.featurecontrols;

import app.morphe.extension.tiktok.settings.Settings;

public final class FeatureControls {
    private FeatureControls() {
    }

    public static boolean shouldHideCaptchaPopup() {
        return Settings.HIDE_CAPTCHA_POPUPS.get();
    }

    public static Object filterNormalPendant(Object pendant) {
        return Settings.HIDE_HOMEPAGE_COIN.get() ? null : pendant;
    }
}
