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
import android.widget.TextView;
import android.widget.ImageView;

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
    public void nativeSidebarAndProfileKeepTextReadableAndRestoreExactNativeColors() {
        FrameLayout header = new FrameLayout(activity);
        header.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(activity);
        title.setText("Profile title");
        title.setTextColor(Color.rgb(22, 24, 35));
        header.addView(title);
        profile.addView(header);
        TextView menu = new TextView(activity);
        menu.setText("Einstellungen und Datenschutz");
        menu.setTextColor(Color.BLACK);
        drawer.addView(menu);
        ImageView media = new ImageView(activity);
        media.setBackgroundColor(Color.WHITE);
        profile.addView(media);
        ThemeNativeTargets.profilePage(profile);
        ThemeNativeTargets.sidebar(drawer);
        assertEquals(ThemeEngine.backgroundColor(activity), ((ColorDrawable) header.getBackground()).getColor());
        assertEquals(ThemeEngine.textColor(activity), title.getCurrentTextColor());
        assertEquals(ThemeEngine.textColor(activity), menu.getCurrentTextColor());
        assertEquals(Color.WHITE, ((ColorDrawable) media.getBackground()).getColor());
        ThemeNativeTargets.sidebar(drawer); // Repeat pass does not classify already themed colors.
        assertEquals(ThemeEngine.textColor(activity), menu.getCurrentTextColor());
        menu.setTextColor(Color.RED); // An asynchronous native rebind retains functional colors.
        ThemeNativeTargets.sidebar(drawer);
        assertEquals(Color.RED, menu.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        ThemeNativeTargets.sidebar(drawer);
        assertEquals(Color.WHITE, ((ColorDrawable) header.getBackground()).getColor());
        assertEquals(Color.rgb(22, 24, 35), title.getCurrentTextColor());
        assertEquals(Color.RED, menu.getCurrentTextColor());
    }

    @Test
    public void unknownNeutralTuxTokensDoNotTurnTextIntoPageColors() throws Exception {
        for (int nativeColor : new int[]{Color.BLACK, Color.WHITE, 0xff161823, 0xe0ffffff, 0xff888888}) {
            assertEquals(0, invoke(ThemeColorResolver.class, "classifyStockColor",
                    new Class<?>[]{Integer.class, Context.class}, nativeColor, activity));
        }
    }

    @Test
    public void missingLegacyPageTokenKeepsNativeFallbackEvenInNightConfiguration() {
        // This Activity has Android resources, not TikTok's bx attribute. Night mode
        // must not manufacture a TUX value and conceal that missing native theme.
        assertFalse(activity.getTheme().resolveAttribute(0x7f06001c, new android.util.TypedValue(), true));
        assertNull(ThemeColorResolver.resolve(0x7f06001c, activity, "default"));
        ThemeStateStore.saveUserPreset(activity, "default");
        assertNull(ThemeColorResolver.resolve(0x7f06001c, activity, "arctic_blue"));
    }

    @Test
    public void nativePagerRepairsDarkCaptionsOnlyOnConfirmedDarkBackground() {
        host.setBackgroundColor(Color.BLACK);
        TextView caption = new TextView(activity);
        caption.setTextColor(Color.BLACK);
        host.addView(caption);
        TextView lightCard = new TextView(activity);
        lightCard.setTextColor(Color.BLACK);
        lightCard.setBackgroundColor(Color.WHITE);
        host.addView(lightCard);
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(ThemeEngine.textColor(activity), caption.getCurrentTextColor());
        assertEquals(Color.BLACK, lightCard.getCurrentTextColor());
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(ThemeEngine.textColor(activity), caption.getCurrentTextColor());
        host.setBackgroundColor(Color.WHITE); // Recycled native content changed its backdrop.
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(Color.BLACK, caption.getCurrentTextColor());
        host.setBackgroundColor(Color.BLACK);
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(ThemeEngine.textColor(activity), caption.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(Color.BLACK, caption.getCurrentTextColor());
    }

    @Test
    public void nativeProfileFollowsDrawerScrollWithoutMovingAnUnrelatedView() {
        FrameLayout unrelated = new FrameLayout(activity);
        host.addView(unrelated);
        unrelated.layout(0, 0, 400, 700);
        ThemeNativeTargets.profilePage(profile);
        ThemeNativeTargets.sidebar(drawer);
        for (int scroll : new int[]{1, 23, 220, 280, 140, 0}) {
            host.scrollTo(scroll, 0);
            ThemeNativeTargets.profilePage(profile);
            assertEquals(0, screenX(profile));
            assertEquals(400 - scroll, screenX(drawer));
            assertEquals(0, unrelated.getTranslationX(), 0f);
            // A second observer pass must not briefly reset or accumulate translation.
            ThemeNativeTargets.profilePage(profile);
            assertEquals(0, screenX(profile));
        }
    }

    @Test
    public void pagerDrawCorrectsScrollAdvancedAfterPreDrawIncludingClosingFrame() {
        FrameLayout unrelatedPager = new FrameLayout(activity);
        host.addView(unrelatedPager);
        unrelatedPager.layout(0, 0, 400, 700);
        ThemeNativeTargets.profilePage(profile);
        ThemeNativeTargets.sidebar(drawer);
        int previousScroll = 0;
        for (int scroll : new int[]{1, 23, 220, 280, 140, 0}) {
            decor.getViewTreeObserver().dispatchOnPreDraw();
            host.scrollTo(scroll, 0); // Native computeScroll runs after the observer pass.
            assertEquals(previousScroll - scroll, screenX(profile));
            ThemeNativeTargets.beforePagerDraw(unrelatedPager);
            assertEquals(previousScroll - scroll, screenX(profile));
            ThemeNativeTargets.beforePagerDraw(host);
            assertEquals(0, screenX(profile));
            assertEquals(400 - scroll, screenX(drawer));
            assertEquals(0f, unrelatedPager.getTranslationX(), 0f);
            ThemeNativeTargets.beforePagerDraw(host);
            assertEquals(0, screenX(profile));
            previousScroll = scroll;
        }
        assertTrue(host.getClipChildren());
        assertTrue(host.getClipToPadding());

        host.scrollTo(180, 0);
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(0, screenX(profile));
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(-180, screenX(profile));
        assertEquals(0f, profile.getTranslationX(), 0f);
        assertTrue(host.getClipChildren());
        assertTrue(host.getClipToPadding());
    }

    @Test
    public void nativeDirectClosingTranslationDoesNotResurrectAnOldOffset() {
        drawer.layout(80, 0, 400, 700);
        ThemeNativeTargets.sidebar(drawer);
        for (int translation : new int[]{-160, -80, 0}) {
            profile.setTranslationX(translation);
            ThemeNativeTargets.profilePage(profile);
            assertEquals(0, screenX(profile));
        }
        drawer.setVisibility(View.GONE);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(0, screenX(profile));
    }

    @Test
    public void nestedProfilePageStaysVisibleWithoutMovingItsHostOrDrawer() {
        host.removeView(profile);
        FrameLayout page = new FrameLayout(activity);
        host.addView(page, 0, new FrameLayout.LayoutParams(400, 700));
        page.addView(profile, new FrameLayout.LayoutParams(400, 700));
        page.layout(0, 0, 400, 700);
        profile.layout(0, 0, 400, 700);
        profile.setPadding(7, 11, 13, 17);
        Drawable original = new ColorDrawable(Color.MAGENTA);
        profile.setBackground(original);
        drawer.layout(80, 0, 400, 700);
        ThemeNativeTargets.sidebar(drawer);
        page.setTranslationX(-160);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(0, screenX(profile));
        assertEquals(-160f, page.getTranslationX(), 0f);
        assertEquals(80, screenX(drawer));
        assertFalse(page.getClipChildren());
        assertFalse(page.getClipToPadding());
        assertTrue(ThemeNativeTargets.isOwned(profile));
        assertSame(original, profile.getBackground());
        assertEquals(7, profile.getPaddingLeft());
        assertEquals(11, profile.getPaddingTop());
        drawer.setVisibility(View.GONE);
        page.setTranslationX(0);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(0, screenX(profile));
        assertTrue(page.getClipChildren());
        assertTrue(page.getClipToPadding());
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
    public void actualNavigationBarHasThemeFillAndSeparatorKeepsItsOwnRole() {
        View separator = new View(activity);
        separator.setBackgroundColor(Color.WHITE);
        host.addView(separator, new FrameLayout.LayoutParams(400, 1));
        drawer.setBackgroundColor(Color.BLACK);
        FrameLayout tabBackground = new FrameLayout(activity);
        tabBackground.setBackgroundColor(Color.BLACK);
        drawer.addView(tabBackground);
        ThemeNativeTargets.navigation(drawer);
        ThemeNativeTargets.navigationDivider(separator);
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) drawer.getBackground()).getColor());
        assertTrue(Color.alpha(((ColorDrawable) drawer.getBackground()).getColor()) < 255);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) tabBackground.getBackground()).getColor());
        assertEquals(ThemeEngine.dividerColor(activity), ((ColorDrawable) separator.getBackground()).getColor());
        drawer.setBackgroundColor(Color.BLACK); // TikTok repaints when switching back to FYP.
        ThemeNativeTargets.navigation(drawer);
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) drawer.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.navigation(drawer);
        ThemeNativeTargets.navigationDivider(separator);
        assertEquals(Color.BLACK, ((ColorDrawable) drawer.getBackground()).getColor());
        assertEquals(Color.BLACK, ((ColorDrawable) tabBackground.getBackground()).getColor());
        assertEquals(Color.WHITE, ((ColorDrawable) separator.getBackground()).getColor());
    }

    @Test
    public void inboxHeaderFillIsStyledAlongsideItsTextAndMediaStaysNative() throws Exception {
        FrameLayout header = new FrameLayout(activity);
        header.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(activity);
        title.setText("Posteingang");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        header.addView(title);
        host.addView(header);
        header.layout(0, 0, 400, 80);
        title.layout(0, 0, 240, 60);
        ImageView avatar = new ImageView(activity);
        avatar.setBackgroundColor(Color.WHITE);
        header.addView(avatar);
        ImageView action = new ImageView(activity);
        com.bytedance.tux.drawable.TuxIconDrawable icon = new com.bytedance.tux.drawable.TuxIconDrawable();
        icon.colors = android.content.res.ColorStateList.valueOf(Color.BLACK);
        action.setImageDrawable(icon); action.setClickable(true); header.addView(action);
        Object screen = invoke(ThemeDynamicListGuardV3.class, "detectScreen", new Class<?>[]{View.class}, decor);
        invoke(ThemeDynamicListGuardV3.class, "style", new Class<?>[]{View.class, screen.getClass()}, decor, screen);
        assertEquals(ThemeEngine.backgroundColor(activity), ((ColorDrawable) header.getBackground()).getColor());
        assertEquals(ThemeEngine.textColor(activity), title.getCurrentTextColor());
        assertEquals(Color.WHITE, ((ColorDrawable) avatar.getBackground()).getColor());
        assertEquals(ThemeEngine.textColor(activity), action.getImageTintList().getDefaultColor());
        assertEquals(ThemeEngine.textColor(activity), icon.colors.getDefaultColor());
    }

    @Test
    public void listBannerMapsWrappedWhiteFillPreservesRadiiAndRestoresDefault() throws Exception {
        FrameLayout banner = new FrameLayout(activity); profile.addView(banner);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.WHITE); shape.setCornerRadius(18);
        android.graphics.drawable.InsetDrawable original = new android.graphics.drawable.InsetDrawable(shape, 3);
        banner.setBackground(original);
        invoke(ThemeDynamicListGuardV3.class, "styleListFill", new Class<?>[]{View.class, int.class},
                banner, ThemeEngine.backgroundColor(activity));
        assertEquals(Integer.valueOf(ThemeEngine.backgroundColor(activity)), ThemeViewColors.flatColor(banner.getBackground()));
        assertEquals(18f, ((GradientDrawable) ((android.graphics.drawable.InsetDrawable)
                banner.getBackground()).getDrawable()).getCornerRadius(), 0.01f);
        assertEquals(Color.WHITE, shape.getColor().getDefaultColor());
        invoke(ThemeDynamicListGuardV3.class, "restoreListFills", new Class<?>[]{View.class}, decor);
        assertSame(original, banner.getBackground());
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
            for (Class<?> type : new Class<?>[]{ThemeDynamicListGuardV3.class}) {
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

    @Test
    public void lightTikTokPaletteUsesArcticBackgroundEvenWhenAndroidIsInNightMode() {
        NativePalette palette = new NativePalette();
        palette.background = 0xffffffff00000000L;
        palette.text = 0xff16182300000000L;
        ThemeComposeColorResolver.mapPalette(palette, "background");
        assertEquals(Integer.toUnsignedLong(ThemeEngine.backgroundColor(activity)) << 32, palette.background);
        assertEquals(Integer.toUnsignedLong(ThemeEngine.textColor(activity)) << 32, palette.text);
        ThemeComposeColorResolver.mapPalette(palette, "background");
        assertEquals(Integer.toUnsignedLong(ThemeEngine.backgroundColor(activity)) << 32, palette.background);
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeComposeColorResolver.mapPalette(palette, "background");
        assertEquals(0xffffffff00000000L, palette.background);
        assertEquals(0xff16182300000000L, palette.text);
    }

    @Test
    public void profileControlsBecomeVisibleWithoutTintingPhotosOrFunctionalIcons() {
        ImageView menu = new ImageView(activity);
        menu.setClickable(true);
        menu.setImageDrawable(new ColorDrawable(Color.BLACK));
        profile.addView(menu);
        ImageView photo = new ImageView(activity);
        photo.setClickable(true);
        photo.setImageBitmap(android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888));
        profile.addView(photo);
        ImageView badge = new ImageView(activity);
        badge.setClickable(true);
        badge.setImageDrawable(new ColorDrawable(Color.RED));
        profile.addView(badge);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), menu.getImageTintList().getDefaultColor());
        assertNull(photo.getImageTintList());
        assertNull(badge.getImageTintList());
        assertTrue(menu.isClickable());
        assertEquals(View.VISIBLE, menu.getVisibility());
        assertEquals(1f, menu.getAlpha(), 0f);
        menu.setImageDrawable(new ColorDrawable(Color.MAGENTA)); // Async native functional rebind.
        menu.setImageTintList(null);
        ThemeNativeTargets.profilePage(profile);
        assertNull(menu.getImageTintList());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertNull(menu.getImageTintList());
    }

    @Test
    public void profileRoundedWhiteSurfaceKeepsGeometryAndTransparentOverlaysStayTransparent() {
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(Color.WHITE);
        bubble.setCornerRadius(12);
        TextView label = new TextView(activity);
        label.setText("Erzähl doch mal");
        label.setTextColor(Color.BLACK);
        label.setBackground(bubble);
        profile.addView(label);
        View overlay = new View(activity);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        profile.addView(overlay);
        ThemeNativeTargets.profilePage(profile);
        GradientDrawable themed = (GradientDrawable) label.getBackground();
        assertEquals(ThemeEngine.backgroundColor(activity), themed.getColor().getDefaultColor());
        assertEquals(12f, themed.getCornerRadius(), 0f);
        assertEquals(ThemeEngine.textColor(activity), label.getCurrentTextColor());
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) overlay.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertSame(bubble, label.getBackground());
        assertEquals(Color.BLACK, label.getCurrentTextColor());
    }

    @Test
    public void captionSpansOnDarkGradientScrimAreReadableAndRestoreAfterNativeRebind() {
        host.setBackgroundColor(Color.BLACK);
        FrameLayout captionArea = new FrameLayout(activity);
        captionArea.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, 0xcc000000}));
        host.addView(captionArea);
        TextView caption = new TextView(activity);
        android.text.SpannableString text = new android.text.SpannableString("Caption #tag");
        text.setSpan(new android.text.style.ForegroundColorSpan(Color.BLACK), 0, 7, 33);
        text.setSpan(new android.text.style.ForegroundColorSpan(Color.RED), 8, 12, 33);
        caption.setText(text);
        caption.setTextColor(Color.BLACK);
        captionArea.addView(caption);
        ThemeNativeTargets.beforePagerDraw(host);
        android.text.Spanned mapped = (android.text.Spanned) caption.getText();
        assertEquals(ThemeEngine.textColor(activity), mapped.getSpans(0, 7,
                android.text.style.ForegroundColorSpan.class)[0].getForegroundColor());
        assertEquals(Color.RED, mapped.getSpans(8, 12,
                android.text.style.ForegroundColorSpan.class)[0].getForegroundColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.beforePagerDraw(host);
        assertEquals(Color.BLACK, ((android.text.Spanned) caption.getText()).getSpans(0, 7,
                android.text.style.ForegroundColorSpan.class)[0].getForegroundColor());
    }

    @Test
    public void repeatedInboxBindsKeepOneDrawableAndNativeRepaintIsStillRepaired() {
        FrameLayout row = new FrameLayout(activity);
        ThemeDynamicListGuardV3.onInboxRowBound(row);
        Drawable first = row.getBackground();
        for (int i = 0; i < 120; i++) ThemeDynamicListGuardV3.onInboxRowBound(row);
        assertSame(first, row.getBackground());
        row.setBackgroundColor(Color.WHITE);
        ThemeDynamicListGuardV3.onInboxRowBound(row);
        assertNotSame(first, row.getBackground());
        assertEquals(ThemeEngine.surfaceColor(activity), ((GradientDrawable) row.getBackground()).getColor().getDefaultColor());
    }

    @Test
    public void inboxIdleFramesDoNotWalkTheWholeWindowOrRecreateCards() throws Exception {
        TextView title = new TextView(activity);
        title.setText("Posteingang");
        title.setTextSize(20);
        host.addView(title);
        title.layout(0, 0, 200, 50);
        ThemeDynamicListGuardV3.install(activity);
        Field guards = ThemeDynamicListGuardV3.class.getDeclaredField("GUARDS");
        guards.setAccessible(true);
        Object guard = ((Map<?, ?>) guards.get(null)).get(decor);
        Method preDraw = guard.getClass().getMethod("onPreDraw");
        preDraw.setAccessible(true);
        preDraw.invoke(guard);
        Field lastStyle = guard.getClass().getDeclaredField("lastStyleMs");
        lastStyle.setAccessible(true);
        long first = lastStyle.getLong(guard);
        for (int i = 0; i < 120; i++) preDraw.invoke(guard);
        assertEquals(first, lastStyle.getLong(guard));
        // A preset change is applied before drawing even inside the throttle interval.
        ThemeStateStore.saveUserPreset(activity, "rose_noir");
        preDraw.invoke(guard);
        assertEquals(ThemeEngine.textColor(activity), title.getCurrentTextColor());
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void nativeIconDrawUsesThemeTintAndDefaultPreservesAFunctionalRebind() {
        profile.setBackgroundColor(Color.WHITE); // Native black icons already have a readable Default backdrop.
        NativeTintImage icon = new NativeTintImage(activity);
        NativeTintDrawable drawable = new NativeTintDrawable();
        icon.setImageDrawable(drawable);
        profile.addView(icon);
        ThemeNativeTargets.profilePage(profile);
        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 24, 24);
        drawable.draw(new android.graphics.Canvas(pixels));
        assertEquals(ThemeEngine.textColor(activity), pixels.getPixel(12, 12));
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        drawable.draw(new android.graphics.Canvas(pixels));
        assertEquals(Color.BLACK, pixels.getPixel(12, 12));
        ThemeStateStore.saveUserPreset(activity, "arctic_blue");
        ThemeNativeTargets.profilePage(profile);
        icon.setTintColorStateList$tux_theme_release(android.content.res.ColorStateList.valueOf(Color.RED));
        ThemeNativeTargets.profilePage(profile);
        drawable.draw(new android.graphics.Canvas(pixels));
        assertEquals(Color.RED, pixels.getPixel(12, 12));
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        drawable.draw(new android.graphics.Canvas(pixels));
        assertEquals(Color.RED, pixels.getPixel(12, 12));
        pixels.recycle();
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void nativeControlStatesKeepSelectionAndDisabledAlphaWhileNeutralLabelsStayReadable() {
        profile.setBackgroundColor(Color.WHITE); // Native black icons already have a readable Default backdrop.
        android.content.res.ColorStateList nativeStates = new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{-android.R.attr.state_enabled}, new int[0]},
                new int[]{Color.RED, 0x66000000, Color.BLACK});
        NativeTintImage icon = new NativeTintImage(activity);
        NativeTintDrawable drawable = new NativeTintDrawable();
        icon.setImageDrawable(drawable);
        icon.setTintColorStateList$tux_theme_release(nativeStates);
        profile.addView(icon);
        TextView label = new TextView(activity);
        label.setTextColor(nativeStates);
        profile.addView(label);
        ThemeNativeTargets.profilePage(profile);
        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 24, 24);
        drawable.setState(new int[]{android.R.attr.state_enabled});
        drawable.draw(new android.graphics.Canvas(pixels));
        assertEquals(ThemeEngine.textColor(activity), pixels.getPixel(12, 12));
        assertEquals(ThemeEngine.textColor(activity), label.getCurrentTextColor());
        assertTrue(label.getTextColors().isStateful());
        drawable.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_selected});
        pixels.eraseColor(Color.TRANSPARENT);
        drawable.draw(new android.graphics.Canvas(pixels));
        label.setSelected(true);
        assertEquals(Color.RED, pixels.getPixel(12, 12));
        assertEquals(Color.RED, label.getCurrentTextColor());
        label.setSelected(false);
        label.setEnabled(false);
        assertEquals(0x66, Color.alpha(label.getCurrentTextColor()));
        assertEquals(ThemeEngine.textColor(activity) & 0xffffff, label.getCurrentTextColor() & 0xffffff);
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertSame(nativeStates, label.getTextColors());
        assertSame(nativeStates, drawable.colors);
        pixels.recycle();
    }

    /** Models the verified TuxIconDrawable draw-time color writer, beyond ImageView tint. */
    public static final class NativeTintDrawable extends Drawable {
        private android.content.res.ColorStateList colors = android.content.res.ColorStateList.valueOf(Color.BLACK);
        @Override public void draw(android.graphics.Canvas canvas) { canvas.drawColor(colors == null ? Color.BLACK : colors.getColorForState(getState(), colors.getDefaultColor())); }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }
    public static final class NativeTintImage extends ImageView {
        public NativeTintImage(Context context) { super(context); }
        public void setTintColorStateList$tux_theme_release(android.content.res.ColorStateList colors) {
            ((NativeTintDrawable) getDrawable()).colors = colors;
        }
    }

    public static final class LayerTintImage extends ImageView {
        final NativeTintDrawable cachedIcon = new NativeTintDrawable();
        public LayerTintImage(Context context) { super(context); }
        public void setTintColorStateList$tux_theme_release(android.content.res.ColorStateList colors) {
            cachedIcon.colors = colors;
        }
    }

    @Test
    public void nativeLayerIconTintsTheDisplayedChildAndRestoresItsOwnOriginalColors() {
        FrameLayout header = new FrameLayout(activity); profile.addView(header);
        LayerTintImage view = new LayerTintImage(activity); header.addView(view);
        NativeTintDrawable actual = new NativeTintDrawable();
        android.content.res.ColorStateList original = actual.colors;
        view.setImageDrawable(new android.graphics.drawable.LayerDrawable(new Drawable[]{actual}));
        ThemeNativeTargets.header(header);
        assertEquals(ThemeEngine.textColor(activity), actual.colors.getDefaultColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.header(header);
        assertSame(original, actual.colors);
    }

    @Test
    public void thoughtBubbleNativePaintFillIsThemedWithoutChangingLayoutAndRestoresDefault() {
        com.ss.android.ugc.aweme.social.thought.view.SocialThoughtBaseBubbleBackgroundView bubble =
                new com.ss.android.ugc.aweme.social.thought.view.SocialThoughtBaseBubbleBackgroundView(activity);
        profile.addView(bubble);
        bubble.layout(12, 20, 140, 65);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.backgroundColor(activity), bubble.getFillColor());
        assertEquals(12, bubble.getLeft());
        assertEquals(128, bubble.getWidth());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.WHITE, bubble.getFillColor());
        ThemeStateStore.saveUserPreset(activity, "arctic_blue");
        ThemeNativeTargets.profilePage(profile);
        bubble.setFillColor(Color.RED);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.RED, bubble.getFillColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.RED, bubble.getFillColor());
    }

    @Test
    public void asynchronousDescriptionDiscoveryDoesNotRewriteNativeTextOrColors() throws Exception {
        TextView caption = new TextView(activity);
        android.text.SpannableString text = new android.text.SpannableString("Native description");
        text.setSpan(new android.text.style.ForegroundColorSpan(Color.BLACK), 0, text.length(), 33);
        caption.setText(text);
        caption.setTextColor(Color.BLACK);
        host.addView(caption);
        CharSequence nativeText = caption.getText();
        Thread bind = new Thread(() -> { for (int i = 0; i < 100; i++) ThemeNativeTargets.feedDescription(caption); });
        bind.start(); bind.join();
        assertSame(nativeText, caption.getText());
        assertEquals(Color.BLACK, caption.getCurrentTextColor());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertSame(nativeText, caption.getText());
        assertEquals(Color.BLACK, caption.getCurrentTextColor());
        assertEquals(1, ((android.text.Spanned) caption.getText()).getSpans(0, text.length(),
                android.text.style.ForegroundColorSpan.class).length);
    }

    @Test
    public void backgroundInboxBindDefersAllViewWritesToMainThread() throws Exception {
        FrameLayout row = new FrameLayout(activity);
        row.setBackgroundColor(Color.WHITE);
        TextView label = new TextView(activity);
        label.setTextColor(Color.BLACK);
        row.addView(label);
        host.addView(row);
        Thread bind = new Thread(() -> ThemeDynamicListGuardV3.onInboxRowBound(row));
        bind.start(); bind.join();
        assertEquals(Color.WHITE, ((ColorDrawable) row.getBackground()).getColor());
        assertEquals(Color.BLACK, label.getCurrentTextColor());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(ThemeEngine.textColor(activity), label.getCurrentTextColor());
        assertTrue(row.getBackground() instanceof GradientDrawable);
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void visibleCaptionLayoutDrawsNeutralGlyphsAndRetainsNativeTextGeometryAndSpans() {
        android.text.SpannableString text = new android.text.SpannableString("Visible video description");
        android.text.style.TextAppearanceSpan appearance = new android.text.style.TextAppearanceSpan(null, 0, 28,
                android.content.res.ColorStateList.valueOf(Color.BLACK), null);
        text.setSpan(appearance, 0, text.length(), 33);
        android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(28); paint.setColor(Color.BLACK);
        android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), paint, 350).build();
        View nativeView = new View(activity);
        int height = layout.getHeight();
        ThemeCaptionRenderer.beforeDraw(nativeView, layout);
        assertSame(text, layout.getText());
        assertEquals(height, layout.getHeight());
        assertEquals(350, layout.getWidth());
        assertEquals(Color.WHITE, paint.getColor());
        assertEquals(0, text.getSpanStart(appearance));
        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(350, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(pixels);
        canvas.drawColor(Color.RED); layout.draw(canvas);
        int glyphs = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < 350; x++) {
            int pixel = pixels.getPixel(x, y);
            if (Color.green(pixel) > 200 && Color.blue(pixel) > 200) glyphs++;
        }
        assertTrue("Native Layout must draw visible light glyphs over red media", glyphs > 50);
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeCaptionRenderer.beforeDraw(nativeView, layout);
        assertEquals(Color.WHITE, paint.getColor());
        assertEquals(0, text.getSpanStart(appearance));
        pixels.eraseColor(Color.RED); layout.draw(canvas);
        int defaultGlyphs = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < 350; x++) {
            int pixel = pixels.getPixel(x, y);
            if (Color.green(pixel) > 200 && Color.blue(pixel) > 200) defaultGlyphs++;
        }
        assertTrue("Caption repair must remain visible with TikTok Default", defaultGlyphs > 50);
        pixels.recycle();
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void modelDispatchedIconWithNoNativeStateTintStillDrawsAndRestoresItsNativeColor() {
        profile.setBackgroundColor(Color.WHITE); // Native black icons already have a readable Default backdrop.
        NativeTintImage icon = new NativeTintImage(activity);
        NativeTintDrawable drawable = new NativeTintDrawable();
        drawable.colors = null;
        icon.setImageDrawable(drawable);
        profile.addView(icon);
        assertFalse(icon.isClickable());
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), drawable.colors.getDefaultColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertNull(drawable.colors);
    }

    @Test
    public void nativeNotePathPainterAndHintAreThemedWhileGradientAndStickerRebindsStayNative() {
        NoteView note = new NoteView(activity);
        note.setHintTextColor(0x77000000);
        profile.addView(note);
        int nativeHint = note.getCurrentHintTextColor();
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.backgroundColor(activity), note.painter.fill.getColor());
        assertEquals(ThemeEngine.secondaryTextColor(activity), note.getCurrentHintTextColor());
        assertEquals(0x77, Color.alpha(note.painter.shadow.getColor()));
        assertEquals(Color.RED, note.painter.accent.getColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.WHITE, note.painter.fill.getColor());
        assertEquals(nativeHint, note.getCurrentHintTextColor());
        ThemeStateStore.saveUserPreset(activity, "arctic_blue");
        note.gradient = new Object();
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.WHITE, note.painter.fill.getColor());
        note.gradient = null; note.sticker = true;
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.WHITE, note.painter.fill.getColor());
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void transparentNativeCaptionAndSpanDrawVisibleGlyphsInDefault() {
        ThemeStateStore.saveUserPreset(activity, "default");
        android.text.SpannableString text = new android.text.SpannableString("Transparent native caption");
        text.setSpan(new android.text.style.ForegroundColorSpan(Color.TRANSPARENT), 0, text.length(), 33);
        android.text.TextPaint paint = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(28); paint.setColor(Color.TRANSPARENT);
        android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length(), paint, 350).build();
        ThemeCaptionRenderer.beforeDraw(new View(activity), layout);
        assertEquals(Color.WHITE, paint.getColor());
        assertSame(text, layout.getText());
        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(350, layout.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(pixels); canvas.drawColor(Color.RED); layout.draw(canvas);
        int glyphs = 0;
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < 350; x++)
            if (Color.green(pixels.getPixel(x, y)) > 200) glyphs++;
        assertTrue("Transparent native description must render visible glyphs", glyphs > 50);
        pixels.recycle();
    }

    @Test
    public void defaultRepairsForegroundContrastAgainstNativeFlatBackdropsWithoutThemingTheirFills() {
        ThemeStateStore.saveUserPreset(activity, "default");
        profile.setBackgroundColor(Color.WHITE);
        TextView whiteOnWhite = new TextView(activity); whiteOnWhite.setTextColor(Color.WHITE);
        profile.addView(whiteOnWhite);
        FrameLayout header = new FrameLayout(activity); header.setBackgroundColor(Color.BLACK); profile.addView(header);
        TextView darkOnDark = new TextView(activity); darkOnDark.setTextColor(Color.BLACK); header.addView(darkOnDark);
        TextView functional = new TextView(activity); functional.setTextColor(Color.RED); header.addView(functional);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.BLACK, whiteOnWhite.getCurrentTextColor());
        assertEquals(Color.WHITE, darkOnDark.getCurrentTextColor());
        assertEquals(Color.RED, functional.getCurrentTextColor());
        assertEquals(Color.WHITE, ((ColorDrawable) profile.getBackground()).getColor());
        assertEquals(Color.BLACK, ((ColorDrawable) header.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "arctic_blue"); ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), whiteOnWhite.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.BLACK, whiteOnWhite.getCurrentTextColor());
    }

    @Test
    public void tabSwitchRepairsSystemBarsWithoutRestylingTheActivityTreeAndPreservesDefault() {
        ThemeEngine.onMainActivityCreated(activity);
        activity.getWindow().setNavigationBarColor(Color.WHITE);
        ThemeNativeTargets.profilePage(profile);
        assertNotEquals(Color.WHITE, activity.getWindow().getNavigationBarColor());
        activity.getWindow().setNavigationBarColor(Color.WHITE);
        profile.getViewTreeObserver().dispatchOnPreDraw();
        assertNotEquals(Color.WHITE, activity.getWindow().getNavigationBarColor());
        ThemeStateStore.saveUserPreset(activity, "default");
        activity.getWindow().setNavigationBarColor(Color.WHITE);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.WHITE, activity.getWindow().getNavigationBarColor());
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void emptyAsyncNativeIconSampleCanBeResolvedAfterItsResourceArrives() {
        Drawable icon = new Drawable() {
            boolean loaded;
            @Override public void draw(android.graphics.Canvas canvas) { if (loaded) canvas.drawColor(Color.BLACK); loaded = true; }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        assertFalse(ThemeViewColors.monochrome(icon));
        assertTrue(ThemeViewColors.monochrome(icon));
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void textCompoundTuxIconDrawsReadableGlyphAndRestoresNullNativeTint() {
        TextView label = new TextView(activity); label.setText("Add bio");
        com.bytedance.tux.drawable.TuxIconDrawable plus = new com.bytedance.tux.drawable.TuxIconDrawable();
        plus.setBounds(0, 0, 24, 24);
        label.setCompoundDrawablesRelative(plus, null, null, null); profile.addView(label);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), plus.colors.getDefaultColor());
        assertEquals(24, plus.getBounds().width());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertNull(plus.colors);
    }

    @Test
    public void nativeHeaderRepairsALateWhiteChildBeforeItsFirstFrame() {
        FrameLayout header = new FrameLayout(activity); profile.addView(header); header.layout(0, 0, 400, 90);
        header.setBackgroundColor(Color.WHITE); ThemeNativeTargets.header(header);
        FrameLayout nativeFill = new FrameLayout(activity); nativeFill.setBackgroundColor(0xfff5f5f5);
        TextView title = new TextView(activity); title.setText("Inbox"); title.setTextColor(Color.BLACK);
        nativeFill.addView(title); header.addView(nativeFill); nativeFill.layout(0, 0, 400, 90);
        // A late bind inside the ordinary 100ms throttle must still get its first frame themed.
        header.getViewTreeObserver().dispatchOnGlobalLayout(); header.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(ThemeEngine.backgroundColor(activity), ((ColorDrawable) nativeFill.getBackground()).getColor());
        assertEquals(ThemeEngine.textColor(activity), title.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.header(header);
        assertEquals(0xfff5f5f5, ((ColorDrawable) nativeFill.getBackground()).getColor());
        assertEquals(Color.BLACK, title.getCurrentTextColor());
    }

    @Test
    public void systemNotificationCardsKeepCornersAndFunctionalPressedColorsAndRestoreNativeFill() {
        FrameLayout notifications = new FrameLayout(activity); profile.addView(notifications);
        FrameLayout card = new FrameLayout(activity);
        GradientDrawable nativeFill = new GradientDrawable(); nativeFill.setCornerRadius(20);
        android.content.res.ColorStateList states = new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[0]}, new int[]{Color.RED, Color.WHITE});
        nativeFill.setColor(states); card.setBackground(nativeFill); notifications.addView(card);
        TextView title = new TextView(activity); title.setTextColor(Color.BLACK); card.addView(title);
        ImageView media = new ImageView(activity); media.setBackgroundColor(Color.WHITE); card.addView(media);
        ThemeNativeTargets.systemNotifications(notifications);
        GradientDrawable themed = (GradientDrawable) card.getBackground();
        assertEquals(20f, themed.getCornerRadius(), 0.01f);
        assertEquals(ThemeEngine.surfaceColor(activity), themed.getColor().getDefaultColor());
        assertEquals(Color.RED, themed.getColor().getColorForState(new int[]{android.R.attr.state_pressed}, Color.RED));
        assertEquals(ThemeEngine.textColor(activity), title.getCurrentTextColor());
        assertEquals(Color.WHITE, ((ColorDrawable) media.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.systemNotifications(notifications);
        assertSame(nativeFill, card.getBackground()); assertSame(states, nativeFill.getColor());
    }

    @Test
    public void nativeSheetKeepsDismissBackdropTransparentAndThemesItsRoundedContent() {
        FrameLayout sheet = new FrameLayout(activity); profile.addView(sheet);
        FrameLayout content = new FrameLayout(activity); GradientDrawable fill = new GradientDrawable();
        fill.setColor(Color.WHITE); fill.setCornerRadius(24); content.setBackground(fill); sheet.addView(content);
        TextView option = new TextView(activity); option.setTextColor(Color.BLACK); content.addView(option);
        // The verified TuxSheet return is the content root; the dismiss backdrop is its parent.
        ThemeNativeTargets.sheet(content);
        assertNull(sheet.getBackground());
        assertEquals(ThemeEngine.surfaceColor(activity), ((GradientDrawable) content.getBackground()).getColor().getDefaultColor());
        assertEquals(ThemeEngine.textColor(activity), option.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.sheet(content);
        assertSame(fill, content.getBackground()); assertEquals(Color.BLACK, option.getCurrentTextColor());
    }

    @Test
    public void nativeSheetContentWithoutDrawableDoesNotExposeItsWhiteParentBehindThemedText() {
        FrameLayout backdrop = new FrameLayout(activity); backdrop.setBackgroundColor(Color.WHITE);
        profile.addView(backdrop);
        FrameLayout content = new FrameLayout(activity); backdrop.addView(content);
        TextView option = new TextView(activity); option.setText("Save"); option.setTextColor(Color.BLACK);
        content.addView(option);
        ThemeNativeTargets.sheet(content);
        assertEquals(ThemeEngine.surfaceColor(activity), ((ColorDrawable) content.getBackground()).getColor());
        assertEquals(ThemeEngine.textColor(activity), option.getCurrentTextColor());
        assertEquals(Color.WHITE, ((ColorDrawable) backdrop.getBackground()).getColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.sheet(content);
        assertNull(content.getBackground());
        assertEquals(Color.BLACK, option.getCurrentTextColor());
    }

    @Test
    public void enabledTextIsThemedWhenItsDefaultStateIsTransparent() {
        TextView control = new TextView(activity); profile.addView(control);
        android.content.res.ColorStateList colors = new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}, new int[0]},
                new int[]{Color.BLACK, Color.TRANSPARENT});
        control.setTextColor(colors);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), control.getCurrentTextColor());
        control.setEnabled(false);
        assertEquals(Color.TRANSPARENT, control.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertSame(colors, control.getTextColors());
    }

    @Test
    public void unusedBackgroundTintDoesNotTurnLightTextBlack() {
        TextView control = new TextView(activity); profile.addView(control);
        control.setBackground(null);
        control.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.CYAN));
        assertEquals(Color.WHITE, ThemeViewColors.contrastForeground(control, Color.WHITE));
        control.setBackgroundColor(Color.TRANSPARENT);
        assertEquals(Color.WHITE, ThemeViewColors.contrastForeground(control, Color.WHITE));
    }

    @Test
    public void navigationBitmapGlyphIsTintedButPhotosAndColoredArtKeepTheirPixels() {
        FrameLayout navigation = new FrameLayout(activity); profile.addView(navigation);
        ImageView glyph = new ImageView(activity); navigation.addView(glyph);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
        for (int y = 2; y < 6; y++) for (int x = 2; x < 6; x++) bitmap.setPixel(x, y, Color.BLACK);
        glyph.setImageBitmap(bitmap);
        ImageView photo = new ImageView(activity); navigation.addView(photo);
        android.graphics.Bitmap image = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
        image.setPixel(3, 3, Color.WHITE); photo.setImageBitmap(image);
        ImageView brand = new ImageView(activity); navigation.addView(brand);
        android.graphics.Bitmap art = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
        art.setPixel(3, 3, Color.RED); brand.setImageBitmap(art);
        ThemeNativeTargets.navigation(navigation);
        assertEquals(ThemeEngine.textColor(activity), glyph.getImageTintList().getDefaultColor());
        assertNull(photo.getImageTintList()); assertNull(brand.getImageTintList());
        assertEquals(Color.BLACK, bitmap.getPixel(3, 3));
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.navigation(navigation);
        assertNull(glyph.getImageTintList());
    }

    @Test
    public void selectedNotificationFilterKeepsItsHueWithReadableContrast() {
        FrameLayout notifications = new FrameLayout(activity); profile.addView(notifications);
        TextView filter = new TextView(activity); filter.setTextColor(0xff00505c); notifications.addView(filter);
        TextView warning = new TextView(activity); warning.setTextColor(Color.RED); notifications.addView(warning);
        ThemeNativeTargets.systemNotifications(notifications);
        int mapped = filter.getCurrentTextColor();
        assertNotEquals(0xff00505c, mapped);
        assertTrue(Color.blue(mapped) > Color.red(mapped));
        assertEquals(Color.RED, warning.getCurrentTextColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.systemNotifications(notifications);
        assertEquals(0xff00505c, filter.getCurrentTextColor());
    }

    @Test
    public void insetRippleCardKeepsItsRoundedContentAndUntintedMask() {
        FrameLayout notifications = new FrameLayout(activity); profile.addView(notifications);
        FrameLayout card = new FrameLayout(activity); notifications.addView(card);
        GradientDrawable content = new GradientDrawable(); content.setColor(Color.WHITE); content.setCornerRadius(18);
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(18);
        android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33000000), content, mask);
        android.graphics.drawable.InsetDrawable original = new android.graphics.drawable.InsetDrawable(ripple, 5);
        card.setBackground(original);
        ThemeNativeTargets.systemNotifications(notifications);
        assertTrue(card.getBackground() instanceof android.graphics.drawable.InsetDrawable);
        android.graphics.drawable.RippleDrawable styled = (android.graphics.drawable.RippleDrawable)
                ((android.graphics.drawable.InsetDrawable) card.getBackground()).getDrawable();
        GradientDrawable styledContent = (GradientDrawable) styled.getDrawable(0);
        assertEquals(18f, styledContent.getCornerRadius(), 0.01f);
        assertEquals(ThemeEngine.backgroundColor(activity), styledContent.getColor().getDefaultColor());
        assertEquals(Color.WHITE, ((GradientDrawable) styled.findDrawableByLayerId(android.R.id.mask)).getColor().getDefaultColor());
        assertEquals(Color.WHITE, content.getColor().getDefaultColor());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.systemNotifications(notifications);
        assertSame(original, card.getBackground());
    }

    @Test @Config(sdk = 29)
    public void nativeStateListCardKeepsItsFunctionalPressedFill() {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.RED));
        GradientDrawable normal = new GradientDrawable(); normal.setColor(Color.WHITE); normal.setCornerRadius(12);
        states.addState(new int[0], normal); states.setState(new int[0]);
        Drawable styled = ThemeViewColors.copyFlatFill(states, 0xff06121a);
        assertNotNull(styled); styled.setState(new int[0]);
        assertEquals(Integer.valueOf(0xff06121a), ThemeViewColors.flatColor(styled));
        styled.setState(new int[]{android.R.attr.state_pressed});
        assertEquals(Integer.valueOf(Color.RED), ThemeViewColors.flatColor(styled));
        assertEquals(Color.WHITE, normal.getColor().getDefaultColor());
    }

    @Test
    public void brightFunctionalActionRetainsItsCyanFillAndGetsReadableDarkText() {
        TextView button = new TextView(activity); button.setText("Follow");
        button.setBackgroundColor(0xff20d5ec); button.setTextColor(Color.WHITE); profile.addView(button);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(0xff20d5ec, ((ColorDrawable) button.getBackground()).getColor());
        assertEquals(Color.BLACK, button.getCurrentTextColor());
        TextView redAction = new TextView(activity); redAction.setTextColor(Color.RED); profile.addView(redAction);
        ThemeNativeTargets.profilePage(profile); assertEquals(Color.RED, redAction.getCurrentTextColor());
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void nativeTextAppearanceColorCannotHideThemedProfileGlyphs() {
        TextView label = new TextView(activity); label.setTextSize(28); label.setTextColor(Color.WHITE);
        android.text.SpannableString nativeText = new android.text.SpannableString("Profile heading");
        android.text.style.TextAppearanceSpan appearance = new android.text.style.TextAppearanceSpan(null, 0, 28,
                android.content.res.ColorStateList.valueOf(Color.BLACK), null);
        nativeText.setSpan(appearance, 0, nativeText.length(), 33); label.setText(nativeText); profile.addView(label);
        CharSequence original = label.getText(); ThemeNativeTargets.profilePage(profile);
        label.measure(View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        label.layout(0, 0, 350, 100);
        android.graphics.Bitmap pixels = android.graphics.Bitmap.createBitmap(350, 100, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(pixels); canvas.drawColor(Color.BLACK); label.draw(canvas);
        int glyphs = 0;
        for (int y = 0; y < 100; y++) for (int x = 0; x < 350; x++)
            if (Color.red(pixels.getPixel(x, y)) > 200 && Color.green(pixels.getPixel(x, y)) > 200) glyphs++;
        assertTrue("Native TextAppearanceSpan must not leave black glyphs on the dark profile", glyphs > 50);
        assertTrue(((android.text.Spanned) label.getText()).getSpanStart(appearance) >= 0);
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertSame(original, label.getText()); pixels.recycle();
    }

    @Test
    public void aMissingBackgroundIsNotUnboxedOrTreatedAsAFunctionalFill() {
        TextView unpainted = new TextView(activity); unpainted.setTextColor(Color.BLACK);
        assertNull(unpainted.getBackground()); assertNull(unpainted.getBackgroundTintList());
        assertEquals(ThemeEngine.textColor(activity), ThemeViewColors.contrastForeground(unpainted, ThemeEngine.textColor(activity)));
        profile.addView(unpainted); ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), unpainted.getCurrentTextColor());
    }

    @Test
    public void detachedViewInNativeReplacementSpanGetsContrastAndRestoresWithoutChangingSpanGeometry() {
        TextView embedded = new TextView(activity); embedded.setText("Add bio"); embedded.setTextColor(Color.BLACK);
        com.bytedance.tux.drawable.TuxIconDrawable plus = new com.bytedance.tux.drawable.TuxIconDrawable();
        android.content.res.ColorStateList originalIcon = android.content.res.ColorStateList.valueOf(Color.BLACK);
        plus.colors = originalIcon; embedded.setCompoundDrawablesRelative(plus, null, null, null);
        ViewReplacementSpan span = new ViewReplacementSpan(embedded);
        TextView label = new TextView(activity); label.setTextColor(Color.WHITE);
        android.text.SpannableString text = new android.text.SpannableString("Bio");
        text.setSpan(span, 0, text.length(), 33); label.setText(text); profile.addView(label);
        assertNull("Embedded tag is not a child in the profile hierarchy", embedded.getParent());
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), embedded.getCurrentTextColor());
        assertEquals(ThemeEngine.textColor(activity), plus.colors.getDefaultColor());
        android.text.Spanned themed = (android.text.Spanned) label.getText();
        assertSame(span, themed.getSpans(0, themed.length(), android.text.style.ReplacementSpan.class)[0]);
        assertEquals(73, span.getSize(new android.graphics.Paint(), themed, 0, themed.length(), null));
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertEquals(Color.BLACK, embedded.getCurrentTextColor()); assertSame(originalIcon, plus.colors);
        assertSame(span, ((android.text.Spanned) label.getText()).getSpans(0, text.length(),
                android.text.style.ReplacementSpan.class)[0]);
    }

    @Test
    public void inlineNativeDrawableRetainsBoundsAndFunctionalColorsAndRestoresItsExactPalette() {
        com.bytedance.tux.drawable.TuxIconDrawable icon = new com.bytedance.tux.drawable.TuxIconDrawable();
        android.content.res.ColorStateList original = android.content.res.ColorStateList.valueOf(Color.BLACK);
        icon.colors = original; icon.setBounds(1, 2, 20, 24);
        DrawableReplacementSpan span = new DrawableReplacementSpan(icon);
        com.bytedance.tux.drawable.TuxIconDrawable colored = new com.bytedance.tux.drawable.TuxIconDrawable();
        colored.colors = android.content.res.ColorStateList.valueOf(Color.RED);
        android.text.SpannableString text = new android.text.SpannableString("++");
        text.setSpan(span, 0, 1, 33); text.setSpan(new DrawableReplacementSpan(colored), 1, 2, 33);
        TextView label = new TextView(activity); label.setText(text); profile.addView(label);
        ThemeNativeTargets.profilePage(profile);
        assertEquals(ThemeEngine.textColor(activity), icon.colors.getDefaultColor());
        assertEquals(Color.RED, colored.colors.getDefaultColor());
        assertEquals(new android.graphics.Rect(1, 2, 20, 24), icon.getBounds());
        ThemeStateStore.saveUserPreset(activity, "default"); ThemeNativeTargets.profilePage(profile);
        assertSame(original, icon.colors);
    }

    private static class ViewSpanBase extends android.text.style.ReplacementSpan {
        private final View content;
        ViewSpanBase(View content) { this.content = content; }
        @Override public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end,
                android.graphics.Paint.FontMetricsInt metrics) { return 73; }
        @Override public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, android.graphics.Paint paint) { content.draw(canvas); }
    }
    private static final class ViewReplacementSpan extends ViewSpanBase {
        ViewReplacementSpan(View content) { super(content); }
    }
    private static final class DrawableReplacementSpan extends android.text.style.ReplacementSpan {
        private final Drawable content;
        DrawableReplacementSpan(Drawable content) { this.content = content; }
        @Override public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end,
                android.graphics.Paint.FontMetricsInt metrics) { return content.getBounds().width(); }
        @Override public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, android.graphics.Paint paint) { content.draw(canvas); }
    }

    /** Captures the three Paint/four Path native painter and its public note APIs. */
    public static final class NoteView extends TextView {
        final NotePainter painter = new NotePainter();
        Object gradient; boolean sticker;
        public NoteView(Context context) { super(context); }
        public Object getBubbleStyle() { return this; }
        public Object getBubbleBackgroundGradientImgData() { return gradient; }
        public boolean getEnableStarSticker() { return sticker; }
    }
    public static final class NotePainter {
        final android.graphics.Path a = new android.graphics.Path(), b = new android.graphics.Path(),
                c = new android.graphics.Path(), d = new android.graphics.Path();
        final android.graphics.Paint fill = new android.graphics.Paint(), shadow = new android.graphics.Paint(),
                accent = new android.graphics.Paint();
        android.graphics.Shader shader; Drawable image;
        NotePainter() { fill.setColor(Color.WHITE); shadow.setColor(0x77ffffff); accent.setColor(Color.RED); }
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
