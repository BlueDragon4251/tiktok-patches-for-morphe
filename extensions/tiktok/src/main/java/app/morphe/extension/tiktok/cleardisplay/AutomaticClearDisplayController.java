package app.morphe.extension.tiktok.cleardisplay;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Owns the current video's native clear-display event and optionally schedules it.
 * Gesture remapping reuses the same proven TikTok event path via postNow().
 */
@SuppressWarnings("unused")
public final class AutomaticClearDisplayController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static Runnable pending;
    private static Object latestEvent;
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

    /** Called for every newly rendered feed video, regardless of whether auto mode is enabled. */
    public static void onNewVideo(Object clearDisplayEvent) {
        synchronized (LOCK) {
            cancelLocked();
            manualOverride = false;
            lastAutomaticPostAtMs = 0L;
            latestEvent = clearDisplayEvent;
            if (!isEnabled() || clearDisplayEvent == null) {
                return;
            }

            final Object event = clearDisplayEvent;
            pending = () -> {
                synchronized (LOCK) {
                    pending = null;
                    if (!isEnabled() || manualOverride || event != latestEvent) {
                        return;
                    }
                }
                postEvent(event, true);
            };
            MAIN.postDelayed(pending, delayMs());
        }
    }

    /** Immediately enters clear-display mode using the current video's native event. */
    public static boolean postNow() {
        final Object event;
        synchronized (LOCK) {
            event = latestEvent;
            if (event == null) return false;
            cancelLocked();
        }
        return postEvent(event, false);
    }

    /**
     * Tracks a native clear-display state change. A user leaving clear display after
     * automatic activation suppresses reactivation until the next video.
     */
    public static void onClearDisplayStateChanged(boolean enabled) {
        if (!isEnabled() || enabled) {
            return;
        }

        // TikTok emits an initial "clear display = false" state while a new video is
        // being prepared. That is not a user override and must not cancel the timer.
        long automaticPostAt = lastAutomaticPostAtMs;
        if (automaticPostAt <= 0L) {
            return;
        }

        // Ignore the state churn immediately caused by posting the native event itself.
        // Only a later transition back to the normal UI is considered a manual override.
        long now = System.currentTimeMillis();
        if (now - automaticPostAt <= 750L) {
            return;
        }

        synchronized (LOCK) {
            manualOverride = true;
            cancelLocked();
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

    private static boolean postEvent(Object event, boolean automatic) {
        try {
            Method post = findPostMethod(event.getClass());
            if (post == null) {
                throw new NoSuchMethodException(event.getClass().getName() + ".post()");
            }
            if (!post.isAccessible()) {
                post.setAccessible(true);
            }

            if (automatic) {
                lastAutomaticPostAtMs = System.currentTimeMillis();
            }
            post.invoke(event);
            if (BaseSettings.DEBUG.get()) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] "
                        + (automatic ? "automatic" : "gesture") + " clear-display event posted");
            }
            return true;
        } catch (Throwable throwable) {
            if (automatic) {
                lastAutomaticPostAtMs = 0L;
            }
            if (BaseSettings.DEBUG.get()) {
                Logger.printException(() -> "[BlueIT ClearDisplay] event failed", throwable);
            }
            return false;
        }
    }

    private static Method findPostMethod(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod("post");
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        try {
            return type.getMethod("post");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
