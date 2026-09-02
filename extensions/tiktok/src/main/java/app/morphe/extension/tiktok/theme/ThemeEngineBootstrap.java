package app.morphe.extension.tiktok.theme;

import android.app.Activity;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Fail-open entry point for the experimental TikTok Theme Engine.
 *
 * Keep this class deliberately independent from ThemeEngine at link time. The actual engine is
 * loaded through reflection so a VerifyError/LinkageError in theme code can never make TikTok's
 * MainActivity fail verification or crash synchronously during onCreate.
 */
@SuppressWarnings("unused")
public final class ThemeEngineBootstrap {
    private static final String ENGINE_CLASS = "app.morphe.extension.tiktok.theme.ThemeEngine";
    private static final String REALTIME_GUARD_CLASS =
            "app.morphe.extension.tiktok.theme.ThemeRealtimeUiGuard";
    private static volatile boolean runtimeFailed;
    private static volatile String patchDefaultPreset = "default";

    private ThemeEngineBootstrap() {}

    public static void setPatchDefaultPreset(String preset) {
        try {
            patchDefaultPreset = preset == null || preset.isEmpty() ? "default" : preset;
        } catch (Throwable ignored) {
            patchDefaultPreset = "default";
        }
    }

    public static void start(Activity activity) {
        if (activity == null || runtimeFailed) return;

        try {
            Utils.setContext(activity);
        } catch (Throwable throwable) {
            safeLog("BlueIT Theme Engine context bootstrap failed", throwable);
            return;
        }

        try {
            View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            Runnable start = () -> startEngine(activity, patchDefaultPreset);
            if (decor != null) {
                // Keep theme work out of TikTok's critical MainActivity.onCreate path.
                decor.postDelayed(start, 350L);
            } else {
                activity.runOnUiThread(start);
            }
        } catch (Throwable throwable) {
            runtimeFailed = true;
            safeLog("BlueIT Theme Engine bootstrap scheduling failed", throwable);
        }
    }

    private static void startEngine(Activity activity, String preset) {
        if (runtimeFailed || activity == null || activity.isFinishing()) return;

        try {
            // ThemeStateStore owns patch-default seeding now. Unlike a normal Setting it persists an
            // explicit "default" selection too, so a patch-time Liquid Glass seed can never return
            // after the user has chosen another preset.
            ThemeStateStore.initialize(activity, preset);

            Class<?> engine = Class.forName(ENGINE_CLASS, true, activity.getClassLoader());
            Method onCreated = engine.getDeclaredMethod("onMainActivityCreated", Activity.class);
            onCreated.setAccessible(true);
            onCreated.invoke(null, activity);
        } catch (Throwable throwable) {
            runtimeFailed = true;
            Throwable cause = throwable;
            if (throwable instanceof InvocationTargetException
                    && ((InvocationTargetException) throwable).getCause() != null) {
                cause = ((InvocationTargetException) throwable).getCause();
            }
            safeLog("BlueIT Theme Engine disabled after runtime bootstrap failure", cause);
            return;
        }

        // Recycler-backed Inbox/Activity surfaces can be rebound after the bounded engine passes.
        // Install the frame-synchronous guard separately so a failure here never disables the core
        // theme runtime that already started successfully above.
        try {
            Class<?> guard = Class.forName(REALTIME_GUARD_CLASS, true, activity.getClassLoader());
            Method install = guard.getDeclaredMethod("install", Activity.class);
            install.setAccessible(true);
            install.invoke(null, activity);
        } catch (Throwable throwable) {
            Throwable cause = throwable;
            if (throwable instanceof InvocationTargetException
                    && ((InvocationTargetException) throwable).getCause() != null) {
                cause = ((InvocationTargetException) throwable).getCause();
            }
            safeLog("BlueIT realtime theme guard unavailable", cause);
        }
    }

    private static void safeLog(String message, Throwable throwable) {
        try {
            if (throwable instanceof Exception) {
                Logger.printDebug(() -> message, (Exception) throwable);
            } else {
                Logger.printInfo(() -> message + ": " + throwable.getClass().getSimpleName());
            }
        } catch (Throwable ignored) {
            // Logging must never turn a fail-open guard into a startup crash.
        }
    }
}
