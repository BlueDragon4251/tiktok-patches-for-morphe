package app.morphe.extension.tiktok.cleardisplay;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Schedules TikTok's native clear-display event without duplicating the bytecode hook.
 */
@SuppressWarnings("unused")
public final class AutomaticClearDisplayController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static Runnable pending;
    private static volatile boolean manualOverride;
    private static volatile long lastAutomaticPostAtMs;

    private AutomaticClearDisplayController() {
    }

    public static boolean isEnabled() {
        return Settings.AUTOMATIC_CLEAR_DISPLAY.get();
    }

    public static boolean shouldRestoreRememberedState() {
        return !isEnabled();
    }

    /** Called for every newly rendered feed video. */
    public static void onNewVideo(Object clearDisplayEvent) {
        synchronized (LOCK) {
            cancelLocked();
            manualOverride = false;
            if (!isEnabled() || clearDisplayEvent == null) {
                return;
            }

            final Object event = clearDisplayEvent;
            pending = () -> {
                synchronized (LOCK) {
                    pending = null;
                    if (!isEnabled() || manualOverride) {
                        return;
                    }
                }
                postEvent(event);
            };
            MAIN.postDelayed(pending, delayMs());
        }
    }

    /**
     * Tracks a native clear-display state change. A user leaving clear display after
     * automatic activation suppresses reactivation until the next video.
     */
    public static void onClearDisplayStateChanged(boolean enabled) {
        if (!isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (enabled) {
            // The event bus dispatch is synchronous on supported 46.4.3 paths.
            // Keep a small time window as a defensive fallback.
            return;
        }

        if (now - lastAutomaticPostAtMs > 750L) {
            synchronized (LOCK) {
                manualOverride = true;
                cancelLocked();
            }
        }
    }

    public static void cancel() {
        synchronized (LOCK) {
            cancelLocked();
        }
    }

    private static long delayMs() {
        int configured = Settings.AUTOMATIC_CLEAR_DISPLAY_DELAY_MS.get();
        return Math.max(250L, Math.min(15_000L, configured));
    }

    private static void cancelLocked() {
        if (pending != null) {
            MAIN.removeCallbacks(pending);
            pending = null;
        }
    }

    private static void postEvent(Object event) {
        try {
            lastAutomaticPostAtMs = System.currentTimeMillis();
            Method post = event.getClass().getMethod("post");
            post.invoke(event);
            if (BaseSettings.DEBUG.get()) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] automatic clear-display event posted");
            }
        } catch (Throwable throwable) {
            if (BaseSettings.DEBUG.get()) {
                Logger.printException(() -> "[BlueIT ClearDisplay] automatic event failed", throwable);
            }
        }
    }
}
