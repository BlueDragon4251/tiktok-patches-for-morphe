package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;

/**
 * Legacy compatibility alias. The active implementation is BlueITCreditsPreference.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class MorpheTikTokAboutPreference extends BlueITCreditsPreference {
    public MorpheTikTokAboutPreference(Context context) {
        super(context);
    }
}
