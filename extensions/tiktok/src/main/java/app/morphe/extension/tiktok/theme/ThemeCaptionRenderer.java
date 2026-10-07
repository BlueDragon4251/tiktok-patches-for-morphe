package app.morphe.extension.tiktok.theme;

import android.graphics.Color;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import app.morphe.extension.shared.Logger;

/** Called immediately before the reviewed native caption View draws its actual Layout. */
public final class ThemeCaptionRenderer {
    private static final WeakHashMap<Layout, Fill> FILLS = new WeakHashMap<>();
    private static final AtomicInteger LOG_BUDGET = new AtomicInteger(8);
    private ThemeCaptionRenderer() { }
    public static void beforeDraw(View view, Layout layout) {
        if (view == null || layout == null) return;
        try {
            TextPaint paint = layout.getPaint();
            Fill fill = FILLS.get(layout);
            boolean active = !"default".equals(ThemeStateStore.currentPreset(view.getContext()));
            if (!active) {
                if (fill != null) {
                    if (paint.getColor() == fill.applied) paint.setColor(fill.original);
                    if (layout.getText() instanceof Spannable) ((Spannable) layout.getText()).removeSpan(fill.contrast);
                    FILLS.remove(layout);
                }
                return;
            }
            if (fill == null) {
                fill = new Fill(paint.getColor());
                FILLS.put(layout, fill);
                if (LOG_BUDGET.getAndDecrement() > 0) {
                    final String state = "[BlueIT Caption Draw v1] chars=" + layout.getText().length()
                        + " layout=" + layout.getWidth() + "x" + layout.getHeight()
                        + " paint=" + Integer.toHexString(paint.getColor()) + " visible=" + view.getVisibility()
                        + " alpha=" + view.getAlpha() + " class=" + view.getClass().getName();
                    Logger.printInfo(() -> state);
                }
            }
            if (paint.getColor() != fill.applied) fill.original = paint.getColor();
            if (ThemeViewColors.neutral(fill.original)) {
                paint.setColor(Color.WHITE);
                fill.applied = Color.WHITE;
            }
            // The native Layout can carry TextAppearanceSpan/custom CharacterStyle colors that
            // override TextView.setTextColor. Append an appearance-only neutral contrast span.
            // Preserve the same text, all native spans and Layout; never call setText/requestLayout.
            if (layout.getText() instanceof Spannable && layout.getText().length() > 0) {
                Spannable text = (Spannable) layout.getText();
                if (text.getSpanStart(fill.contrast) != 0 || text.getSpanEnd(fill.contrast) != text.length())
                    text.setSpan(fill.contrast, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Throwable error) {
            if (LOG_BUDGET.getAndDecrement() > 0) Logger.printInfo(() -> "BlueIT caption draw failed", new Exception(error));
        }
    }
    private static final class Fill {
        int original, applied;
        final Contrast contrast = new Contrast();
        Fill(int color) { original = applied = color; }
    }
    private static final class Contrast extends CharacterStyle implements UpdateAppearance {
        @Override public void updateDrawState(TextPaint paint) {
            if (ThemeViewColors.neutral(paint.getColor())) paint.setColor(Color.WHITE);
        }
    }
}
