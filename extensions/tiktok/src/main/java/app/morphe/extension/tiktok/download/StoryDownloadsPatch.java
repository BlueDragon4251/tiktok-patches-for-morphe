/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.extension.tiktok.download;

import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("unused")
public final class StoryDownloadsPatch {
    private StoryDownloadsPatch() {
    }

    /**
     * Injection point. Share panel, other users' stories.
     */
    public static boolean shouldDownloadStories() {
        return Settings.DOWNLOAD_STORIES.get();
    }

    /**
     * Injection point. Long-press panel "save" entry.
     *
     * @return false to take the regular post branch instead of the owner-only story branch.
     */
    public static boolean overrideIsStory(boolean isStory) {
        return isStory && !Settings.DOWNLOAD_STORIES.get();
    }
}
