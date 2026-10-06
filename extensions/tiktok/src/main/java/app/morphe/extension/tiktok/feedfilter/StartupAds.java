package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.settings.Settings;

public final class StartupAds {
    private StartupAds() {}
    public static boolean shouldBlock() { return Settings.REMOVE_ADS.get(); }
}
