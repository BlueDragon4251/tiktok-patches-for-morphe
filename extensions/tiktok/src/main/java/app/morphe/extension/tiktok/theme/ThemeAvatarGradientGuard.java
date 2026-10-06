package app.morphe.extension.tiktok.theme;

/** Validates native story-avatar shader data before TikTok caches it for drawing. */
public final class ThemeAvatarGradientGuard {
    private ThemeAvatarGradientGuard() { }

    public static boolean isUsable(int[] colors, float[] positions) {
        // The native consumer already falls back to its three-stop palette for a null config.
        // It only checks equal array lengths, which still permits a one-stop LinearGradient crash.
        if (colors == null || colors.length < 2) return false;
        if (positions == null) return true;
        if (positions.length != colors.length) return false;
        for (float position : positions) {
            if (Float.isNaN(position) || Float.isInfinite(position)) return false;
        }
        return true;
    }
}
