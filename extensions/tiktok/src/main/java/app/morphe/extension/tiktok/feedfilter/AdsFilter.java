package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AdsFilter implements IFilter {
    private static volatile boolean rawAdAccessorResolved;
    private static volatile Method rawAdGetter;
    private static volatile Field rawAdField;

    @Override
    public boolean getEnabled() {
        return Settings.REMOVE_ADS.get();
    }

    @Override
    public boolean getFiltered(Aweme item) {
        if (item == null) return false;

        // These native flags are present on the exact TikTok 46.4.3 Aweme model.
        if (item.isAd() || item.isSoftAd() || item.isWithPromotionalMusic()) {
            return true;
        }

        // TikTok 46.4.3 can carry the full advertising payload while the lightweight
        // isAd/isSoftAd flags are not set on every feed representation. The exact target
        // exposes both getAwemeRawAd() and the public awemeRawAd field. Resolve them at
        // runtime so the extension stays fail-open if TikTok changes the model again.
        return hasRawAdPayload(item);
    }

    private static boolean hasRawAdPayload(Aweme item) {
        resolveRawAdAccessor(item.getClass());

        Method getter = rawAdGetter;
        if (getter != null) {
            try {
                if (getter.invoke(item) != null) return true;
            } catch (Throwable ignored) {
                // Fall through to the exact 46.4.3 public field accessor.
            }
        }

        Field field = rawAdField;
        if (field != null) {
            try {
                return field.get(item) != null;
            } catch (Throwable ignored) {
                // Fail open: never remove a normal feed item when model access fails.
            }
        }
        return false;
    }

    private static void resolveRawAdAccessor(Class<?> awemeClass) {
        if (rawAdAccessorResolved) return;
        synchronized (AdsFilter.class) {
            if (rawAdAccessorResolved) return;

            try {
                rawAdGetter = awemeClass.getMethod("getAwemeRawAd");
            } catch (Throwable ignored) {
                rawAdGetter = null;
            }

            try {
                rawAdField = awemeClass.getField("awemeRawAd");
            } catch (Throwable ignored) {
                rawAdField = null;
            }

            rawAdAccessorResolved = true;
        }
    }
}
