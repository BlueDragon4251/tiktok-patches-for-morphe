/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.featurecontrols;

import android.app.Activity;

import app.morphe.extension.tiktok.settings.Settings;

public final class FeatureControls {
    private static final int DEFAULT_LONG_PRESS_LOCK_DISTANCE_DP = 140;

    private FeatureControls() {
    }

    public static boolean shouldHideCaptchaPopup() {
        return Settings.HIDE_CAPTCHA_POPUPS.get();
    }

    public static boolean shouldHideCaptchaPopup(Activity activity) {
        if (!Settings.HIDE_CAPTCHA_POPUPS.get()) return false;
        if (activity == null) return true;

        // Account flows must be able to present server-required verification.
        return !activity.getClass().getName().startsWith(
                "com.ss.android.ugc.aweme.account."
        );
    }

    public static Object filterNormalPendant(Object pendant) {
        return Settings.HIDE_HOMEPAGE_COIN.get() ? null : pendant;
    }

    public static boolean overrideLongPressSpeedUpEnabled(boolean enabled) {
        return Settings.ENABLE_LONG_PRESS_SPEED_LOCK.get() || enabled;
    }

    public static int overrideLongPressSpeedUpLockDistance(int distanceDp) {
        if (!Settings.ENABLE_LONG_PRESS_SPEED_LOCK.get()) return distanceDp;
        return distanceDp > 0 ? distanceDp : DEFAULT_LONG_PRESS_LOCK_DISTANCE_DP;
    }
}
