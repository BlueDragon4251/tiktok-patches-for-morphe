package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;

/**
 * Third-generation profile drawer underlay guard for TikTok 46.7.3.
 *
 * The previous guards first tried to identify the drawer and only then searched for a shifted
 * sibling. Device logs showed that the drawer heuristics never matched. V3 inverts that logic: while
 * the actual profile page is active, any large visible profile underlay whose real screen X becomes
 * substantially negative is compensated directly. This works whether TikTok moved it by layout,
 * ViewPager/scroll, translation or an ancestor transform and does not require identifying the drawer.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class ThemeProfileOverlayGuardV3 {
    private static final int MAX_NODES = 1800;
    private static final long PROFILE_GRACE_MS = 6000L;
    private static final WeakHashMap<View, Guard> GUARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> ORIGINAL_TRANSLATIONS = new WeakHashMap<>();
    private static final AtomicInteger LOG_BUDGET = new AtomicInteger(30);

    private ThemeProfileOverlayGuardV3() {}

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

    private static boolean profileVisible(View root) {
        try {
            int strong = 0;
            int weak = 0;
            ArrayDeque<Node> queue = new ArrayDeque<>();
            queue.addLast(new Node(root, 0));
            int visited = 0;
            while (!queue.isEmpty() && visited++ < 1100) {
                Node node = queue.removeFirst();
                View view = node.view;
                if (view == null || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) continue;
                if (view instanceof TextView) {
                    CharSequence text = ((TextView) view).getText();
                    if (text != null) {
                        String value = text.toString().trim().toLowerCase(Locale.ROOT);
                        if (value.equals("profil bearbeiten") || value.equals("edit profile")
                                || value.equals("profil teilen") || value.equals("share profile")
                                || value.equals("bio hinzufügen") || value.equals("add bio")) {
                            strong++;
                        }
                        if (value.equals("follower") || value.equals("followers")
                                || value.equals("folge ich") || value.equals("following")
                                || value.equals("likes") || value.equals("gefällt mir")) {
                            weak++;
                        }
                    }
                }
                String marker = className(view) + " " + resourceName(view);
                if (containsAny(marker, "profile_fragment", "profilepage", "profile_page",
                        "user_profile", "mine_profile")) strong++;

                if (strong >= 1 && weak >= 1) return true;
                if (strong >= 2) return true;

                if (view instanceof ViewGroup && node.depth < 32) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        queue.addLast(new Node(group.getChildAt(i), node.depth + 1));
                    }
                }
            }
            return strong >= 1 && weak >= 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static View findShiftedUnderlay(ViewGroup root, int rootWidth, int rootHeight) {
        View best = null;
        int bestScore = Integer.MIN_VALUE;
        int threshold = Math.max(dp(root.getContext(), 10), Math.round(rootWidth * 0.045f));

        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.addLast(new Node(root, 0));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_NODES) {
            Node node = queue.removeFirst();
            View view = node.view;
            if (view == null || view == root || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
                continue;
            }

            int[] xy = location(view);
            if (xy != null) {
                int width = view.getWidth();
                int height = view.getHeight();
                int right = xy[0] + width;
                if (xy[0] <= -threshold
                        && xy[0] > -rootWidth * 0.96f
                        && width >= rootWidth * 0.70f
                        && height >= rootHeight * 0.42f
                        && right > rootWidth * 0.10f) {
                    int texts = countTextViews(view, 5, 20);
                    String marker = className(view) + " " + resourceName(view);
                    int score = 0;
                    score += Math.max(0, 22 - node.depth);
                    score += Math.min(12, texts);
                    if (width >= rootWidth * 0.90f) score += 8;
                    if (height >= rootHeight * 0.70f) score += 8;
                    if (containsAny(marker, "profile", "mine", "user_page", "main_page")) score += 12;
                    if (score > bestScore) {
                        best = view;
                        bestScore = score;
                    }
                }
            }

            if (view instanceof ViewGroup && node.depth < 34) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.addLast(new Node(group.getChildAt(i), node.depth + 1));
                }
            }
        }
        return best;
    }

    private static boolean compensate(View root) {
        try {
            if (!(root instanceof ViewGroup) || root.getWidth() <= 0 || root.getHeight() <= 0) return false;
            int rootWidth = root.getWidth();
            int rootHeight = root.getHeight();
            View target = findShiftedUnderlay((ViewGroup) root, rootWidth, rootHeight);
            if (target == null) return false;

            int[] xy = location(target);
            if (xy == null || xy[0] >= 0) return false;
            synchronized (ORIGINAL_TRANSLATIONS) {
                if (!ORIGINAL_TRANSLATIONS.containsKey(target)) {
                    ORIGINAL_TRANSLATIONS.put(target, target.getTranslationX());
                }
            }
            float correction = -xy[0];
            target.setTranslationX(target.getTranslationX() + correction);

            if (LOG_BUDGET.getAndDecrement() > 0) {
                final int x = xy[0];
                final float delta = correction;
                final String cls = target.getClass().getName();
                final int width = target.getWidth();
                final int height = target.getHeight();
                Logger.printInfo(() -> "[BlueIT Profile Overlay V3] corrected screenX=" + x
                        + " delta=" + delta + " size=" + width + "x" + height + " view=" + cls);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void restoreTracked() {
        synchronized (ORIGINAL_TRANSLATIONS) {
            for (Map.Entry<View, Float> entry : new ArrayList<>(ORIGINAL_TRANSLATIONS.entrySet())) {
                View view = entry.getKey();
                Float value = entry.getValue();
                if (view == null || value == null) continue;
                try {
                    view.setTranslationX(value);
                } catch (Throwable ignored) {
                }
            }
            ORIGINAL_TRANSLATIONS.clear();
        }
    }

    private static int countTextViews(View root, int depth, int max) {
        if (root == null || depth < 0 || max <= 0) return 0;
        int count = 0;
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && !value.toString().trim().isEmpty()) count = 1;
        }
        if (count >= max || !(root instanceof ViewGroup) || depth == 0) return count;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount() && count < max; i++) {
            count += countTextViews(group.getChildAt(i), depth - 1, max - count);
        }
        return count;
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

    private static String className(View view) {
        try {
            String name = view.getClass().getName();
            return name == null ? "" : name.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resourceName(View view) {
        try {
            int id = view.getId();
            if (id == View.NO_ID || id == 0) return "";
            return view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token)) return true;
        }
        return false;
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
        long lastProfileSeenMs;
        boolean profileLogged;
        int noCandidateBudget = 6;

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
            restoreTracked();
        }

        @Override
        public boolean onPreDraw() {
            try {
                Activity activity = activityRef.get();
                if (activity == null || activity.isFinishing() || !root.isAttachedToWindow()) {
                    detachAndForget();
                    return true;
                }

                // Reset our previous frame's correction before measuring TikTok's real layout again.
                // This makes the correction idempotent and avoids accumulating translationX.
                restoreTracked();
                if (!themeActive(root)) return true;

                long now = SystemClock.uptimeMillis();
                if (profileVisible(root)) {
                    lastProfileSeenMs = now;
                    if (!profileLogged) {
                        profileLogged = true;
                        Logger.printInfo(() -> "[BlueIT Profile Overlay V3] profile screen armed");
                    }
                }

                if (lastProfileSeenMs != 0L && now - lastProfileSeenMs <= PROFILE_GRACE_MS) {
                    boolean corrected = compensate(root);
                    if (!corrected && noCandidateBudget-- > 0) {
                        Logger.printInfo(() -> "[BlueIT Profile Overlay V3] profile active; no shifted underlay this frame");
                    }
                }
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

    private static final class Node {
        final View view;
        final int depth;
        Node(View view, int depth) {
            this.view = view;
            this.depth = depth;
        }
    }
}
