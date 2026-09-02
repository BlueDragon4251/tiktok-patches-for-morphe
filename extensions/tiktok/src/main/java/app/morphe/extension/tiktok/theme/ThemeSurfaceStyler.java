package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Applies BlueIT theme palettes to concrete TikTok UI surfaces.
 *
 * TikTok 46.7.3 mixes classic Views, dynamically inflated containers and obfuscated ids/classes.
 * The classifier therefore combines stable resource/class-name hints with conservative geometry.
 * It deliberately avoids broad recoloring of full-screen video renderer containers.
 */
@SuppressWarnings({"deprecation", "unused"})
final class ThemeSurfaceStyler {
    private static final int MAX_SCAN_NODES = 2600;

    private ThemeSurfaceStyler() {}

    static void apply(
            Activity activity,
            String preset,
            int background,
            int surface,
            int accent,
            int text,
            int secondaryText,
            int divider,
            int cornerRadiusDp
    ) {
        if (activity == null || activity.isFinishing()) return;

        View decor = activity.getWindow().getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return;

        int rootWidth = decor.getWidth();
        int rootHeight = decor.getHeight();
        ScreenHints hints = scanScreenHints(decor);

        styleTree(
                decor,
                preset,
                background,
                surface,
                accent,
                text,
                secondaryText,
                divider,
                cornerRadiusDp,
                rootWidth,
                rootHeight,
                hints,
                false,
                false,
                false,
                false
        );
    }

    private static ScreenHints scanScreenHints(View root) {
        ScreenHints hints = new ScreenHints();
        int[] remaining = {MAX_SCAN_NODES};
        scanHintsRecursive(root, hints, remaining);
        return hints;
    }

    private static void scanHintsRecursive(View view, ScreenHints hints, int[] remaining) {
        if (view == null || remaining[0]-- <= 0 || view.getVisibility() != View.VISIBLE) return;

        String resource = resourceEntryName(view);
        String className = className(view);
        String combined = resource + " " + className;

        if (containsAny(combined,
                "inbox", "conversation", "message_list", "session_list", "chat_list",
                "notification_list", "notice_list", "im_session")) {
            hints.inboxLike = true;
        }

        if (containsAny(combined,
                "setting", "preference", "privacy_setting", "settings_page", "settings_root")) {
            hints.settingsLike = true;
        }

        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null) {
                String text = value.toString().trim();
                if ("BlueIT Service".equals(text)) {
                    // This row only exists on TikTok's Settings & privacy screen.
                    hints.settingsLike = true;
                }

                if ("Inbox".equalsIgnoreCase(text)
                        || "Posteingang".equalsIgnoreCase(text)
                        || "Messages".equalsIgnoreCase(text)
                        || "Nachrichten".equalsIgnoreCase(text)) {
                    hints.inboxLike = true;
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanHintsRecursive(group.getChildAt(i), hints, remaining);
                if (remaining[0] <= 0) return;
            }
        }
    }

    private static void styleTree(
            View view,
            String preset,
            int background,
            int surface,
            int accent,
            int text,
            int secondaryText,
            int divider,
            int cornerRadiusDp,
            int rootWidth,
            int rootHeight,
            ScreenHints hints,
            boolean insideDrawer,
            boolean insideSettings,
            boolean insideInbox,
            boolean parentCardStyled
    ) {
        if (view == null || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return;

        if (!(view instanceof ViewGroup)) {
            styleLeaf(view, accent, text, secondaryText, divider);
            return;
        }

        ViewGroup group = (ViewGroup) view;
        String resource = resourceEntryName(group);
        String className = className(group);
        String combined = resource + " " + className;

        boolean drawer = isLikelyDrawer(group, rootWidth, rootHeight);
        boolean settingsRoot = hints.settingsLike && isLargeScreenContainer(group, rootWidth, rootHeight);
        boolean inboxRoot = hints.inboxLike && isLargeScreenContainer(group, rootWidth, rootHeight);
        boolean bottomNavigation = isBottomNavigation(group, combined, rootWidth, rootHeight);
        boolean sheet = isSheet(group, combined, rootWidth, rootHeight);

        boolean nowInsideDrawer = insideDrawer || drawer;
        boolean nowInsideSettings = insideSettings || settingsRoot;
        boolean nowInsideInbox = insideInbox || inboxRoot;

        if (settingsRoot || inboxRoot) {
            // Do not blindly theme every full-screen root. These flags are only set after finding
            // a visible Settings/Inbox anchor in the current hierarchy.
            group.setBackgroundColor(opaque(background));
        }

        boolean settingsCard = nowInsideSettings
                && !settingsRoot
                && !parentCardStyled
                && isCardContainer(group, rootWidth, rootHeight);
        boolean inboxRow = nowInsideInbox
                && !inboxRoot
                && !parentCardStyled
                && isListRow(group, rootWidth, rootHeight);
        boolean drawerSection = nowInsideDrawer
                && !drawer
                && !parentCardStyled
                && isDrawerSection(group, rootWidth, rootHeight);

        boolean themedSurface = drawer
                || bottomNavigation
                || sheet
                || settingsCard
                || inboxRow
                || drawerSection;

        if (themedSurface) {
            int radius = cornerRadiusDp;
            if (inboxRow) radius = Math.min(22, Math.max(12, cornerRadiusDp));
            if (settingsCard) radius = Math.min(26, Math.max(12, cornerRadiusDp));
            if (drawer) radius = 0;

            applySurface(group, preset, surface, divider, radius);
            styleTextAndIcons(group, accent, text, secondaryText, divider);
        } else if (settingsRoot || inboxRoot) {
            // Screen roots often contain title/toolbar text outside a dedicated card surface.
            styleTextAndIcons(group, accent, text, secondaryText, divider);
        }

        boolean childParentCardStyled = parentCardStyled || settingsCard || inboxRow || drawerSection;

        for (int index = 0; index < group.getChildCount(); index++) {
            styleTree(
                    group.getChildAt(index),
                    preset,
                    background,
                    surface,
                    accent,
                    text,
                    secondaryText,
                    divider,
                    cornerRadiusDp,
                    rootWidth,
                    rootHeight,
                    hints,
                    nowInsideDrawer,
                    nowInsideSettings,
                    nowInsideInbox,
                    childParentCardStyled
            );
        }
    }

    private static void applySurface(
            ViewGroup group,
            String preset,
            int surface,
            int divider,
            int radiusDp
    ) {
        Context context = group.getContext();
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(surface);
        drawable.setCornerRadius(dp(context, Math.max(0, radiusDp)));

        boolean translucent = Color.alpha(surface) < 250;
        boolean glassLike = translucent
                || "liquid_glass".equals(preset)
                || "frosted_graphite".equals(preset);

        if (glassLike || radiusDp > 0) {
            drawable.setStroke(Math.max(1, Math.round(dp(context, 1))), divider);
        }

        group.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            group.setBackgroundTintList(null);
            if (radiusDp > 0) {
                group.setClipToOutline(true);
            }
            group.setElevation(dp(context, glassLike ? 6 : 2));
        }
    }

    private static void styleTextAndIcons(
            View root,
            int accent,
            int primary,
            int secondary,
            int divider
    ) {
        if (root == null || root.getVisibility() != View.VISIBLE) return;

        styleLeaf(root, accent, primary, secondary, divider);

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleTextAndIcons(group.getChildAt(i), accent, primary, secondary, divider);
            }
        }
    }

    private static void styleLeaf(
            View view,
            int accent,
            int primary,
            int secondary,
            int divider
    ) {
        String resource = resourceEntryName(view);

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            boolean secondaryText = containsAny(resource,
                    "summary", "subtitle", "secondary", "description", "desc", "hint", "time")
                    || textSizeSp(textView) < 14.5f;

            int color = (textView.isSelected() || textView.isActivated())
                    ? accent
                    : (secondaryText ? secondary : primary);

            textView.setTextColor(color);
            textView.setHintTextColor(secondary);
            textView.setLinkTextColor(accent);
            tintCompoundDrawables(textView, color);
            return;
        }

        if (view instanceof ImageView) {
            tintImageIfIcon((ImageView) view, resource, primary);
            return;
        }

        if (isDivider(view)) {
            view.setBackgroundColor(divider);
        }
    }

    private static boolean isBottomNavigation(
            ViewGroup group,
            String combined,
            int rootWidth,
            int rootHeight
    ) {
        if (containsAny(combined,
                "bottom_navigation", "bottom_nav", "bottom_tab", "main_tab",
                "tab_bar", "navigation_bar", "main_bottom")) {
            return true;
        }

        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.88f) return false;

        int minHeight = Math.round(dp(group.getContext(), 44));
        int maxHeight = Math.round(dp(group.getContext(), 116));
        if (height < minHeight || height > maxHeight) return false;

        int[] location = location(group);
        if (location == null) return false;
        int bottom = location[1] + height;
        if (location[1] < rootHeight * 0.76f
                || bottom < rootHeight - Math.round(dp(group.getContext(), 28))) {
            return false;
        }

        int visibleChildren = 0;
        int compactChildren = 0;
        int firstCenter = Integer.MAX_VALUE;
        int lastCenter = Integer.MIN_VALUE;

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getWidth() <= 0) continue;
            visibleChildren++;
            if (child.getWidth() <= rootWidth * 0.34f) {
                compactChildren++;
            }
            int center = child.getLeft() + child.getWidth() / 2;
            firstCenter = Math.min(firstCenter, center);
            lastCenter = Math.max(lastCenter, center);
        }

        if (visibleChildren < 4 || visibleChildren > 7 || compactChildren < 4) return false;
        if (lastCenter - firstCenter < rootWidth * 0.58f) return false;

        int shortTexts = countShortTextViews(group, 3, 12);
        int images = countImageViews(group, 3, 12);
        return shortTexts >= 3 || images >= 3;
    }

    private static boolean isLikelyDrawer(ViewGroup group, int rootWidth, int rootHeight) {
        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.58f || width > rootWidth * 0.96f) return false;
        if (height < rootHeight * 0.72f) return false;

        int[] location = location(group);
        if (location == null) return false;

        int right = location[0] + width;
        boolean touchesLeft = location[0] <= Math.round(dp(group.getContext(), 8));
        boolean touchesRight = right >= rootWidth - Math.round(dp(group.getContext(), 8));
        if (!touchesLeft && !touchesRight) return false;

        return countTextViews(group, 1, 5) >= 3;
    }

    private static boolean isSheet(
            ViewGroup group,
            String combined,
            int rootWidth,
            int rootHeight
    ) {
        if (containsAny(combined,
                "bottom_sheet", "comments_panel", "comment_panel", "share_panel",
                "panel_container", "action_sheet", "dialog_sheet", "half_screen")) {
            return true;
        }

        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.78f) return false;
        if (height < rootHeight * 0.18f || height > rootHeight * 0.78f) return false;

        int[] location = location(group);
        if (location == null) return false;
        int bottom = location[1] + height;
        if (bottom < rootHeight - Math.round(dp(group.getContext(), 18))) return false;

        // Geometry-only fallback is intentionally strict so the feed/video renderer is untouched.
        return countTextViews(group, 2, 4) >= 3
                && group.getChildCount() >= 2
                && group.getChildCount() <= 12;
    }

    private static boolean isLargeScreenContainer(ViewGroup group, int rootWidth, int rootHeight) {
        if (group.getWidth() < rootWidth * 0.88f) return false;
        if (group.getHeight() < rootHeight * 0.68f) return false;

        int[] location = location(group);
        if (location == null) return false;
        return location[0] <= rootWidth * 0.08f;
    }

    private static boolean isCardContainer(ViewGroup group, int rootWidth, int rootHeight) {
        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.78f || width > rootWidth * 1.02f) return false;

        int minHeight = Math.round(dp(group.getContext(), 64));
        int maxHeight = Math.min(
                Math.round(dp(group.getContext(), 620)),
                Math.round(rootHeight * 0.55f)
        );
        if (height < minHeight || height > maxHeight) return false;
        if (group.getChildCount() < 1 || group.getChildCount() > 18) return false;

        int texts = countTextViews(group, 2, 5);
        return texts >= 1;
    }

    private static boolean isListRow(ViewGroup group, int rootWidth, int rootHeight) {
        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.74f) return false;

        int minHeight = Math.round(dp(group.getContext(), 54));
        int maxHeight = Math.round(dp(group.getContext(), 118));
        if (height < minHeight || height > maxHeight) return false;

        int[] location = location(group);
        if (location == null) return false;

        // Keep the dedicated bottom nav classifier responsible for the final screen strip.
        if (location[1] + height > rootHeight - Math.round(dp(group.getContext(), 110))) {
            return false;
        }

        int texts = countTextViews(group, 3, 6);
        int images = countImageViews(group, 3, 6);
        return texts >= 1
                && texts <= 5
                && images >= 1
                && group.getChildCount() >= 2
                && group.getChildCount() <= 10;
    }

    private static boolean isDrawerSection(ViewGroup group, int rootWidth, int rootHeight) {
        int width = group.getWidth();
        int height = group.getHeight();
        if (width < rootWidth * 0.52f || width > rootWidth * 0.94f) return false;

        int minHeight = Math.round(dp(group.getContext(), 48));
        int maxHeight = Math.round(dp(group.getContext(), 150));
        if (height < minHeight || height > maxHeight) return false;

        int texts = countTextViews(group, 2, 5);
        return texts >= 1 && texts <= 4;
    }

    private static int countTextViews(View root, int maxDepth, int maxCount) {
        if (root == null || maxDepth < 0 || maxCount <= 0) return 0;
        int count = root instanceof TextView ? 1 : 0;
        if (count >= maxCount || !(root instanceof ViewGroup) || maxDepth == 0) return count;

        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount() && count < maxCount; i++) {
            count += countTextViews(group.getChildAt(i), maxDepth - 1, maxCount - count);
        }
        return count;
    }

    private static int countShortTextViews(View root, int maxDepth, int maxCount) {
        if (root == null || maxDepth < 0 || maxCount <= 0) return 0;

        int count = 0;
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null) {
                int length = text.toString().trim().length();
                if (length > 0 && length <= 24) count = 1;
            }
        }

        if (count >= maxCount || !(root instanceof ViewGroup) || maxDepth == 0) return count;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount() && count < maxCount; i++) {
            count += countShortTextViews(group.getChildAt(i), maxDepth - 1, maxCount - count);
        }
        return count;
    }

    private static int countImageViews(View root, int maxDepth, int maxCount) {
        if (root == null || maxDepth < 0 || maxCount <= 0) return 0;
        int count = root instanceof ImageView ? 1 : 0;
        if (count >= maxCount || !(root instanceof ViewGroup) || maxDepth == 0) return count;

        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount() && count < maxCount; i++) {
            count += countImageViews(group.getChildAt(i), maxDepth - 1, maxCount - count);
        }
        return count;
    }

    private static void tintImageIfIcon(ImageView imageView, String resource, int color) {
        if (resource == null) resource = "";

        if (containsAny(resource,
                "avatar", "profile", "photo", "image", "cover", "thumbnail", "story", "head")) {
            return;
        }

        boolean iconName = containsAny(resource,
                "icon", "arrow", "chevron", "back", "menu", "search", "setting",
                "close", "more", "camera", "bell", "shield", "wallet", "qr");
        if (!iconName) return;

        int maxSize = Math.round(dp(imageView.getContext(), 64));
        if (imageView.getWidth() > maxSize || imageView.getHeight() > maxSize) return;

        Drawable drawable = imageView.getDrawable();
        if (drawable == null) return;

        try {
            Drawable tinted = drawable.mutate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tinted.setTint(color);
                imageView.setImageDrawable(tinted);
                imageView.setImageTintList(ColorStateList.valueOf(color));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void tintCompoundDrawables(TextView textView, int color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        try {
            Drawable[] drawables = textView.getCompoundDrawablesRelative();
            for (Drawable drawable : drawables) {
                if (drawable != null) {
                    drawable.mutate().setTint(color);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isDivider(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) return false;

        float density = view.getResources().getDisplayMetrics().density;
        return height <= Math.max(2, Math.round(1.5f * density))
                && width >= Math.round(48f * density);
    }

    private static float textSizeSp(TextView view) {
        float scaledDensity = view.getResources().getDisplayMetrics().scaledDensity;
        if (scaledDensity <= 0f) return 16f;
        return view.getTextSize() / scaledDensity;
    }

    private static int[] location(View view) {
        try {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            return location;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resourceEntryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID || id == 0) return "";

        try {
            return view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String className(View view) {
        String simple = view.getClass().getSimpleName();
        return simple == null ? "" : simple.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isEmpty()) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token)) return true;
        }
        return false;
    }

    private static int opaque(int color) {
        return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float dp(Context context, int value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    private static final class ScreenHints {
        boolean settingsLike;
        boolean inboxLike;
    }
}
