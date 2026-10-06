package com.ss.android.ugc.aweme.social.thought.view;
import android.content.Context;
import android.graphics.Color;
import android.widget.FrameLayout;
/** Test double for the native public paint-color API verified in 47.1.3. */
public class SocialThoughtBaseBubbleBackgroundView extends FrameLayout {
    private int fillColor = Color.WHITE;
    public SocialThoughtBaseBubbleBackgroundView(Context context) { super(context); }
    public int getFillColor() { return fillColor; }
    public void setFillColor(int color) { fillColor = color; invalidate(); }
}
