package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import app.morphe.extension.tiktok.settings.Settings;

/** A typed replacement for the native watermark draw; no native scratch value is changed. */
@SuppressWarnings("unused")
public final class CommentWatermarkRenderer {
    private CommentWatermarkRenderer() { }

    public static void draw(Canvas canvas, Bitmap bitmap, float x, float y, Paint paint) {
        if (!Settings.DOWNLOAD_WATERMARK.get()) canvas.drawBitmap(bitmap, x, y, paint);
    }
}
