package app.morphe.extension.tiktok.feedfilter;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeed;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeedList;

/** BlueIT-only advanced rules backed by fields already exposed by TikTok 46.4.3. */
public final class AdvancedFeedFilter {
    private AdvancedFeedFilter() {
    }

    public static void filter(FeedItemList feedItemList) {
        if (feedItemList == null || feedItemList.items == null) {
            return;
        }
        filterList(feedItemList.items, source -> source instanceof Aweme ? (Aweme) source : null);
    }

    public static void filter(FollowFeedList followFeedList) {
        if (followFeedList == null || followFeedList.mItems == null) {
            return;
        }
        filterList(
                followFeedList.mItems,
                source -> source instanceof FollowFeed ? ((FollowFeed) source).aweme : null
        );
    }

    public static void filterFinal(FollowFeedList followFeedList) {
        filter(followFeedList);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void filterList(List list, AwemeExtractor extractor) {
        if (!hasActiveRule() || list.isEmpty()) {
            return;
        }

        List snapshot = new ArrayList(list);
        List kept = new ArrayList(snapshot.size());
        int removed = 0;

        for (Object container : snapshot) {
            Aweme aweme = extractor.extract(container);
            String reason = reason(aweme);
            if (reason == null) {
                kept.add(container);
            } else {
                removed++;
                if (BaseSettings.DEBUG.get()) {
                    String aid = aweme == null ? "null" : aweme.getAid();
                    Logger.printInfo(() -> "[BlueIT AdvancedFeed] aid=" + aid + " filtered=" + reason);
                }
            }
        }

        if (removed > 0) {
            list.clear();
            list.addAll(kept);
        }
    }

    private static boolean hasActiveRule() {
        return Settings.HIDE_PROMOTIONAL_MUSIC.get()
                || Settings.HIDE_LIVE_REPLAYS.get()
                || Settings.MIN_LIKE_VIEW_RATIO_PERCENT.get() > 0;
    }

    private static String reason(Aweme aweme) {
        if (aweme == null) {
            return null;
        }
        if (Settings.HIDE_PROMOTIONAL_MUSIC.get() && aweme.isWithPromotionalMusic()) {
            return "promotional_music";
        }
        if (Settings.HIDE_LIVE_REPLAYS.get() && aweme.isLiveReplay()) {
            return "live_replay";
        }

        int minimumRatio = Settings.MIN_LIKE_VIEW_RATIO_PERCENT.get();
        if (minimumRatio > 0) {
            AwemeStatistics statistics = aweme.getStatistics();
            if (statistics != null) {
                long views = statistics.getPlayCount();
                long likes = statistics.getDiggCount();
                if (views > 0L) {
                    double ratio = likes * 100.0d / views;
                    if (ratio < minimumRatio) {
                        return "like_view_ratio";
                    }
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface AwemeExtractor {
        Aweme extract(Object source);
    }
}
