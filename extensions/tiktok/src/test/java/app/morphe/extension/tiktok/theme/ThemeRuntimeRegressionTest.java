package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.shared.Utils;

import static org.junit.Assert.*;

/** Regressions reported on dev.19; exercises actual Android Views, not source-text matching. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, qualifiers = "w400dp-h800dp-mdpi-night")
@LooperMode(LooperMode.Mode.PAUSED)
public class ThemeRuntimeRegressionTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private ViewGroup decor;
    private FrameLayout host;
    private FrameLayout profile;
    private FrameLayout drawer;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        Utils.setContext(activity);
        ThemeStateStore.saveUserPreset(activity, "arctic_blue");
        host = new FrameLayout(activity);
        profile = new FrameLayout(activity);
        drawer = new FrameLayout(activity);
        host.addView(profile, new FrameLayout.LayoutParams(400, 700));
        host.addView(drawer, new FrameLayout.LayoutParams(320, 700));
        activity.setContentView(host);
        decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 800);
        host.layout(0, 0, 400, 700);
        profile.layout(0, 0, 400, 700);
        drawer.layout(400, 0, 720, 700);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void legacyPageTokenUsesBackgroundEvenInNightConfiguration() {
        // bx was logged as role=text on dev.19 despite being a background in the exact APK.
        assertEquals(Integer.valueOf(ThemeEngine.backgroundColor(activity)),
                ThemeColorResolver.resolve(0x7f06001c, activity, "default"));
        assertNotEquals(Integer.valueOf(ThemeEngine.textColor(activity)),
                ThemeColorResolver.resolve(0x7f06001c, activity, "default"));
        ThemeStateStore.saveUserPreset(activity, "default");
        assertNull(ThemeColorResolver.resolve(0x7f06001c, activity, "arctic_blue"));
    }

    @Test
    public void scrolledProfileIsFoundBelowDecorAndDrawerKeepsItsPosition() throws Exception {
        host.scrollTo(220, 0);
        assertEquals(-220, screenX(profile));
        int drawerX = screenX(drawer);
        Map<View, Object> corrections = new HashMap<>();
        assertTrue(compensate(corrections));
        assertEquals(0, screenX(profile));
        assertEquals(drawerX, screenX(drawer));

        // Continued opening and closing must follow the current host scroll, without drift.
        for (int scroll : new int[]{280, 140, 0}) {
            host.scrollTo(scroll, 0);
            restore(corrections);
            compensate(corrections);
            assertEquals(0, screenX(profile));
            assertEquals(400 - scroll, screenX(drawer));
        }
    }

    @Test
    public void directClosingAnimationDoesNotResurrectPreviousNegativeTranslation() throws Exception {
        Map<View, Object> corrections = new HashMap<>();
        for (int translation : new int[]{-160, -80, 0}) {
            profile.setTranslationX(translation);
            restore(corrections);
            assertEquals(translation, profile.getTranslationX(), 0f);
            compensate(corrections);
            assertEquals(0, screenX(profile));
        }
    }

    @Test
    public void nativeTranslationChangeSurvivesUndoOfAncestorCompensation() throws Exception {
        host.scrollTo(200, 0);
        Map<View, Object> corrections = new HashMap<>();
        assertTrue(compensate(corrections));
        profile.setTranslationX(17f);
        restore(corrections);
        assertEquals(17f, profile.getTranslationX(), 0f);
    }

    @Test
    public void inboxBindStylesBeforeMeasurementAndRebindDoesNotNeedVisibleTitle() throws Exception {
        FrameLayout row = new FrameLayout(activity);
        FrameLayout filler = new FrameLayout(activity);
        row.addView(filler);
        ThemeDynamicListGuardV3.onInboxRowBound(row);
        assertTrue(row.getBackground() instanceof GradientDrawable);

        host.addView(row);
        row.layout(0, 670, 400, 750); // Partially clipped at the bottom of the host.
        filler.layout(0, 0, 400, 80);
        row.setBackground(null); // A native recycler bind clears the holder's card.
        filler.setBackgroundColor(Color.WHITE);
        invoke(ThemeDynamicListGuardV3.class, "styleBoundInboxRows", new Class<?>[]{View.class}, decor);
        assertTrue(row.getBackground() instanceof GradientDrawable);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) filler.getBackground()).getColor());
    }

    @Test
    public void defaultPresetLeavesNativeInboxDrawableUntouched() {
        ThemeStateStore.saveUserPreset(activity, "default");
        FrameLayout row = new FrameLayout(activity);
        Drawable nativeBackground = new ColorDrawable(Color.MAGENTA);
        row.setBackground(nativeBackground);
        ThemeDynamicListGuardV3.onInboxRowBound(row);
        assertSame(nativeBackground, row.getBackground());
    }

    @Test
    public void secondaryActivityReceivesItsOwnGuards() throws Exception {
        ThemeEngine.onMainActivityCreated(activity);
        ActivityController<Activity> secondary = Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            View secondaryDecor = secondary.get().getWindow().getDecorView();
            for (Class<?> type : new Class<?>[]{ThemeRealtimeUiGuard.class,
                    ThemeDynamicListGuardV3.class, ThemeProfileOverlayGuardV3.class}) {
                Field field = type.getDeclaredField("GUARDS");
                field.setAccessible(true);
                assertTrue(type.getSimpleName(), ((Map<?, ?>) field.get(null)).containsKey(secondaryDecor));
            }
        } finally {
            secondary.pause().stop().destroy();
        }
    }

    @Test
    public void composePaletteStillMapsOnceAndRestoresExactNativeValues() {
        NativePalette palette = new NativePalette();
        long originalText = palette.text;
        long originalBackground = palette.background;
        long originalMedia = palette.media;
        assertSame(palette, ThemeComposeColorResolver.mapPalette(palette));
        assertEquals(Integer.toUnsignedLong(ThemeEngine.textColor(activity)) << 32, palette.text);
        assertEquals(Integer.toUnsignedLong(ThemeEngine.backgroundColor(activity)) << 32, palette.background);
        assertEquals(originalMedia, palette.media);
        long mappedText = palette.text;
        ThemeComposeColorResolver.mapPalette(palette);
        assertEquals(mappedText, palette.text);
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeComposeColorResolver.mapPalette(palette);
        assertEquals(originalText, palette.text);
        assertEquals(originalBackground, palette.background);
        assertEquals(originalMedia, palette.media);
    }

    public static final class NativePalette {
        public long text = 0xffffffff00000000L;
        public long background = 0xff00000000000000L;
        public long media = 0xff00ff0000000000L;
    }

    private boolean compensate(Map<View, Object> corrections) throws Exception {
        return (Boolean) invoke(ThemeProfileOverlayGuardV3.class, "compensate",
                new Class<?>[]{View.class, Map.class}, decor, corrections);
    }

    private void restore(Map<View, Object> corrections) throws Exception {
        invoke(ThemeProfileOverlayGuardV3.class, "restoreTracked", new Class<?>[]{Map.class}, corrections);
    }

    private static Object invoke(Class<?> type, String name, Class<?>[] parameters, Object... args)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static int screenX(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location[0];
    }
}
