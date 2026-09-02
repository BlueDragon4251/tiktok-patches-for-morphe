package app.morphe.extension.tiktok.cleardisplay;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Owns Automatic Clear Display for TikTok 46.7.3.
 *
 * TikTok has changed/obfuscated the old PINCH_ZOOM enum used by the 46.4.3 native panel route.
 * Keep that route as a best-effort optimization, but never depend on it: every newly rendered video
 * also supplies a real ClearDisplay event instance which is dispatched through TikTok's event bus
 * when the native panel route is unavailable.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class AutomaticClearDisplayController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static final String CLEAR_PANEL_REQUEST_METHOD = "Rv0";
    private static final String CLEAR_EVENT_TYPE_CLASS = "X.12x2";
    private static final String CLEAR_EVENT_PINCH_ZOOM = "PINCH_ZOOM";
    private static final String CLEAR_EVENT_DISPATCHER_CLASS = "X.093F";

    private static Runnable pending;
    private static Object latestEvent;
    private static String latestEventToken = "";
    private static Object latestPanel;
    private static String latestPanelToken = "";
    private static volatile boolean manualOverride;
    private static volatile long lastAutomaticPostAtMs;
    private static volatile Integer pinchZoomType;
    private static volatile Boolean nativePanelRouteAvailable;
    private static volatile boolean nativeCompatibilityLogged;
    private static volatile boolean fallbackFailureLogged;

    private AutomaticClearDisplayController() {
    }

    public static boolean isEnabled() {
        return Settings.AUTOMATIC_CLEAR_DISPLAY.get();
    }

    public static boolean shouldRestoreRememberedState() {
        return !isEnabled();
    }

    /** Called from ClearModePanelComponent.resetClearMode for the current feed item. */
    public static void updatePanelContext(Object panel, Object itemContext) {
        if (panel == null) return;

        String token = contextToken(itemContext);
        synchronized (LOCK) {
            boolean changed = panel != latestPanel || !token.equals(latestPanelToken);
            latestPanel = panel;
            latestPanelToken = token;

            if (changed) {
                cancelLocked();
                manualOverride = false;
                lastAutomaticPostAtMs = 0L;

                // Never let an event retained from the previous feed item become the fallback for
                // this panel. onNewVideo() repopulates it with the current render event.
                latestEvent = null;
                latestEventToken = "";

                if (isEnabled()) {
                    scheduleLocked(panel, token);
                }
            }
        }
    }

    /** Called for every newly rendered feed video by the existing first-frame hook. */
    public static void onNewVideo(Object clearDisplayEvent) {
        synchronized (LOCK) {
            latestEvent = clearDisplayEvent;
            latestEventToken = latestPanelToken;
            if (!isEnabled() || manualOverride || latestPanel == null) {
                return;
            }

            cancelLocked();
            scheduleLocked(latestPanel, latestPanelToken);
        }
    }

    /** Immediately enters clear-display mode using the current 46.7.3 context/event. */
    public static boolean postNow() {
        final Object panel;
        final String token;
        synchronized (LOCK) {
            panel = latestPanel;
            token = latestPanelToken;
            cancelLocked();
        }
        return requestClearDisplay(panel, token, false);
    }

    /**
     * Tracks native clear-display state. Once automatic clear mode was requested, a later false
     * state suppresses re-entry until the next feed item.
     */
    public static void onClearDisplayStateChanged(boolean enabled) {
        if (!isEnabled() || enabled) return;

        long automaticPostAt = lastAutomaticPostAtMs;
        if (automaticPostAt <= 0L) return;

        // Ignore only immediate synchronous event-bus churn caused by our own request.
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

    private static void scheduleLocked(Object panel, String token) {
        if (panel == null || manualOverride) return;

        final Object scheduledPanel = panel;
        final String scheduledToken = token;
        pending = () -> {
            synchronized (LOCK) {
                pending = null;
                if (!isEnabled() || manualOverride || scheduledPanel != latestPanel
                        || !scheduledToken.equals(latestPanelToken)) {
                    return;
                }
            }
            requestClearDisplay(scheduledPanel, scheduledToken, true);
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
     * Try TikTok's old/native panel route first and transparently fall back to the current render
     * event. Missing obfuscated fields/methods are a compatibility condition, not a user-visible
     * error, so they are recorded as INFO at most once instead of producing a debug-error toast.
     */
    private static boolean requestClearDisplay(Object panel, String token, boolean automatic) {
        if (panel != null && requestNativeClearDisplay(panel, automatic)) {
            return true;
        }

        final Object fallbackEvent;
        synchronized (LOCK) {
            if (!token.equals(latestPanelToken) || !token.equals(latestEventToken)) {
                return false;
            }
            fallbackEvent = latestEvent;
        }

        if (fallbackEvent == null) return false;

        boolean dispatched = dispatchEvent(fallbackEvent, automatic);
        if (dispatched) {
            if (BaseSettings.DEBUG.get()) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] 46.7.3 event fallback dispatched "
                        + (automatic ? "automatically" : "for gesture"));
            }
            return true;
        }

        if (!fallbackFailureLogged) {
            fallbackFailureLogged = true;
            Logger.printInfo(() -> "[BlueIT ClearDisplay] no compatible 46.7.3 clear-display route was accepted");
        }
        return false;
    }

    /** Best-effort compatibility with TikTok builds that still expose the old native panel API. */
    private static boolean requestNativeClearDisplay(Object panel, boolean automatic) {
        if (Boolean.FALSE.equals(nativePanelRouteAvailable)) return false;

        try {
            int eventType = getPinchZoomType();
            Method request = findMethod(
                    panel.getClass(),
                    CLEAR_PANEL_REQUEST_METHOD,
                    int.class,
                    String.class,
                    boolean.class
            );
            if (request == null) {
                throw new NoSuchMethodException(panel.getClass().getName() + ".Rv0(int,String,boolean)");
            }
            request.setAccessible(true);

            Object result = request.invoke(panel, eventType, "pinch", true);
            boolean accepted = !(result instanceof Boolean) || (Boolean) result;
            nativePanelRouteAvailable = true;
            if (accepted && automatic) lastAutomaticPostAtMs = System.currentTimeMillis();

            if (BaseSettings.DEBUG.get()) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] native panel request "
                        + (automatic ? "automatic" : "gesture")
                        + " accepted=" + accepted
                        + " eventType=" + eventType);
            }
            return accepted;
        } catch (Throwable throwable) {
            nativePanelRouteAvailable = false;
            if (automatic) lastAutomaticPostAtMs = 0L;
            logNativeCompatibilityOnce(throwable);
            return false;
        }
    }

    /**
     * Resolve the old symbolic constant when it still exists. On partially obfuscated variants,
     * also inspect static values whose name/toString still contains both "pinch" and "zoom".
     */
    private static int getPinchZoomType() throws Exception {
        Integer cached = pinchZoomType;
        if (cached != null) return cached;

        Class<?> typeClass = Class.forName(CLEAR_EVENT_TYPE_CLASS);
        Object enumValue = null;

        try {
            Field field = typeClass.getDeclaredField(CLEAR_EVENT_PINCH_ZOOM);
            field.setAccessible(true);
            enumValue = field.get(null);
        } catch (NoSuchFieldException ignored) {
            for (Field field : typeClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!typeClass.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    Object candidate = field.get(null);
                    if (candidate == null) continue;
                    String label = (field.getName() + " " + String.valueOf(candidate))
                            .toLowerCase(Locale.ROOT);
                    if (label.contains("pinch") && label.contains("zoom")) {
                        enumValue = candidate;
                        break;
                    }
                } catch (Throwable ignoredCandidate) {
                }
            }
        }

        if (enumValue == null) {
            throw new NoSuchFieldException(CLEAR_EVENT_TYPE_CLASS + ".PINCH_ZOOM");
        }

        Method getType = findMethod(enumValue.getClass(), "getType");
        if (getType == null) throw new NoSuchMethodException("clear event type getType()");
        getType.setAccessible(true);
        Object value = getType.invoke(enumValue);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("clear event type getType() is not numeric");
        }

        int resolved = ((Number) value).intValue();
        pinchZoomType = resolved;
        return resolved;
    }

    /** TikTok event-bus fallback used by 46.7.3 when the native enum/panel route is obfuscated. */
    private static boolean dispatchEvent(Object event, boolean automatic) {
        try {
            Class<?> dispatcher = Class.forName(CLEAR_EVENT_DISPATCHER_CLASS);
            Method dispatch = null;
            for (Method candidate : dispatcher.getDeclaredMethods()) {
                if (!candidate.getName().equals("LIZ") || candidate.getParameterTypes().length != 1
                        || !Modifier.isStatic(candidate.getModifiers())) {
                    continue;
                }
                if (candidate.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                    dispatch = candidate;
                    break;
                }
            }
            if (dispatch != null) {
                dispatch.setAccessible(true);
                if (automatic) lastAutomaticPostAtMs = System.currentTimeMillis();
                dispatch.invoke(null, event);
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to the event instance method.
        }

        try {
            Method post = findMethod(event.getClass(), "post");
            if (post == null) return false;
            post.setAccessible(true);
            if (automatic) lastAutomaticPostAtMs = System.currentTimeMillis();
            post.invoke(event);
            return true;
        } catch (Throwable throwable) {
            if (automatic) lastAutomaticPostAtMs = 0L;
            if (BaseSettings.DEBUG.get() && !fallbackFailureLogged) {
                Logger.printInfo(() -> "[BlueIT ClearDisplay] fallback dispatch unavailable: "
                        + throwable.getClass().getSimpleName() + ": " + safeMessage(throwable));
            }
            return false;
        }
    }

    private static void logNativeCompatibilityOnce(Throwable throwable) {
        if (nativeCompatibilityLogged) return;
        nativeCompatibilityLogged = true;
        Logger.printInfo(() -> "[BlueIT ClearDisplay] old native PINCH_ZOOM route unavailable on 46.7.3; "
                + "using event fallback (" + throwable.getClass().getSimpleName()
                + ": " + safeMessage(throwable) + ")");
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "no message" : message;
    }

    private static String contextToken(Object itemContext) {
        if (itemContext == null) return "null";
        try {
            Object aweme = invokeNoArg(itemContext, "getAweme");
            if (aweme != null) {
                Object aid = invokeNoArg(aweme, "getAid");
                if (aid instanceof String && !((String) aid).isEmpty()) {
                    return "aid:" + aid;
                }
            }
        } catch (Throwable ignored) {
        }
        return itemContext.getClass().getName() + "@" + System.identityHashCode(itemContext);
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = findMethod(target.getClass(), name);
        if (method == null) return null;
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
