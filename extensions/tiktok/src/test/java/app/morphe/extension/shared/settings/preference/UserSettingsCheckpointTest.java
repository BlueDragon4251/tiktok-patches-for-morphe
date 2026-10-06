package app.morphe.extension.shared.settings.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class UserSettingsCheckpointTest {
    private Context context;
    private SharedPreferences prefs;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences("checkpoint-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        new File(context.getFilesDir(), "blueit-user-settings-checkpoint-v1.json").delete();
        new File(context.getFilesDir(), "blueit-user-settings-checkpoint-v1.json.bak").delete();
    }
    @Test public void settingsAndDiagnosticsSurviveEmptyPreferenceFileAfterCrash() {
        UserSettingsCheckpoint checkpoint = new UserSettingsCheckpoint(context, prefs);
        prefs.edit().putBoolean("morphe_capture_java_crashes", true)
                .putBoolean("hide_captcha_popups", true).putString("blueit_theme_preset", "arctic_blue")
                .putInt("integer", 37).putLong("long", 1234567890123L).putFloat("float", 1.25f)
                .putStringSet("set", Set.of("a", "b")).commit();
        checkpoint.save();
        // Simulate the next process loading an empty preference file, without notifying the old
        // process's listeners (the old process has died).
        SharedPreferences restarted = context.getSharedPreferences("restarted-test", Context.MODE_PRIVATE);
        restarted.edit().clear().commit();
        new UserSettingsCheckpoint(context, restarted);
        assertEquals(prefs.getAll(), restarted.getAll());
        assertTrue(restarted.getBoolean("morphe_capture_java_crashes", false));
    }
    @Test public void frameworkPreferenceEditsAreCheckpointed() {
        new UserSettingsCheckpoint(context, prefs);
        prefs.edit().putBoolean("hide_captcha_popups", true).apply();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        SharedPreferences restarted = context.getSharedPreferences("ui-restarted", Context.MODE_PRIVATE);
        new UserSettingsCheckpoint(context, restarted);
        assertTrue(restarted.getBoolean("hide_captcha_popups", false));
    }
    @Test public void deliberateResetAndFreshUserValuesWinOverBackup() {
        UserSettingsCheckpoint checkpoint = new UserSettingsCheckpoint(context, prefs);
        prefs.edit().putString("preset", "arctic_blue").commit();
        checkpoint.save();
        SharedPreferences newer = context.getSharedPreferences("newer-test", Context.MODE_PRIVATE);
        newer.edit().putString("preset", "default").commit();
        new UserSettingsCheckpoint(context, newer);
        assertEquals("default", newer.getString("preset", ""));
        prefs.edit().clear().commit();
        checkpoint.save(); // Same synchronous path as SharedPrefCategory.clear().
        new UserSettingsCheckpoint(context, prefs);
        assertTrue(prefs.getAll().isEmpty());
    }
    @Test public void corruptCheckpointFailsOpenWithoutPartialRestore() throws Exception {
        Files.write(new File(context.getFilesDir(), "blueit-user-settings-checkpoint-v1.json").toPath(),
                "{\"schema\":1,\"values\":{\"good\":{\"type\":\"boolean\",\"value\":true},\"bad\":{\"type\":\"unsupported\"}}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new UserSettingsCheckpoint(context, prefs);
        assertTrue(prefs.getAll().isEmpty());
    }
}
