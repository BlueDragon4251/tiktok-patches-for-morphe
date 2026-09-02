package app.morphe.extension.shared.settings.preference;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Shared categories, and helper methods.
 * <p>
 * The various save methods store numbers as Strings,
 * which is required if using {@link PreferenceFragment}.
 * <p>
 * If saved numbers will not be used with a preference fragment,
 * then store the primitive numbers using the {@link #preferences} itself.
 */
public class SharedPrefCategory {
    private static final String USER_SETTINGS_CATEGORY = "morphe_prefs";
    private static final String BLUEIT_TIKTOK_SETTINGS_CATEGORY = "blueit_prefs";
    private static final String BLUEIT_TIKTOK_PACKAGE = "com.zhiliaoapp.musically";
    private static final String BLUEIT_MIGRATION_MARKER = "__blueit_local_settings_migrated_v1";

    @NonNull
    public final String name;
    @NonNull
    public final SharedPreferences preferences;

    /**
     * Android SharedPreferences keeps a process-local in-memory snapshot and is not safe for
     * coordinated writes from multiple app processes. TikTok uses multiple processes and the
     * extension can be initialized outside the main activity process. A stale secondary-process
     * snapshot can therefore overwrite unrelated newer user settings when it later saves one key.
     *
     * TikTok now stores Morphe/BlueIT user settings in its own dedicated app-private preference
     * file (blueit_prefs) instead of sharing morphe_prefs. Existing values are migrated once.
     * Only the main app process may persist either user-settings category.
     */
    private final boolean persistentWritesAllowed;
    private final boolean blueItTikTokStorage;

    public SharedPrefCategory(@NonNull String name) {
        this.name = Objects.requireNonNull(name);
        Context context = Objects.requireNonNull(Utils.getContext());

        blueItTikTokStorage = USER_SETTINGS_CATEGORY.equals(name)
                && BLUEIT_TIKTOK_PACKAGE.equals(context.getPackageName());
        String actualPreferenceName = blueItTikTokStorage
                ? BLUEIT_TIKTOK_SETTINGS_CATEGORY
                : name;

        preferences = context.getSharedPreferences(actualPreferenceName, Context.MODE_PRIVATE);
        persistentWritesAllowed = !isUserSettingsCategory(name) || isMainAppProcess(context);

        if (blueItTikTokStorage && persistentWritesAllowed) {
            migrateLegacyTikTokUserSettings(context);
            Logger.printInfo(() -> "Using TikTok-local BlueIT settings storage: "
                    + BLUEIT_TIKTOK_SETTINGS_CATEGORY);
        } else if (!persistentWritesAllowed) {
            Logger.printInfo(() -> "Preventing secondary-process writes to shared user settings");
        }
    }

    private static boolean isUserSettingsCategory(@NonNull String name) {
        return USER_SETTINGS_CATEGORY.equals(name)
                || BLUEIT_TIKTOK_SETTINGS_CATEGORY.equals(name);
    }

    /**
     * One-time migration from the old Morphe user-settings preference file.
     *
     * The old file is intentionally left in place for downgrade safety. The marker is stored only
     * in blueit_prefs, so once migration has completed a stale legacy file can never overwrite a
     * newer BlueIT value on a later launch.
     */
    private void migrateLegacyTikTokUserSettings(@NonNull Context context) {
        if (preferences.getBoolean(BLUEIT_MIGRATION_MARKER, false)) {
            return;
        }

        try {
            SharedPreferences legacy =
                    context.getSharedPreferences(USER_SETTINGS_CATEGORY, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            int migrated = 0;

            for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                String key = entry.getKey();
                if (BLUEIT_MIGRATION_MARKER.equals(key) || preferences.contains(key)) {
                    continue;
                }

                Object value = entry.getValue();
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else {
                    continue;
                }
                migrated++;
            }

            editor.putBoolean(BLUEIT_MIGRATION_MARKER, true);
            if (editor.commit()) {
                final int migratedCount = migrated;
                Logger.printInfo(() -> "Migrated " + migratedCount
                        + " settings into TikTok-local BlueIT storage");
            } else {
                Logger.printException(() -> "Failed to commit TikTok-local BlueIT settings migration");
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to migrate TikTok-local BlueIT settings", exception);
        }
    }

    private static boolean isMainAppProcess(@NonNull Context context) {
        try {
            String expectedProcessName = context.getApplicationInfo().processName;
            if (expectedProcessName == null || expectedProcessName.isEmpty()) {
                expectedProcessName = context.getPackageName();
            }

            String currentProcessName = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentProcessName = Application.getProcessName();
            } else {
                ActivityManager activityManager =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    int pid = android.os.Process.myPid();
                    for (ActivityManager.RunningAppProcessInfo processInfo
                            : activityManager.getRunningAppProcesses()) {
                        if (processInfo.pid == pid) {
                            currentProcessName = processInfo.processName;
                            break;
                        }
                    }
                }
            }

            // If Android cannot report the process name, preserve the old behavior rather than
            // unexpectedly making settings read-only on an unsupported device.
            return currentProcessName == null || expectedProcessName.equals(currentProcessName);
        } catch (Throwable throwable) {
            Logger.printException(() -> "Failed to determine app process for settings persistence", throwable);
            return true;
        }
    }

    private boolean canPersist() {
        return persistentWritesAllowed;
    }

    private void removeConflictingPreferenceKeyValue(@NonNull String key) {
        Logger.printException(() -> "Found conflicting preference: " + key);
        removeKey(key);
    }

    @SuppressLint("ApplySharedPref") // Must use commit to ensure default value is not saved to preferences.
    private void saveObjectAsString(@NonNull String key, @Nullable Object value) {
        if (!canPersist()) return;
        preferences.edit().putString(key, (value == null ? null : value.toString())).commit();
    }

    @SuppressLint("ApplySharedPref") // Must use commit to ensure default value is not saved to preferences.
    public void clear() {
        if (!canPersist()) return;
        SharedPreferences.Editor editor = preferences.edit().clear();
        // Do not let a deliberate reset re-import stale values from the legacy file on restart.
        if (blueItTikTokStorage) {
            editor.putBoolean(BLUEIT_MIGRATION_MARKER, true);
        }
        editor.commit();
    }

    /**
     * Removes any preference data type that has the specified key.
     */
    @SuppressLint("ApplySharedPref") // Must use commit to ensure default value is not saved to preferences.
    public void removeKey(@NonNull String key) {
        if (!canPersist()) return;
        preferences.edit().remove(Objects.requireNonNull(key)).commit();
    }

    @SuppressLint("ApplySharedPref") // Must use commit to ensure default value is not saved to preferences.
    public void saveBoolean(@NonNull String key, boolean value) {
        if (!canPersist()) return;
        preferences.edit().putBoolean(key, value).commit();
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveEnumAsString(@NonNull String key, @Nullable Enum<?> value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveIntegerString(@NonNull String key, @Nullable Integer value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveLongString(@NonNull String key, @Nullable Long value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveFloatString(@NonNull String key, @Nullable Float value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveString(@NonNull String key, @Nullable String value) {
        saveObjectAsString(key, value);
    }

    @NonNull
    public String getString(@NonNull String key, @NonNull String _default) {
        Objects.requireNonNull(_default);
        try {
            return preferences.getString(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }

    @NonNull
    public <T extends Enum<?>> T getEnum(@NonNull String key, @NonNull T _default) {
        Objects.requireNonNull(_default);
        try {
            String enumName = preferences.getString(key, null);
            if (enumName != null) {
                try {
                    // noinspection unchecked
                    return (T) Enum.valueOf(_default.getClass(), enumName);
                } catch (IllegalArgumentException ex) {
                    // Info level to allow removing enum values in the future without showing any user errors.
                    Logger.printInfo(() -> "Using default, and ignoring unknown enum value: "  + enumName);
                    removeKey(key);
                }
            }
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
        }
        return _default;
    }

    public boolean getBoolean(@NonNull String key, boolean _default) {
        try {
            return preferences.getBoolean(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }

    @NonNull
    public Integer getIntegerString(@NonNull String key, @NonNull Integer _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Integer.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                // Old data previously stored as primitive.
                return preferences.getInt(key, _default);
            } catch (ClassCastException ex2) {
                // Value stored is a completely different type (should never happen).
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    public Long getLongString(@NonNull String key, @NonNull Long _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Long.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                return preferences.getLong(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    public Float getFloatString(@NonNull String key, @NonNull Float _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Float.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                return preferences.getFloat(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
