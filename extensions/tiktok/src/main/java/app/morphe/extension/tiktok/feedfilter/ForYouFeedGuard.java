package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks canonical For You FeedItemList instances from both the network and exact
 * TikTok 46.4.3 cache paths, then re-applies filtering after later client mutations.
 *
 * A global FeedItemList.getItems hook is safe only with this identity guard: the same
 * model class is also used by profile/detail/series surfaces, which must stay untouched.
 */
public final class ForYouFeedGuard {
    private static final Map<FeedItemList, Boolean> MARKED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ForYouFeedGuard() {
    }

    /** Called only from exact 46.4.3 For You network/cache fingerprints. */
    public static void markAndFilter(FeedItemList feedItemList) {
        if (feedItemList == null) return;
        MARKED.put(feedItemList, Boolean.TRUE);
        filterMarked(feedItemList);
    }

    /** Called from FeedItemList.getItems(); profile/detail lists are ignored. */
    public static void filterIfMarked(FeedItemList feedItemList) {
        if (feedItemList == null || !MARKED.containsKey(feedItemList)) return;
        filterMarked(feedItemList);
    }

    private static void filterMarked(FeedItemList feedItemList) {
        // TikTok can keep ad candidates outside items and inject them after the API/cache
        // response. Remove the exact 46.4.3 preloadAds source before those insertions.
        if (Settings.REMOVE_ADS.get()) {
            clearListProperty(feedItemList, "getPreloadAds", "preloadAds");
        }

        FeedItemsFilter.filter(feedItemList);
        AdvancedFeedFilter.filter(feedItemList);
    }

    private static void clearListProperty(Object target, String getterName, String fieldName) {
        Object value = invoke(target, getterName);
        if (!(value instanceof List<?>)) value = readField(target, fieldName);
        if (value instanceof List<?>) {
            try {
                ((List<?>) value).clear();
            } catch (Throwable ignored) {
                // Fail open if TikTok changes the collection implementation.
            }
        }
    }

    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
