package app.morphe.extension.tiktok.download;

import android.os.Looper;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.feed.model.VideoUrlModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowToast;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class DownloadsRuntimeTest {
    private String originalSource, originalQuality;
    private boolean originalDebug, originalToast;

    public static class VideoModel extends Video {
        UrlModel download;
        VideoUrlModel h264, play;
        @Override public UrlModel getDownloadNoWatermarkAddr() { return download; }
        @Override public void setDownloadNoWatermarkAddr(UrlModel model) { download = model; }
        @Override public VideoUrlModel getH264PlayAddr() { return h264; }
        @Override public VideoUrlModel getPlayAddr() { return play; }
    }
    private static class Source extends VideoUrlModel {
        private final List<String> urls;
        Source(String url) { urls = url == null ? Collections.emptyList() : Collections.singletonList(url); }
        @Override public List<String> getUrlList() { return urls; }
        @Override public String getUri() { return "test"; }
        @Override public String getUrlKey() { return "test"; }
        @Override public long getSize() { return 1; }
    }
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        originalSource = Settings.DOWNLOAD_VIDEO_SOURCE.get();
        originalQuality = Settings.DOWNLOAD_VIDEO_QUALITY.get();
        originalDebug = BaseSettings.DEBUG.get();
        originalToast = BaseSettings.DEBUG_TOAST_ON_ERROR.get();
        Settings.DOWNLOAD_VIDEO_QUALITY.save("auto");
        BaseSettings.DEBUG.save(true);
        BaseSettings.DEBUG_TOAST_ON_ERROR.save(true);
        ShadowToast.reset();
    }
    @After public void tearDown() {
        Settings.DOWNLOAD_VIDEO_SOURCE.save(originalSource);
        Settings.DOWNLOAD_VIDEO_QUALITY.save(originalQuality);
        BaseSettings.DEBUG.save(originalDebug);
        BaseSettings.DEBUG_TOAST_ON_ERROR.save(originalToast);
    }
    @Test public void selectsTheNativeGettersWithoutReadingLegacyBackingFields() {
        VideoModel video = new VideoModel();
        video.download = new Source("https://test/original");
        video.h264 = new Source("https://test/h264");
        video.play = new Source("https://test/play");
        // Inherited stub fields remain null, like the removed 47.1.3 fields.
        Settings.DOWNLOAD_VIDEO_SOURCE.save("h264");
        DownloadsPatch.patchVideoObject(video);
        assertSame(video.h264, video.download);
        Settings.DOWNLOAD_VIDEO_SOURCE.save("play");
        DownloadsPatch.patchVideoObject(video);
        assertSame(video.play, video.download);
    }
    @Test public void autoPreservesOriginalAndMissingRequestedSourceFallsBack() {
        VideoModel video = new VideoModel();
        UrlModel original = new Source("https://test/original");
        video.download = original;
        Settings.DOWNLOAD_VIDEO_SOURCE.save("auto");
        DownloadsPatch.patchVideoObject(video);
        assertSame(original, video.download);
        Settings.DOWNLOAD_VIDEO_SOURCE.save("h264");
        DownloadsPatch.patchVideoObject(video);
        assertSame(original, video.download);
        video.download = null;
        video.play = new Source("https://test/play");
        DownloadsPatch.patchVideoObject(video);
        assertSame(video.play, video.download);
    }
    @Test public void repeatedBackgroundFailuresNeverQueueErrorToasts() throws Exception {
        Video video = new VideoModel() {
            @Override public UrlModel getDownloadNoWatermarkAddr() { throw new NoSuchFieldError("native model changed"); }
        };
        Thread worker = new Thread(() -> {
            for (int i = 0; i < 500; i++) DownloadsPatch.patchVideoObject(video);
        });
        worker.start();
        worker.join();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, ShadowToast.shownToastCount());
    }
}
