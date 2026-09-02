package app.morphe.extension.tiktok.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;

/**
 * Narrow native-view hooks for TikTok 46.7.3 surfaces that do not reliably repaint through the
 * generic TUX resolver alone.
 *
 * These hooks are exact and bounded: the bytecode patch calls them only from verified bottom-tab,
 * profile-sidebar and SettingsComposeRvmpFragment lifecycle paths. Every operation is fail-open.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeViewHooks {
    private static final int MAX_OVERLAY_SCAN_NODES = 1800;
    private static final AtomicBoolean BOTTOM_NAV_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SIDEBAR_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean SETTINGS_COMPOSE_LOGGED = new AtomicBoolean(false);
    private static final AtomicInteger SIDEBAR_RESET_LOG_BUDGET = new AtomicInteger(8);

    private ThemeViewHooks() {}

    /** Called from MainPageBusinessAssem after TikTok has assigned/animated its bottom-tab view. */
    public static void styleBottomNavigation(View backgroundView) {
        try {
            if (backgroundView == null) return;
            Context context = backgroundView.getContext();
            if (!themeActive(context)) return;

            applyBottomNavigation(backgroundView);
            // TikTok's showBottomTab animator lasts ~350 ms including its start delay. Reapply only
            // a few bounded times so its own animation cannot restore the stock bar afterwards.
            backgroundView.postDelayed(() -> applyBottomNavigation(backgroundView), 80L);
            backgroundView.postDelayed(() -> applyBottomNavigation(backgroundView), 220L);
            backgroundView.postDelayed(() -> applyBottomNavigation(backgroundView), 420L);

            if (BOTTOM_NAV_LOGGED.compareAndSet(false, true)) {
                final int alpha = Color.alpha(ThemeEngine.surfaceColor(context));
                Logger.printInfo(() -> "[BlueIT Theme View] main bottom navigation styled alpha="
                        + alpha);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyBottomNavigation(View backgroundView) {
        try {
            Context context = backgroundView.getContext();
            if (!themeActive(context)) return;

            int surface = ThemeEngine.surfaceColor(context);
            int divider = ThemeEngine.dividerColor(context);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(surface);
            if (Color.alpha(surface) < 250 || ThemeStateStore.isLiquidGlass(context)) {
                background.setStroke(Math.max(1, Math.round(dp(context, 1))), divider);
            }

            backgroundView.setBackground(background);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                backgroundView.setBackgroundTintList(null);
                backgroundView.setElevation(dp(context, Color.alpha(surface) < 250 ? 6 : 2));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Exact root hook for SettingsComposeRvmpFragment. TUX token replacement styles semantic
     * content; this hook supplies the missing page/root backdrop that Compose otherwise paints with
     * TikTok's stock container color.
     */
    public static void styleSettingsCompose(View composeRoot) {
        try {
            if (composeRoot == null) return;
            Context context = composeRoot.getContext();
            if (!themeActive(context)) return;

            applySettingsCompose(composeRoot);
            composeRoot.post(() -> applySettingsCompose(composeRoot));
            composeRoot.postDelayed(() -> applySettingsCompose(composeRoot), 100L);
            composeRoot.postDelayed(() -> applySettingsCompose(composeRoot), 320L);
            composeRoot.postDelayed(() -> applySettingsCompose(composeRoot), 800L);
            ThemeEngine.requestReapply();

            if (SETTINGS_COMPOSE_LOGGED.compareAndSet(false, true)) {
                Logger.printInfo(() -> "[BlueIT Theme View] SettingsComposeRvmpFragment root styled");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applySettingsCompose(View composeRoot) {
        try {
            Context context = composeRoot.getContext();
            if (!themeActive(context)) return;

            int background = ThemeEngine.backgroundColor(context);
            int surface = ThemeEngine.surfaceColor(context);
            int accent = ThemeEngine.accentColor(context);
            int divider = ThemeEngine.dividerColor(context);
            boolean glass = ThemeStateStore.isLiquidGlass(context);

            // Give the translucent Compose root something visible to reveal underneath it. This is
            // intentionally scoped to the verified SettingsComposeRvmpFragment parent only.
            ViewParent parent = composeRoot.getParent();
            if (parent instanceof View) {
                View parentView = (View) parent;
                if (glass) {
                    GradientDrawable backdrop = new GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            new int[]{
                                    mixOpaque(background, accent, 0.14f),
                                    mixOpaque(background, Color.WHITE, 0.035f),
                                    mixOpaque(background, accent, 0.055f)
                            }
                    );
                    parentView.setBackground(backdrop);
                } else {
                    parentView.setBackgroundColor(opaque(background));
                }
            }

            GradientDrawable root = new GradientDrawable();
            root.setShape(GradientDrawable.RECTANGLE);
            root.setColor(glass ? surface : background);
            if (glass || Color.alpha(surface) < 250) {
                root.setStroke(Math.max(1, Math.round(dp(context, 1))), divider);
            }
            composeRoot.setBackground(root);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                composeRoot.setBackgroundTintList(null);
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
            ViewParent parent = child.getParent();
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

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int mixOpaque(int source, int target, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(source) * (1f - t) + Color.red(target) * t);
        int green = Math.round(Color.green(source) * (1f - t) + Color.green(target) * t);
        int blue = Math.round(Color.blue(source) * (1f - t) + Color.blue(target) * t);
        return Color.rgb(red, green, blue);
    }

    private static float dp(Context context, int value) {
        try {
            return value * context.getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {
            return value;
        }
    }
}
