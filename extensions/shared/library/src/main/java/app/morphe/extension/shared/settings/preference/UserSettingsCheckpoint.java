package app.morphe.extension.shared.settings.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Main-process checkpoint outside SharedPreferences, recovered before Setting instances load. */
public final class UserSettingsCheckpoint {
    private static final int MAX_BYTES = 262144;
    private final SharedPreferences preferences;
    private final AtomicFile file;
    // SharedPreferences keeps listeners weakly; retain this one for the category's lifetime.
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    UserSettingsCheckpoint(Context context, SharedPreferences preferences) {
        this(context, preferences, true);
    }

    /** Npth can initialize before the extension's global context and Setting class are available. */
    public static void restoreForDiagnostics(Context context) {
        try {
            if (!"com.zhiliaoapp.musically".equals(context.getPackageName())
                    || android.os.Build.VERSION.SDK_INT < 28) return;
            String expected = context.getApplicationInfo().processName;
            if (expected == null || expected.isEmpty()) expected = context.getPackageName();
            if (!expected.equals(android.app.Application.getProcessName())) return;
            new UserSettingsCheckpoint(context,
                    context.getSharedPreferences("morphe_prefs", Context.MODE_PRIVATE), false);
        } catch (Exception ignored) { }
    }

    private UserSettingsCheckpoint(Context context, SharedPreferences preferences, boolean observe) {
        this.preferences = preferences;
        file = new AtomicFile(new File(context.getFilesDir(), "blueit-user-settings-checkpoint-v1.json"));
        recover();
        if (!observe) {
            listener = null;
            return;
        }
        save();
        listener = (prefs, key) -> {
            // An external preference-file clear must not erase the recovery copy. Explicit resets
            // through SharedPrefCategory.clear() save the empty checkpoint synchronously instead.
            if (key != null) save();
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    private void recover() {
        if (!preferences.getAll().isEmpty()) return;
        try {
            byte[] bytes = file.readFully();
            if (bytes.length > MAX_BYTES) return;
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (root.getInt("schema") != 1) return;
            JSONObject values = root.getJSONObject("values");
            // Validate the complete checkpoint before touching preferences.
            Map<String, Object> decoded = new HashMap<>();
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject entry = values.getJSONObject(key);
                String type = entry.getString("type");
                Object value;
                switch (type) {
                    case "boolean": value = entry.getBoolean("value"); break;
                    case "string": value = entry.getString("value"); break;
                    case "int": value = entry.getInt("value"); break;
                    case "long": value = entry.getLong("value"); break;
                    case "float": value = Float.valueOf(entry.getString("value")); break;
                    case "set":
                        JSONArray array = entry.getJSONArray("value");
                        Set<String> strings = new HashSet<>();
                        for (int i = 0; i < array.length(); i++) strings.add(array.getString(i));
                        value = strings;
                        break;
                    default: return;
                }
                decoded.put(key, value);
            }
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, Object> entry : decoded.entrySet()) put(editor, entry.getKey(), entry.getValue());
            editor.commit();
        } catch (Exception ignored) {
            // A missing/corrupt backup must leave a fresh install usable.
        }
    }

    public synchronized void save() {
        FileOutputStream output = null;
        try {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                Object value = entry.getValue();
                String type = value instanceof Boolean ? "boolean" : value instanceof String ? "string"
                        : value instanceof Integer ? "int" : value instanceof Long ? "long"
                        : value instanceof Float ? "float" : value instanceof Set ? "set" : null;
                if (type == null) continue;
                Object encoded = value instanceof Set ? new JSONArray((Set<?>) value)
                        : value instanceof Float ? value.toString() : value;
                values.put(entry.getKey(), new JSONObject().put("type", type).put("value", encoded));
            }
            byte[] bytes = new JSONObject().put("schema", 1).put("values", values)
                    .toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) return;
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception ignored) {
            if (output != null) file.failWrite(output);
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
}
