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

/** Regressions reported on dev.19 and dev.20; exercises actual Android Views, not source-text matching. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, qualifiers = "w400dp-h800dp-night-mdpi")
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
    public void nativeMainPageFollowsDrawerScrollWithoutMovingAnUnrelatedView() {
        FrameLayout unrelated = new FrameLayout(activity);
        host.addView(unrelated);
        unrelated.layout(0, 0, 400, 700);
        ThemeNativeTargets.mainPage(profile);
        ThemeNativeTargets.sidebar(drawer);
        for (int scroll : new int[]{1, 23, 220, 280, 140, 0}) {
            host.scrollTo(scroll, 0);
            ThemeNativeTargets.mainPage(profile);
            assertEquals(0, screenX(profile));
            assertEquals(400 - scroll, screenX(drawer));
            assertEquals(0, unrelated.getTranslationX(), 0f);
            // A second observer pass must not briefly reset or accumulate translation.
            ThemeNativeTargets.mainPage(profile);
            assertEquals(0, screenX(profile));
        }
    }

    @Test
    public void nativeDirectClosingTranslationDoesNotResurrectAnOldOffset() {
        drawer.layout(80, 0, 400, 700);
        ThemeNativeTargets.sidebar(drawer);
        for (int translation : new int[]{-160, -80, 0}) {
            profile.setTranslationX(translation);
            ThemeNativeTargets.mainPage(profile);
            assertEquals(0, screenX(profile));
        }
        drawer.setVisibility(View.GONE);
        ThemeNativeTargets.mainPage(profile);
        assertEquals(0, screenX(profile));
    }

    @Test
    public void sidebarHasOneTranslucentFillAndDefaultRestoresNativeDrawables() {
        FrameLayout filler = new FrameLayout(activity);
        drawer.addView(filler);
        Drawable nativeRoot = new ColorDrawable(Color.BLACK);
        Drawable nativeChild = new ColorDrawable(0xff121212);
        drawer.setBackground(nativeRoot);
        filler.setBackground(nativeChild);
        ThemeNativeTargets.sidebar(drawer);
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) drawer.getBackground()).getColor());
        assertTrue(Color.alpha(((ColorDrawable) drawer.getBackground()).getColor()) < 255);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) filler.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.sidebar(drawer);
        assertSame(nativeRoot, drawer.getBackground());
        assertSame(nativeChild, filler.getBackground());
    }

    @Test
    public void nativeChatRootExcludesAllMessageRowsFromCardHeuristics() throws Exception {
        android.widget.TextView title = new android.widget.TextView(activity);
        title.setText("Einstellungen und Datenschutz");
        profile.addView(title);
        FrameLayout message = new FrameLayout(activity);
        profile.addView(message);
        message.layout(0, 100, 400, 190);
        Drawable nativeBubble = new ColorDrawable(Color.MAGENTA);
        message.setBackground(nativeBubble);
        ThemeNativeTargets.chat(profile);
        invoke(ThemeEngine.class, "applyActivity", new Class<?>[]{Activity.class}, activity);
        assertTrue(ThemeNativeTargets.hasChat(decor));
        assertSame(nativeBubble, message.getBackground());
    }

    @Test
    public void searchLateSuggestionFillMapsWhileMediaStaysUntouched() {
        ThemeNativeTargets.search(profile);
        FrameLayout suggestions = new FrameLayout(activity);
        suggestions.setBackgroundColor(0xff121212);
        profile.addView(suggestions);
        android.widget.ImageView media = new android.widget.ImageView(activity);
        Drawable image = new ColorDrawable(Color.BLACK);
        media.setBackground(image);
        suggestions.addView(media);
        ThemeNativeTargets.search(profile);
        assertEquals(ThemeEngine.backgroundColor(activity), ((ColorDrawable) suggestions.getBackground()).getColor());
        assertSame(image, media.getBackground());
    }

    @Test
    public void navigationNativeRepaintGetsThemeAlphaAndPreservesLatestDefault() {
        ThemeNativeTargets.navigation(drawer);
        Drawable repaint = new ColorDrawable(Color.BLACK);
        drawer.setBackground(repaint);
        ThemeNativeTargets.navigation(drawer);
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) drawer.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.navigation(drawer);
        assertSame(repaint, drawer.getBackground());
    }

    @Test
    public void navigationContainerDoesNotAddASecondOpaqueLayer() {
        FrameLayout background = new FrameLayout(activity);
        drawer.addView(background);
        drawer.setBackgroundColor(Color.BLACK);
        ThemeNativeTargets.navigationContainer(drawer);
        ThemeNativeTargets.navigation(background);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) drawer.getBackground()).getColor());
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) background.getBackground()).getColor());
        background.setBackgroundColor(Color.YELLOW); // Android reuses the same ColorDrawable.
        ThemeNativeTargets.navigation(background);
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.navigation(background);
        assertEquals(Color.YELLOW, ((ColorDrawable) background.getBackground()).getColor());
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
                    ThemeDynamicListGuardV3.class}) {
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
