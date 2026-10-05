package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class CommentWatermarkRendererTest {
    private boolean originalSetting;

    private static final class RecordingCanvas extends Canvas {
        int calls;
        Bitmap bitmap;
        Paint paint;
        float x;
        float y;
        @Override public void drawBitmap(Bitmap bitmap, float x, float y, Paint paint) {
            calls++;
            this.bitmap = bitmap;
            this.paint = paint;
            this.x = x;
            this.y = y;
        }
    }

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        originalSetting = Settings.DOWNLOAD_WATERMARK.get();
    }

    @After public void tearDown() {
        Settings.DOWNLOAD_WATERMARK.save(originalSetting);
    }

    @Test public void disabledSettingPreservesTheEntireNativeDraw() {
        Settings.DOWNLOAD_WATERMARK.save(false);
        RecordingCanvas canvas = new RecordingCanvas();
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        CommentWatermarkRenderer.draw(canvas, bitmap, 3.5f, -2.25f, paint);
        assertEquals(1, canvas.calls);
        assertSame(bitmap, canvas.bitmap);
        assertSame(paint, canvas.paint);
        assertEquals(3.5f, canvas.x, 0f);
        assertEquals(-2.25f, canvas.y, 0f);
        assertFalse(bitmap.isRecycled());
    }

    @Test public void enabledSettingSuppressesOnlyTheDrawAndRestoresImmediately() {
        RecordingCanvas canvas = new RecordingCanvas();
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Settings.DOWNLOAD_WATERMARK.save(true);
        CommentWatermarkRenderer.draw(canvas, bitmap, 0f, 0f, null);
        assertEquals(0, canvas.calls);
        assertFalse(bitmap.isRecycled());
        Settings.DOWNLOAD_WATERMARK.save(false);
        CommentWatermarkRenderer.draw(canvas, bitmap, 0f, 0f, null);
        assertEquals(1, canvas.calls);
    }
}
