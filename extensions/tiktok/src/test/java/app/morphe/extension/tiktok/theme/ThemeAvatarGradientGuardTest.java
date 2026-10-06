package app.morphe.extension.tiktok.theme;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ThemeAvatarGradientGuardTest {
    private LinearGradient nativeConsumer(int[] colors, float[] positions) {
        // Native 47.1.3: null config uses the original three-stop fallback and positions.
        if (!ThemeAvatarGradientGuard.isUsable(colors, positions)) {
            colors = new int[]{Color.RED, Color.BLUE, Color.GREEN};
            positions = new float[]{0f, .5f, 1f};
        }
        return new LinearGradient(0f, 0f, 40f, 40f, colors, positions, Shader.TileMode.CLAMP);
    }

    @Test public void aSingleRemainingResolvedColorCannotReachTheCrashingConstructor() {
        assertFalse(ThemeAvatarGradientGuard.isUsable(new int[]{Color.RED}, new float[]{0f}));
        assertFalse(ThemeAvatarGradientGuard.isUsable(new int[]{Color.RED}, new float[]{Float.NaN}));
        assertNotNull(nativeConsumer(new int[]{Color.RED}, new float[]{0f}));
        assertNotNull(nativeConsumer(new int[0], new float[0]));
        assertNotNull(nativeConsumer(null, null));
    }

    @Test public void validNativeGradientsKeepAllStopsAndPositions() {
        int[] colors = {Color.RED, Color.BLUE, Color.GREEN};
        float[] positions = {0f, .25f, 1f};
        assertTrue(ThemeAvatarGradientGuard.isUsable(colors, positions));
        assertNotNull(nativeConsumer(colors, positions));
        assertArrayEquals(new int[]{Color.RED, Color.BLUE, Color.GREEN}, colors);
        assertArrayEquals(new float[]{0f, .25f, 1f}, positions, 0f);
        assertTrue(ThemeAvatarGradientGuard.isUsable(colors, null));
    }

    @Test public void mismatchedOrNonFinitePositionsUseTheNativeFallback() {
        int[] colors = {Color.RED, Color.BLUE};
        assertFalse(ThemeAvatarGradientGuard.isUsable(colors, new float[]{0f}));
        assertFalse(ThemeAvatarGradientGuard.isUsable(colors, new float[]{0f, Float.POSITIVE_INFINITY}));
        assertNotNull(nativeConsumer(colors, new float[]{0f, Float.NaN}));
    }
}
