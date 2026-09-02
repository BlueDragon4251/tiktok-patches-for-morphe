package app.morphe.extension.tiktok.cleardisplay;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Owns Automatic Clear Display for TikTok 46.7.3.
 *
 * The old recovery implementation attempted to drive ClearModePanelComponent through an obfuscated
 * native PINCH_ZOOM/Rv0 route. Even though the call itself was reflective, enabling the feature
 * made TikTok unstable on a real device. The 46.7.3 first-frame path already knows the concrete
 * clear-display event class, so the patch now passes only that class name as a String. This class
 * creates and posts the event entirely through reflection after the configured delay. No TikTok
 * event class, constructor or panel API is linked from the early player bytecode.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class AutomaticClearDisplayController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static final ConcurrentHashMap<String, Constructor<?>> EVENT_CONSTRUCTORS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> EVENT_POST_METHODS =
            new ConcurrentHashMap<>();

    private static Runnable pending;
    private static String latestEventClassName = "";
    private static int videoGeneration;
    private static volatile boolean patchEnabled;
    private static volatile boolean manualOverride;
    private static volatile long lastAutomaticPostAtMs;
    private static volatile boolean compatibilityFailureLogged;

    private AutomaticClearDisplayController() {
    }

    /** Called only when the Automatic Clear Display patch is actually present in the patched APK. */
    public static void enablePatch() {
        patchEnabled = true;
    }

    public static boolean isEnabled() {
        return patchEnabled && Settings.AUTOMATIC_CLEAR_DISPLAY.get();
    }

    public static boolean shouldRestoreRememberedState() {
        return !isEnabled();
    }

    /**
     * Called once for every rendered feed item by the proven first-frame hook.
     *
     * Only a Java String crosses the injected bytecode boundary. The event class itself is resolved
     * lazily after the delay, which keeps ART verification independent from TikTok's obfuscated
     * constructor descriptor.
     */
    public static void onRenderFirstFrame(String eventClassName) {
        if (!isEnabled()) return;

        String normalized = normalizeClassName(eventClassName);
        if (normalized.isEmpty()) return;

        synchronized (LOCK) {
            latestEventClassName = normalized;
            videoGeneration++;
            manualOverride = false;
            lastAutomaticPostAtMs = 0L;
            cancelLocked();
            scheduleLocked(videoGeneration, normalized);
        }
    }

    /** Gesture/manual entry point used by optional integrations after a first frame has been seen. */
    public static boolean postNow() {
        final String eventClassName;
        synchronized (LOCK) {
            eventClassName = latestEventClassName;
            cancelLocked();
        }
        if (!isEnabled() || eventClassName.isEmpty()) return false;
        return postEvent(eventClassName, true);
    }

    /** Proven remembered-state restoration, also kept fully reflective for verifier safety. */
    public static boolean postRemembered(String eventClassName) {
        String normalized = normalizeClassName(eventClassName);
        if (normalized.isEmpty()) return false;
        return postEvent(normalized, false);
    }

    /**
     * Tracks native clear-display state. If the user manually leaves clear display after our
     * automatic request, do not force it back on for the same video. A new first frame resets this.
     */
    public static void onClearDisplayStateChanged(boolean enabled) {
        if (!isEnabled() || enabled) return;

        long automaticPostAt = lastAutomaticPostAtMs;
        if (automaticPostAt <= 0L) return;

        // Ignore immediate synchronous state churn caused by our own event dispatch.
        if (System.currentTimeMillis() - automaticPostAt <= 150L) return;

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

    /**
     * Compatibility no-op for older development APK bytecode. New builds no longer inject a panel
     * hook at all; keeping the method prevents a stale in-process call from becoming fatal.
     */
    public static void updatePanelContext(Object panel, Object itemContext) {
        // Intentionally unused in the verifier-safe 46.7.3 implementation.
    }

    private static void scheduleLocked(int generation, String eventClassName) {
        if (manualOverride) return;

        pending = () -> {
            synchronized (LOCK) {
                pending = null;
                if (!isEnabled()
                        || manualOverride
                        || generation != videoGeneration
                        || !eventClassName.equals(latestEventClassName)) {
                    return;
                }
            }
            postEvent(eventClassName, true);
        };
        MAIN.postDelayed(pending, delayMs());
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

    /**
     * Creates the same four-argument ClearDisplay event used by remembered-state restoration:
     * enabled=true, type=0, empty metadata and source="long_press". The exact event class is not
     * referenced in bytecode; all class/constructor/post lookups happen here and fail open.
     */
    private static boolean postEvent(String eventClassName, boolean automatic) {
        try {
            Constructor<?> constructor = eventConstructor(eventClassName);
            Method post = eventPostMethod(eventClassName, constructor.getDeclaringClass());
            if (constructor == null || post == null) return false;

            Object event = constructor.newInstance(true, 0, "", "long_press");
            if (automatic) lastAutomaticPostAtMs = System.currentTimeMillis();
            post.invoke(event);

            if (BaseSettings.DEBUG.get()) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] reflected event posted "
                        + (automatic ? "automatically" : "for remembered state")
                        + " class=" + eventClassName);
            }
            return true;
        } catch (Throwable throwable) {
            if (automatic) lastAutomaticPostAtMs = 0L;
            logCompatibilityFailureOnce(throwable, eventClassName);
            return false;
        }
    }

    private static Constructor<?> eventConstructor(String eventClassName) throws Exception {
        Constructor<?> cached = EVENT_CONSTRUCTORS.get(eventClassName);
        if (cached != null) return cached;

        Class<?> eventClass = loadEventClass(eventClassName);
        Constructor<?> constructor = eventClass.getDeclaredConstructor(
                boolean.class,
                int.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);
        EVENT_CONSTRUCTORS.put(eventClassName, constructor);
        return constructor;
    }

    private static Method eventPostMethod(String eventClassName, Class<?> eventClass) throws Exception {
        Method cached = EVENT_POST_METHODS.get(eventClassName);
        if (cached != null) return cached;

        Method post = findNoArgMethod(eventClass, "post");
        if (post == null) {
            throw new NoSuchMethodException(eventClassName + ".post()");
        }
        post.setAccessible(true);
        EVENT_POST_METHODS.put(eventClassName, post);
        return post;
    }

    private static Class<?> loadEventClass(String eventClassName) throws ClassNotFoundException {
        try {
            return Class.forName(eventClassName);
        } catch (ClassNotFoundException first) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                return Class.forName(eventClassName, false, contextLoader);
            }
            throw first;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String normalizeClassName(String eventClassName) {
        if (eventClassName == null) return "";
        String value = eventClassName.trim();
        if (value.startsWith("L") && value.endsWith(";") && value.length() > 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace('/', '.');
    }

    private static void logCompatibilityFailureOnce(Throwable throwable, String eventClassName) {
        if (compatibilityFailureLogged) return;
        compatibilityFailureLogged = true;
        Logger.printInfo(() -> "[BlueIT ClearDisplay] verifier-safe reflected event route unavailable "
                + "class=" + eventClassName
                + " (" + throwable.getClass().getSimpleName()
                + ": " + safeMessage(throwable) + ")");
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no message" : message;
    }
}
