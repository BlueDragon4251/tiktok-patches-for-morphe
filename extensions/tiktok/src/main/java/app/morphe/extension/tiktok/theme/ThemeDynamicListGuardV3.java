package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;

/**
 * Third-generation dynamic list guard for TikTok 46.7.3.
 *
 * dev.17 proved that geometry-only section matching was the wrong abstraction: the guard found and
 * themed ten Activity rows while matching zero section containers. V3 derives the section from the
 * rows TikTok actually attached. Every pre-draw pass styles the currently visible rows and then walks
 * their ancestor chains, so recycler rebinding cannot expose a stock-light frame and the owning
 * recommendation/list surface is themed regardless of its obfuscated class or measured height.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeDynamicListGuardV3 {
    private static final int SCREEN_NONE = 0;
    private static final int SCREEN_INBOX = 1;
    private static final int SCREEN_ACTIVITY = 2;
    private static final int MAX_NODES = 1800;
    private static final WeakHashMap<View, Guard> GUARDS = new WeakHashMap<>();
    private static final AtomicInteger LOG_BUDGET = new AtomicInteger(20);

    private ThemeDynamicListGuardV3() {}

    public static void install(Activity activity) {
        try {
            if (activity == null || activity.isFinishing() || activity.getWindow() == null) return;
            View root = activity.getWindow().getDecorView();
            if (root == null) return;
            synchronized (GUARDS) {
                Guard existing = GUARDS.get(root);
                if (existing != null) {
                    existing.activityRef = new WeakReference<>(activity);
                    return;
                }
                Guard guard = new Guard(activity, root);
                GUARDS.put(root, guard);
                guard.attach();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean themeActive(View root) {
        try {
            return root != null && root.getContext() != null
                    && !"default".equals(ThemeStateStore.currentPreset(root.getContext()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ScreenInfo detectScreen(View root) {
        int kind = SCREEN_NONE;
        int titleBottom = 0;
        try {
            int rootHeight = root.getHeight();
            if (rootHeight <= 0) return new ScreenInfo(kind, titleBottom);
            boolean directChat = false;
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.addLast(new Node(root, 0));
            int visited = 0;
            while (!queue.isEmpty() && visited++ < 800) {
                Node node = queue.removeFirst();
                View view = node.view;
                if (view == null || view.getVisibility() != View.VISIBLE) continue;
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    CharSequence value = tv.getText();
                    if (value != null) {
                        String lower = value.toString().trim().toLowerCase(Locale.ROOT);
                        if (lower.equals("schreib etwas") || lower.equals("schreibe etwas")
                                || lower.equals("write a message") || lower.equals("send a message")
                                || lower.equals("type a message") || lower.equals("write something")) {
                            directChat = true;
                        }
                        int[] xy = location(tv);
                        if (xy != null && xy[1] >= 0 && xy[1] < rootHeight * 0.30f) {
                            if (isInboxTitle(lower)) {
                                kind = SCREEN_INBOX;
                                titleBottom = Math.max(titleBottom, xy[1] + tv.getHeight());
                            } else if (isActivityTitle(lower)) {
                                kind = SCREEN_ACTIVITY;
                                titleBottom = Math.max(titleBottom, xy[1] + tv.getHeight());
                            }
                        }
                    }
                }
                if (view instanceof ViewGroup && node.depth < 28) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        queue.addLast(new Node(group.getChildAt(i), node.depth + 1));
                    }
                }
            }
            if (directChat) kind = SCREEN_NONE;
        } catch (Throwable ignored) {
        }
        return new ScreenInfo(kind, titleBottom);
    }

    private static boolean isInboxTitle(String value) {
        return value.equals("posteingang") || value.equals("inbox")
                || value.equals("messages") || value.equals("nachrichten");
    }

    private static boolean isActivityTitle(String value) {
        return value.equals("aktivität") || value.equals("activity")
                || value.equals("all activity") || value.equals("alle aktivitäten")
                || value.equals("notifications") || value.equals("benachrichtigungen");
    }

    private static boolean isSuggestedLabel(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return lower.equals("vorgeschlagene konten")
                || lower.equals("vorgeschlagene accounts")
                || lower.equals("suggested accounts")
                || lower.equals("suggested users")
                || lower.equals("suggested accounts for you")
                || lower.equals("konten, die dir gefallen könnten");
    }

    private static void style(View root, ScreenInfo screen) {
        try {
            Context context = root.getContext();
            int rootWidth = root.getWidth();
            int rootHeight = root.getHeight();
            if (rootWidth <= 0 || rootHeight <= 0) return;

            int background = opaque(ThemeEngine.backgroundColor(context));
            int surface = opaque(ThemeEngine.surfaceColor(context));
            int primary = ThemeEngine.textColor(context);
            int secondary = ThemeEngine.secondaryTextColor(context);

            ArrayList<ViewGroup> rows = new ArrayList<>();
            ArrayList<TextView> suggested = new ArrayList<>();
            IdentityHashMap<ViewGroup, Integer> ancestorHits = new IdentityHashMap<>();
            int lightSurfaces = 0;

            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.addLast(new Node(root, 0));
            int visited = 0;
            while (!queue.isEmpty() && visited++ < MAX_NODES) {
                Node node = queue.removeFirst();
                View view = node.view;
                if (view == null || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
                int[] xy = location(view);
                if (xy != null) {
                    int bottom = xy[1] + Math.max(0, view.getHeight());
                    if (bottom < 0 || xy[1] > rootHeight) continue;
                }

                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    CharSequence value = tv.getText();
                    String raw = value == null ? "" : value.toString().trim();
                    boolean header = isSuggestedLabel(raw);
                    float sp = textSizeSp(tv);
                    tv.setTextColor(header || sp >= 14.5f ? primary : secondary);
                    tv.setHintTextColor(secondary);
                    if (header) suggested.add(tv);
                }

                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    if (group != root && isRow(group, rootWidth, rootHeight, screen.titleBottom)
                            && !parentIsRow(group, rootWidth, rootHeight, screen.titleBottom)) {
                        group.setBackgroundColor(surface);
                        rows.add(group);
                        bumpAncestors(group, root, ancestorHits);
                    }

                    if (screen.kind == SCREEN_ACTIVITY && group != root
                            && group.getWidth() >= rootWidth * 0.72f
                            && group.getHeight() >= dp(context, 52)) {
                        int[] groupXy = location(group);
                        if (groupXy != null
                                && groupXy[1] + group.getHeight() > Math.max(screen.titleBottom, rootHeight * 0.18f)
                                && groupXy[1] < rootHeight * 1.02f
                                && isLightBackground(group.getBackground())) {
                            group.setBackgroundColor(background);
                            lightSurfaces++;
                        }
                    }

                    if (node.depth < 34) {
                        for (int i = 0; i < group.getChildCount(); i++) {
                            queue.addLast(new Node(group.getChildAt(i), node.depth + 1));
                        }
                    }
                }
            }

            for (TextView label : suggested) {
                View current = label;
                for (int i = 0; i < 6; i++) {
                    ViewParent parent = current.getParent();
                    if (!(parent instanceof ViewGroup)) break;
                    ViewGroup group = (ViewGroup) parent;
                    if (group.getWidth() >= rootWidth * 0.62f) {
                        group.setBackgroundColor(background);
                        ancestorHits.put(group, Math.max(ancestorHits.containsKey(group)
                                ? ancestorHits.get(group) : 0, 3));
                    }
                    current = group;
                    if (group == root) break;
                }
            }

            int sectionCount = 0;
            if (screen.kind == SCREEN_ACTIVITY) {
                for (Map.Entry<ViewGroup, Integer> entry : ancestorHits.entrySet()) {
                    ViewGroup group = entry.getKey();
                    int hits = entry.getValue() == null ? 0 : entry.getValue();
                    if (group == null || group == root || hits < 2) continue;
                    if (group.getWidth() < rootWidth * 0.68f || group.getHeight() < dp(context, 72)) continue;
                    int[] groupXy = location(group);
                    if (groupXy == null) continue;
                    int bottom = groupXy[1] + group.getHeight();
                    if (bottom <= Math.max(screen.titleBottom, rootHeight * 0.16f)
                            || groupXy[1] >= rootHeight) continue;
                    group.setBackgroundColor(background);
                    sectionCount++;
                }
            }

            if (LOG_BUDGET.getAndDecrement() > 0) {
                final int k = screen.kind;
                final int r = rows.size();
                final int s = sectionCount;
                final int l = lightSurfaces;
                Logger.printInfo(() -> "[BlueIT Dynamic List V3] kind=" + k
                        + " rows=" + r + " ancestorSections=" + s + " lightCleared=" + l);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void bumpAncestors(View child, View root, IdentityHashMap<ViewGroup, Integer> hits) {
        try {
            ViewParent parent = child.getParent();
            int depth = 0;
            while (parent instanceof ViewGroup && depth++ < 12) {
                ViewGroup group = (ViewGroup) parent;
                if (group == root) break;
                Integer value = hits.get(group);
                hits.put(group, value == null ? 1 : value + 1);
                parent = group.getParent();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isRow(ViewGroup group, int rootWidth, int rootHeight, int titleBottom) {
        try {
            int width = group.getWidth();
            int height = group.getHeight();
            if (width < rootWidth * 0.68f || width > rootWidth * 1.06f) return false;
            if (height < dp(group.getContext(), 44) || height > dp(group.getContext(), 154)) return false;
            int[] xy = location(group);
            if (xy == null) return false;
            int minTop = Math.max(titleBottom - dp(group.getContext(), 8), Math.round(rootHeight * 0.08f));
            if (xy[1] < minTop || xy[1] > rootHeight * 1.01f) return false;
            int texts = countTextViews(group, 4, 9);
            return texts >= 1 && texts <= 8;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean parentIsRow(ViewGroup group, int rootWidth, int rootHeight, int titleBottom) {
        try {
            ViewParent parent = group.getParent();
            return parent instanceof ViewGroup
                    && isRow((ViewGroup) parent, rootWidth, rootHeight, titleBottom);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int countTextViews(View root, int depth, int max) {
        if (root == null || depth < 0 || max <= 0) return 0;
        int count = 0;
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && !text.toString().trim().isEmpty()) count = 1;
        }
        if (count >= max || !(root instanceof ViewGroup) || depth == 0) return count;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount() && count < max; i++) {
            count += countTextViews(group.getChildAt(i), depth - 1, max - count);
        }
        return count;
    }

    private static boolean isLightBackground(Drawable drawable) {
        try {
            if (!(drawable instanceof ColorDrawable)) return false;
            int color = ((ColorDrawable) drawable).getColor();
            if (Color.alpha(color) < 100) return false;
            double r = Color.red(color) / 255.0;
            double g = Color.green(color) / 255.0;
            double b = Color.blue(color) / 255.0;
            return (0.2126 * r + 0.7152 * g + 0.0722 * b) >= 0.72;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float textSizeSp(TextView view) {
        try {
            float density = view.getResources().getDisplayMetrics().scaledDensity;
            return density > 0f ? view.getTextSize() / density : 16f;
        } catch (Throwable ignored) {
            return 16f;
        }
    }

    private static int[] location(View view) {
        try {
            int[] xy = new int[2];
            view.getLocationOnScreen(xy);
            return xy;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int opaque(int color) {
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, int value) {
        try {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static final class Guard implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        WeakReference<Activity> activityRef;
        final View root;

        Guard(Activity activity, View root) {
            this.activityRef = new WeakReference<>(activity);
            this.root = root;
        }

        void attach() {
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnPreDrawListener(this);
                root.addOnAttachStateChangeListener(this);
            } catch (Throwable ignored) {
            }
        }

        void detach() {
            try {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                root.removeOnAttachStateChangeListener(this);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public boolean onPreDraw() {
            try {
                Activity activity = activityRef.get();
                if (activity == null || activity.isFinishing() || !root.isAttachedToWindow()) {
                    detachAndForget();
                    return true;
                }
                if (!themeActive(root)) return true;
                ScreenInfo screen = detectScreen(root);
                if (screen.kind != SCREEN_NONE) style(root, screen);
            } catch (Throwable ignored) {
            }
            return true;
        }

        @Override public void onViewAttachedToWindow(View v) { }
        @Override public void onViewDetachedFromWindow(View v) { detachAndForget(); }

        private void detachAndForget() {
            detach();
            synchronized (GUARDS) {
                Guard current = GUARDS.get(root);
                if (current == this) GUARDS.remove(root);
            }
        }
    }

    private static final class ScreenInfo {
        final int kind;
        final int titleBottom;
        ScreenInfo(int kind, int titleBottom) {
            this.kind = kind;
            this.titleBottom = titleBottom;
        }
    }

    private static final class Node {
        final View view;
        final int depth;
        Node(View view, int depth) {
            this.view = view;
            this.depth = depth;
        }
    }
}
