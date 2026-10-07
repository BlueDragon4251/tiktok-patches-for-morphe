package app.morphe.extension.shared;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicReference;
import app.morphe.extension.shared.settings.preference.SharedPrefCategory;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class StartupContextTest {
    @Test public void applicationAttachPrimesContextBeforeBackgroundPreferenceInitialization() throws Exception {
        Context nativeBase = RuntimeEnvironment.getApplication();
        nativeBase.getSharedPreferences("startup-race", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit();
        Utils.context = null;
        Utils.primeContext(nativeBase);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<SharedPrefCategory> category = new AtomicReference<>();
        Thread startup = new Thread(() -> {
            try { category.set(new SharedPrefCategory("startup-race")); }
            catch (Throwable error) { failure.set(error); }
        });
        startup.start(); startup.join();
        assertNull(failure.get());
        assertTrue(category.get().getBoolean("enabled", false));
        Context first = Utils.getContext();
        Utils.primeContext(null);
        Utils.primeContext(new android.content.ContextWrapper(nativeBase));
        assertSame(first, Utils.getContext());
    }
}
