package app.morphe.extension.tiktok.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;

/**
 * Narrow native-view hooks for TikTok 46.7.3 surfaces that do not reliably repaint through the
 * generic TUX resolver alone.
 *
 * These hooks are deliberately exact and bounded: the bytecode patch only calls them from the
 * verified main bottom-tab and profile-sidebar lifecycle paths. Every operation is fail-open.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeViewHooks {
    private static final int MAX_OVERLAY_SCAN_NODES = 1800;
    private static final AtomicBoolean BOTTOM_NAV_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SIDEBAR_LOGGED = new AtomicBoolean(false);
    private static final AtomicInteger SIDEBAR_RESET_LOG_BUDGET = new AtomicInteger(8);

    private ThemeViewHooks() {}

    /** Called from MainPageBusinessAssem.sh() after TikTok has assigned its stock tab background. */
    public static void styleBottomNavigation(View backgroundView) {
        try {
            if (backgroundView == null) return;
            Context context = backgroundView.getContext();
            if (!themeActive(context)) return;

            int surface = ThemeEngine.surfaceColor(context);
            int divider = ThemeEngine.dividerColor(context);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(surface);

            // A transparent Liquid Glass bar needs a visible edge against moving video content.
            if (Color.alpha(surface) < 250 || ThemeStateStore.isLiquidGlass(context)) {
                background.setStroke(Math.max(1, Math.round(dp(context, 1))), divider);
            }

            backgroundView.setBackground(background);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                backgroundView.setBackgroundTintList(null);
                backgroundView.setElevation(dp(context, Color.alpha(surface) < 250 ? 6 : 2));
            }

            if (BOTTOM_NAV_LOGGED.compareAndSet(false, true)) {
                final String message = "[BlueIT Theme View] main bottom navigation styled alpha="
                        + Color.alpha(surface);
                Logger.printInfo(() -> message);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called from SidebarPageFragment creation/show. Besides styling the sidebar itself, this turns
     * TikTok's current push-aside presentation into an overlay by cancelling only the large
     * translated underlay while the verified sidebar is visible.
     */
    public static void styleProfileSidebar(View sidebarRoot) {
        try {
            if (sidebarRoot == null) return;
            Context context = sidebarRoot.getContext();
            if (!themeActive(context)) return;

            int surface = ThemeEngine.surfaceColor(context);
            int divider = ThemeEngine.dividerColor(context);
            int radius = ThemeStateStore.isLiquidGlass(context) ? 24 : 16;

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(surface);
            float corner = dp(context, radius);
            // Sidebar is right-aligned: only its exposed left edge receives rounded corners.
            background.setCornerRadii(new float[]{corner, corner, 0f, 0f, 0f, 0f, corner, corner});
            if (Color.alpha(surface) < 250 || ThemeStateStore.isLiquidGlass(context)) {
                background.setStroke(Math.max(1, Math.round(dp(context, 1))), divider);
            }
            sidebarRoot.setBackground(background);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                sidebarRoot.setBackgroundTintList(null);
                sidebarRoot.setElevation(dp(context, 18));
            }
            sidebarRoot.bringToFront();

            // TikTok animates the profile content left while this page is shown. The animation lasts
            // a few hundred milliseconds, so use a small bounded set of corrections rather than a
            // permanent layout/global-draw listener.
            sidebarRoot.post(() -> restoreSidebarUnderlay(sidebarRoot));
            sidebarRoot.postDelayed(() -> restoreSidebarUnderlay(sidebarRoot), 32L);
            sidebarRoot.postDelayed(() -> restoreSidebarUnderlay(sidebarRoot), 96L);
            sidebarRoot.postDelayed(() -> restoreSidebarUnderlay(sidebarRoot), 180L);
            sidebarRoot.postDelayed(() -> restoreSidebarUnderlay(sidebarRoot), 320L);

            if (SIDEBAR_LOGGED.compareAndSet(false, true)) {
                final String message = "[BlueIT Theme View] profile sidebar styled alpha="
                        + Color.alpha(surface);
                Logger.printInfo(() -> message);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreSidebarUnderlay(View sidebarRoot) {
        try {
            View root = sidebarRoot.getRootView();
            if (!(root instanceof ViewGroup)) return;

            int rootWidth = root.getWidth();
            int rootHeight = root.getHeight();
            if (rootWidth <= 0 || rootHeight <= 0) return;

            float minTranslation = rootWidth * 0.08f;
            int reset = 0;
            int visited = 0;

            ArrayDeque<View> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty() && visited++ < MAX_OVERLAY_SCAN_NODES) {
                View view = queue.removeFirst();
                if (view == null || view.getVisibility() != View.VISIBLE) continue;

                // Never move the sidebar or one of its ancestors/descendants.
                if (view == sidebarRoot || isAncestor(view, sidebarRoot) || isAncestor(sidebarRoot, view)) {
                    if (view instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) view;
                        for (int i = 0; i < group.getChildCount(); i++) {
                            queue.addLast(group.getChildAt(i));
                        }
                    }
                    continue;
                }

                if (view.getWidth() >= rootWidth * 0.82f
                        && view.getHeight() >= rootHeight * 0.62f
                        && Math.abs(view.getTranslationX()) >= minTranslation) {
                    view.setTranslationX(0f);
                    reset++;
                }

                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        queue.addLast(group.getChildAt(i));
                    }
                }
            }

            if (reset > 0 && SIDEBAR_RESET_LOG_BUDGET.getAndDecrement() > 0) {
                final int corrected = reset;
                Logger.printInfo(() -> "[BlueIT Theme View] sidebar overlay restored underlay views="
                        + corrected);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAncestor(View possibleAncestor, View child) {
        try {
            if (!(possibleAncestor instanceof ViewGroup) || child == null) return false;
            android.view.ViewParent parent = child.getParent();
            while (parent instanceof View) {
                if (parent == possibleAncestor) return true;
                parent = parent.getParent();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean themeActive(Context context) {
        try {
            return context != null && !"default".equals(ThemeStateStore.currentPreset(context));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float dp(Context context, int value) {
        try {
            return value * context.getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {
            return value;
        }
    }
}
