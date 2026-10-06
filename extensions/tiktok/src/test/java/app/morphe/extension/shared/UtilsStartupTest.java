package app.morphe.extension.shared;

import android.content.Context;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import app.morphe.extension.shared.settings.preference.LogBufferManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class UtilsStartupTest {
    @Test public void concurrentEarlyReadsLogOnceAndRecoverWhenContextArrives() throws Exception {
        Field contextField = Utils.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Field loggedField = Utils.class.getDeclaredField("missingContextLogged");
        loggedField.setAccessible(true);
        contextField.set(null, null);
        ((AtomicBoolean) loggedField.get(null)).set(false);
        java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<Context>> reads = new java.util.ArrayList<>();
            for (int i = 0; i < 160; i++) reads.add(workers.submit(Utils::getContext));
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            for (java.util.concurrent.Future<Context> read : reads) assertNull(read.get());
            String events = LogBufferManager.snapshotForCrash(100000);
            String message = "Context is not set by extension hook yet";
            assertEquals(1, events.split(message, -1).length - 1);
            Context context = RuntimeEnvironment.getApplication();
            Utils.setContext(context);
            assertSame(context, Utils.getContext());
        } finally {
            workers.shutdownNow();
            Utils.setContext(RuntimeEnvironment.getApplication());
        }
    }
}
