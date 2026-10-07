package app.morphe.extension.tiktok.theme;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.WeakHashMap;

/** Async native binds may return Views before attachment. Confine theme maps and writes to UI. */
final class ThemeUiThread {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Runnable> PENDING = new WeakHashMap<>();
    private ThemeUiThread() { }
    static boolean defer(View view, Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) return false;
        synchronized (PENDING) {
            boolean queued = PENDING.containsKey(view);
            PENDING.put(view, work);
            if (!queued) MAIN.post(() -> {
                Runnable latest;
                synchronized (PENDING) { latest = PENDING.remove(view); }
                if (latest != null) latest.run();
            });
        }
        return true;
    }
}
