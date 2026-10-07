package app.morphe.extension.tiktok.theme;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import app.morphe.extension.shared.Logger;

/** Roots supplied by verified native lifecycle hooks. No labels, dimensions or class-name guesses. */
public final class ThemeNativeTargets {
    private static final int PROFILE = 1, SIDEBAR = 2, SEARCH = 3, CHAT = 4, NAV = 5, NAV_DIVIDER = 6, INBOX_ROW = 7, FEED_DESCRIPTION = 8;
    private static final Map<View, Target> TARGETS = new WeakHashMap<>();
    private static final Map<ViewGroup, Target> PAGER_TEXTS = new WeakHashMap<>();
    private ThemeNativeTargets() {}

    public static void profilePage(View view) { register(view, PROFILE); }
    public static void sidebar(View view) { register(view, SIDEBAR); }
    public static void search(View view) { register(view, SEARCH); }
    public static void chat(View view) { register(view, CHAT); }
    public static void navigation(View view) { register(view, NAV); }
    public static void navigationDivider(View view) { register(view, NAV_DIVIDER); }
    public static void feedDescription(TextView view) { register(view, FEED_DESCRIPTION); }
    static void inboxRow(View view) { register(view, INBOX_ROW); }

    /** Native pager entry after computeScroll, before any child is drawn in this frame. */
    public static void beforePagerDraw(ViewGroup pager) {
        if (pager == null) return;
        try {
            Target textTarget = PAGER_TEXTS.get(pager);
            if (!active(pager)) {
                if (textTarget != null) textTarget.restoreTexts();
            } else {
                if (textTarget == null) {
                    textTarget = new Target(pager, 0);
                    PAGER_TEXTS.put(pager, textTarget);
                }
                long now = SystemClock.uptimeMillis();
                if (now - textTarget.lastStyleMs >= 100 || textTarget.lastStyleMs < 0
                        || !textTarget.preset.equals(ThemeStateStore.currentPreset(pager.getContext()))) {
                    textTarget.palette(pager);
                    repairPagerTextContrast(pager, textTarget);
                    textTarget.lastStyleMs = now;
                }
                // Existing captions are cheap to check on every draw, including a recycled light
                // card/backdrop change. Only discovery of new children uses the bounded scan.
                for (Map.Entry<TextView, TextFill> entry : textTarget.texts.entrySet()) {
                    TextView text = entry.getKey();
                    TextFill fill = entry.getValue();
                    if (!text.isShown() || !contains(pager, text)) continue;
                    if (hasDarkFlatBackdrop(text)) styleText(text, textTarget);
                    else if (text.getTextColors() == fill.applied) {
                        text.setTextColor(fill.original);
                        fill.restoreSpans(text);
                        fill.applied = fill.original;
                    }
                }
            }
            for (Map.Entry<View, Target> entry : TARGETS.entrySet()) {
                View profile = entry.getKey();
                if (entry.getValue().kind == PROFILE && profile != null
                        && profile.isAttachedToWindow() && contains(pager, profile)) {
                    entry.getValue().apply(false);
                }
            }
        } catch (Throwable ignored) { }
    }

    /** Fix dark native captions over an actual dark flat fill, never over guessed media colors. */
    private static void repairPagerTextContrast(ViewGroup pager, Target target) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(pager);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 1200) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || isOwned(view)) continue;
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                TextFill previous = target.texts.get(text);
                if (previous != null && text.getTextColors() == previous.applied
                        && !hasDarkFlatBackdrop(text)) {
                    text.setTextColor(previous.original);
                    previous.restoreSpans(text);
                    previous.applied = previous.original;
                }
                int current = text.getCurrentTextColor();
                int r = Color.red(current), g = Color.green(current), b = Color.blue(current);
                if (Color.alpha(current) > 0 && Math.max(r, Math.max(g, b)) < 90
                        && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 34
                        && hasDarkFlatBackdrop(text)) styleText(text, target);
                TextFill fill = target.texts.get(text);
                if (fill != null && hasDarkFlatBackdrop(text)) fill.mapSpans(text, target.primary);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
            }
        }
    }

    private static boolean hasDarkFlatBackdrop(View view) {
        View current = view;
        for (int i = 0; i < 16; i++) {
            Drawable fill = current.getBackground();
            Integer flat = ThemeViewColors.flatColor(fill);
            if (flat != null) {
                if (Color.alpha(flat) == 255) {
                    return Math.max(Color.red(flat), Math.max(Color.green(flat), Color.blue(flat))) < 90;
                }
                // A translucent black shadow over a native dark page does not stop the search.
                if (Color.alpha(flat) != 0 && !ThemeViewColors.neutral(flat)) return false;
            } else if (fill instanceof GradientDrawable && android.os.Build.VERSION.SDK_INT >= 24) {
                int[] stops = ((GradientDrawable) fill).getColors();
                if (stops == null) return false;
                for (int stop : stops) if (Color.alpha(stop) != 0
                        && Math.max(Color.red(stop), Math.max(Color.green(stop), Color.blue(stop))) >= 90) return false;
                // Native black-to-transparent caption scrims reveal the backing page/media.
            } else if (fill != null) return false;
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return false;
            current = (View) parent;
        }
        return false;
    }

    private static void register(View view, int kind) {
        if (view == null) return;
        if (ThemeUiThread.defer(view, () -> register(view, kind))) return;
        try {
            Target target = TARGETS.get(view);
            if (target == null) {
                target = new Target(view, kind);
                TARGETS.put(view, target);
                if (kind != INBOX_ROW) {
                    view.addOnAttachStateChangeListener(target);
                    target.attach();
                }
                final int role = kind;
                Logger.printInfo(() -> "[BlueIT Native Target v3] role=" + role + " view=" + view.getClass().getName());
            }
            target.apply(true);
        } catch (Throwable error) { logFailure(error); }
    }

    private static final java.util.concurrent.atomic.AtomicInteger STATE_LOG_BUDGET = new java.util.concurrent.atomic.AtomicInteger(24);
    private static final java.util.concurrent.atomic.AtomicBoolean FAILURE_LOGGED = new java.util.concurrent.atomic.AtomicBoolean();
    private static void logFailure(Throwable error) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) Logger.printInfo(() -> "BlueIT native theme operation failed", new Exception(error));
    }

    static boolean isOwned(View view) {
        Target target = TARGETS.get(view);
        return target != null;
    }

    static boolean hasChat(View root) {
        for (Map.Entry<View, Target> entry : TARGETS.entrySet()) {
            View view = entry.getKey();
            if (entry.getValue().kind == CHAT && view.isAttachedToWindow()
                    && view.getRootView() == root && view.isShown()) return true;
        }
        return false;
    }

    private static boolean active(View view) {
        return !"default".equals(ThemeStateStore.currentPreset(view.getContext()));
    }

    private static void color(View view, int value, Target target) {
        Drawable current = view.getBackground();
        Fill fill = target.fills.get(view);
        Integer flat = ThemeViewColors.flatColor(current);
        boolean changedInPlace = fill != null && current == fill.applied && flat != null
                && flat != fill.appliedColor;
        if (fill == null || current != fill.applied || changedInPlace || view.getBackgroundTintList() != null) {
            Drawable nativeValue = current;
            if (changedInPlace && current.getConstantState() != null) {
                nativeValue = current.getConstantState().newDrawable().mutate();
            }
            fill = new Fill(nativeValue, view.getBackgroundTintList());
            target.fills.put(view, fill);
        }
        if (ThemeViewColors.flatColor(current) == null || ThemeViewColors.flatColor(current) != value) {
            // Do not mutate TikTok's ColorDrawable: keep the native snapshot intact for Default.
            Drawable replacement = new ColorDrawable(value);
            if (current instanceof GradientDrawable && ThemeViewColors.flatColor(current) != null
                    && current.getConstantState() != null) {
                GradientDrawable shape = (GradientDrawable) current.getConstantState().newDrawable().mutate();
                shape.setColor(value);
                replacement = shape;
            }
            view.setBackground(replacement);
        }
        if (view.getBackgroundTintList() != null) view.setBackgroundTintList(null);
        fill.applied = view.getBackground();
        fill.appliedColor = value;
    }

    private static final class Fill {
        final Drawable original;
        final ColorStateList tint;
        Drawable applied;
        int appliedColor;
        Fill(Drawable original, ColorStateList tint) { this.original = original; this.tint = tint; }
    }

    private static boolean pageFill(int color, Target target) {
        if (Color.alpha(color) == 0) return false;
        int rgb = color & 0xffffff;
        return rgb == (target.background & 0xffffff)
                || rgb == (target.surface & 0xffffff)
                || rgb == 0 || rgb == 0x121212 || rgb == 0x161823
                || rgb == 0x1e1e1e || rgb == 0x252525 || rgb == 0xffffff;
    }

    /** Map only flat page fills within a native-owned subtree; media and Compose palettes stay native. */
    private static void page(View root, boolean overlay, Target target) {
        Drawable rootFill = root.getBackground();
        // Profile roots can carry native media/functional fills; preserve those exactly.
        Integer rootColor = ThemeViewColors.flatColor(rootFill);
        if (target.kind != INBOX_ROW && (target.kind != PROFILE || rootFill == null
                || rootColor != null && pageFill(rootColor, target))) {
            color(root, overlay ? target.surface
                    : target.background, target);
        }
        if (!(root instanceof ViewGroup)) return;
        ArrayDeque<View> queue = new ArrayDeque<>();
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) queue.add(group.getChildAt(i));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 1800) {
            View view = queue.removeFirst();
            if (isOwned(view) || view.getVisibility() != View.VISIBLE) continue;
            if (view instanceof ImageView) {
                styleIcon((ImageView) view, target);
                continue;
            }
            ThemeViewColors.bubble(view, target.background);
            Drawable background = view.getBackground();
            Integer flat = ThemeViewColors.flatColor(background);
            if (flat != null && pageFill(flat, target)) {
                color(view, overlay ? Color.TRANSPARENT : target.background, target);
            }
            if (view instanceof TextView) styleText((TextView) view, target);
            if (!(view instanceof ViewGroup)) continue;
            ViewGroup child = (ViewGroup) view;
            for (int i = 0; i < child.getChildCount(); i++) queue.add(child.getChildAt(i));
        }
    }

    private static void styleText(TextView view, Target target) {
        ColorStateList current = view.getTextColors();
        TextFill fill = target.texts.get(view);
        if (fill == null || current != fill.applied) {
            fill = new TextFill(current);
            target.texts.put(view, fill);
        }
        int nativeColor = fill.original.getDefaultColor();
        int r = Color.red(nativeColor), g = Color.green(nativeColor), b = Color.blue(nativeColor);
        // Functional colors retain their native behavior.
        if (Color.alpha(nativeColor) == 0
                || Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 34) return;
        boolean secondary = Color.alpha(nativeColor) < 200
                || Math.max(r, Math.max(g, b)) > 60 && Math.min(r, Math.min(g, b)) < 220;
        int mapped = secondary ? target.secondary : target.primary;
        if (fill.original.isStateful()) {
            if (fill.applied == null || fill.primary != target.primary || fill.secondary != target.secondary) {
                fill.applied = ThemeViewColors.stateColors(fill.original, target.primary, target.secondary);
                fill.primary = target.primary; fill.secondary = target.secondary;
                view.setTextColor(fill.applied);
            }
            mapped = view.getCurrentTextColor();
        } else {
            if (view.getCurrentTextColor() != mapped) view.setTextColor(mapped);
            fill.applied = view.getTextColors();
        }
        fill.mapSpans(view, mapped);
    }

    private static void styleIcon(ImageView view, Target target) {
        IconFill fill = target.icons.get(view);
        if (fill == null || view.getImageTintList() != fill.applied || view.getDrawable() != fill.drawable
                || fill.nativeTint != null && fill.nativeTint.get(fill.drawable) != fill.appliedNative) {
            if (!ThemeViewColors.icon(view)) return;
            ColorStateList original = fill != null && view.getImageTintList() == fill.applied
                    ? fill.original : view.getImageTintList();
            fill = new IconFill(view, original);
            target.icons.put(view, fill);
        }
        if (fill.applied == null || fill.primary != target.primary) {
            fill.primary = target.primary;
            ColorStateList source = fill.originalNative != null ? fill.originalNative : fill.original;
            fill.applied = source != null && source.isStateful()
                    ? ThemeViewColors.stateColors(source, target.primary, target.primary)
                    : ColorStateList.valueOf(target.primary);
            view.setImageTintList(fill.applied);
            if (fill.nativeTint != null) {
                fill.appliedNative = fill.applied;
                fill.nativeTint.set(view, fill.appliedNative);
            }
        }
    }

    private static final class IconFill {
        final Drawable drawable;
        final ColorStateList original;
        ColorStateList applied, appliedNative;
        int primary;
        final ThemeViewColors.NativeTint nativeTint;
        final ColorStateList originalNative;
        IconFill(ImageView view, ColorStateList original) {
            drawable = view.getDrawable(); this.original = original;
            nativeTint = ThemeViewColors.NativeTint.find(view);
            originalNative = nativeTint == null ? null : nativeTint.get(drawable);
        }
    }

    private static final class TextFill {
        final ColorStateList original;
        ColorStateList applied;
        int primary, secondary;
        CharSequence originalText, appliedText;
        int spanColor;
        TextFill(ColorStateList original) { this.original = original; }
        void restoreSpans(TextView view) {
            if (appliedText != null && view.getText() == appliedText) view.setText(originalText);
            appliedText = null;
        }
        void mapSpans(TextView view, int color) {
            CharSequence text = view.getText();
            if (text == appliedText && spanColor == color) return;
            if (text != appliedText) originalText = text;
            if (!(originalText instanceof Spanned)) return;
            Spanned spans = (Spanned) originalText;
            SpannableString mapped = null;
            for (ForegroundColorSpan span : spans.getSpans(0, spans.length(), ForegroundColorSpan.class)) {
                if (!ThemeViewColors.neutral(span.getForegroundColor())) continue;
                if (mapped == null) mapped = new SpannableString(originalText);
                mapped.removeSpan(span);
                mapped.setSpan(new ForegroundColorSpan(color), spans.getSpanStart(span),
                        spans.getSpanEnd(span), spans.getSpanFlags(span));
            }
            if (mapped != null) {
                view.setText(mapped);
                appliedText = view.getText();
                spanColor = color;
            }
        }
    }

    private static View visibleSidebar(View main) {
        int width = main.getRootView().getWidth();
        int[] origin = new int[2];
        main.getRootView().getLocationOnScreen(origin);
        for (Map.Entry<View, Target> entry : TARGETS.entrySet()) {
            View sidebar = entry.getKey();
            if (entry.getValue().kind != SIDEBAR || !sidebar.isAttachedToWindow()
                    || sidebar.getRootView() != main.getRootView() || !sidebar.isShown()) continue;
            int[] location = new int[2];
            sidebar.getLocationOnScreen(location);
            if (location[0] < origin[0] + width && location[0] + sidebar.getWidth() > origin[0]) return sidebar;
        }
        return null;
    }

    private static boolean contains(ViewGroup parent, View child) {
        for (android.view.ViewParent p = child.getParent(); p != null; p = p.getParent()) {
            if (p == parent) return true;
        }
        return false;
    }

    private static final class Target implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnScrollChangedListener {
        // WeakHashMap values must not retain their keys.
        final java.lang.ref.WeakReference<View> reference;
        final int kind;
        ViewTreeObserver observer;
        float correction;
        float lastApplied;
        final Map<View, Fill> fills = new WeakHashMap<>();
        boolean stateLogged;
        long lastStyleMs = -1;
        String preset = "";
        int background, surface, primary, secondary;
        final Map<ImageView, IconFill> icons = new WeakHashMap<>();
        final Map<TextView, TextFill> texts = new WeakHashMap<>();
        final Map<ViewGroup, boolean[]> clips = new WeakHashMap<>();
        Target(View view, int kind) { reference = new java.lang.ref.WeakReference<>(view); this.kind = kind; }
        void attach() {
            View view = reference.get();
            if (view == null) return;
            detach();
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
            if (kind == PROFILE) observer.addOnScrollChangedListener(this);
        }
        void detach() {
            if (observer != null && observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
                observer.removeOnScrollChangedListener(this);
            }
            observer = null;
            restoreClips();
        }
        void restoreClips() {
            for (Map.Entry<ViewGroup, boolean[]> entry : clips.entrySet()) {
                entry.getKey().setClipChildren(entry.getValue()[0]);
                entry.getKey().setClipToPadding(entry.getValue()[1]);
            }
            clips.clear();
        }
        void restoreTexts() {
            for (Map.Entry<TextView, TextFill> entry : texts.entrySet()) {
                if (entry.getKey().getTextColors() == entry.getValue().applied) {
                    entry.getKey().setTextColor(entry.getValue().original);
                    entry.getValue().restoreSpans(entry.getKey());
                }
            }
            texts.clear();
            lastStyleMs = -1;
        }
        void allowProfileUnderDrawer(View profile, View sidebar) {
            // Only the ancestry between the two native lifecycle roots. The shifted page's
            // clipping would otherwise cut off the corrected profile underneath the overlay.
            for (android.view.ViewParent p = profile.getParent(); p instanceof ViewGroup; p = p.getParent()) {
                ViewGroup group = (ViewGroup) p;
                if (!clips.containsKey(group)) {
                    clips.put(group, new boolean[]{group.getClipChildren(), group.getClipToPadding()});
                }
                if (group.getClipChildren()) group.setClipChildren(false);
                if (group.getClipToPadding()) group.setClipToPadding(false);
                if (contains(group, sidebar)) break;
            }
        }
        void palette(View view) {
            preset = ThemeStateStore.currentPreset(view.getContext());
            background = ThemeEngine.backgroundColor(view.getContext());
            surface = ThemeEngine.surfaceColor(view.getContext());
            primary = ThemeEngine.textColor(view.getContext());
            secondary = ThemeEngine.secondaryTextColor(view.getContext());
        }
        void apply(boolean force) {
            View view = reference.get();
            if (view == null) return;
            boolean enabled = active(view);
            if (kind == FEED_DESCRIPTION && enabled && view instanceof TextView) {
                // This is the native measurement TextView. The visible title owns a separate
                // Layout; ThemeCaptionRenderer styles it at draw time without resetting text.
                return;
            } else if (kind == PROFILE) {
                View sidebar = enabled ? visibleSidebar(view) : null;
                if (sidebar != null) allowProfileUnderDrawer(view, sidebar);
                else restoreClips();
                // Undo mathematically, never write a displaced intermediate frame to the View.
                int[] position = new int[2], origin = new int[2];
                view.getLocationOnScreen(position);
                view.getRootView().getLocationOnScreen(origin);
                float current = view.getTranslationX();
                float ownCorrection = current == lastApplied ? correction : 0;
                float nativeTranslation = current - ownCorrection;
                float baseX = position[0] - origin[0] - ownCorrection;
                float next = sidebar != null ? Math.max(0, -baseX) : 0;
                float applied = nativeTranslation + next;
                if (current != applied) view.setTranslationX(applied);
                // A native direct translation neutralized at zero needs no ancestor undo later.
                correction = applied == 0 ? 0 : next;
                lastApplied = applied;
                if (enabled) restyle(view, false, force);
            } else if (enabled && kind == SIDEBAR) restyle(view, true, force);
            else if (enabled && kind == SEARCH) restyle(view, false, force);
            else if (enabled && kind == NAV) restyle(view, true, force);
            else if (enabled && kind == INBOX_ROW) restyle(view, true, force);
            else if (enabled && kind == NAV_DIVIDER) color(view, ThemeEngine.dividerColor(view.getContext()), this);
            if (!enabled && !fills.isEmpty()) {
                for (Map.Entry<View, Fill> entry : fills.entrySet()) {
                    View target = entry.getKey();
                    Fill fill = entry.getValue();
                    if (target.getBackground() == fill.applied) {
                        target.setBackground(fill.original);
                        target.setBackgroundTintList(fill.tint);
                    }
                }
                fills.clear();
            }
            if (!enabled) {
                ThemeViewColors.restoreBubbles(view.getRootView());
                for (Map.Entry<ImageView, IconFill> entry : icons.entrySet()) {
                    ImageView icon = entry.getKey();
                    IconFill fill = entry.getValue();
                    if (icon.getDrawable() == fill.drawable) {
                        if (icon.getImageTintList() == fill.applied) icon.setImageTintList(fill.original);
                        if (fill.nativeTint != null && fill.nativeTint.get(fill.drawable) == fill.appliedNative)
                            fill.nativeTint.set(icon, fill.originalNative);
                    }
                }
                icons.clear();
            }
            if (!enabled && !texts.isEmpty()) {
                restoreTexts();
            }
        }
        void restyle(View view, boolean overlay, boolean force) {
            if (!force && !view.isShown()) return;
            long now = SystemClock.uptimeMillis();
            if (!force && now - lastStyleMs < 100 && lastStyleMs >= 0
                    && preset.equals(ThemeStateStore.currentPreset(view.getContext()))) return;
            palette(view);
            page(view, overlay, this);
            if (!stateLogged) {
                stateLogged = true;
                if (STATE_LOG_BUDGET.getAndDecrement() > 0) Logger.printInfo(() -> "[BlueIT Native Styled v3] role=" + kind + " texts=" + texts.size()
                    + " icons=" + icons.size() + " fills=" + fills.size() + " class=" + view.getClass().getName());
            }
            lastStyleMs = now;
        }
        @Override public boolean onPreDraw() { try { apply(false); } catch (Throwable error) { logFailure(error); } return true; }
        @Override public void onScrollChanged() { try { apply(false); } catch (Throwable error) { logFailure(error); } }
        @Override public void onViewAttachedToWindow(View view) { attach(); }
        @Override public void onViewDetachedFromWindow(View view) { detach(); }
    }
}
