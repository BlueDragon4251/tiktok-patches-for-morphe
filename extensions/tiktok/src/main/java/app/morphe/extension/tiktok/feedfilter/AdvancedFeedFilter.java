package app.morphe.extension.tiktok.feedfilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.AdvancedFeedSettings;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeed;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeedList;

/**
 * BlueIT advanced feed filtering. All reflective metadata rules are fail-open:
 * missing/renamed TikTok data never causes a video to be hidden.
 */
public final class AdvancedFeedFilter {
    private static volatile String cachedKeywordsSource = "";
    private static volatile String[] cachedKeywords = new String[0];
    private static volatile String cachedCreatorsSource = "";
    private static volatile String[] cachedCreators = new String[0];
    private static volatile String cachedSoundsSource = "";
    private static volatile String[] cachedSounds = new String[0];
    private static volatile String cachedDurationSource = "";
    private static volatile Range cachedDuration = new Range(0L, Long.MAX_VALUE);

    private AdvancedFeedFilter() {
    }

    public static void filter(FeedItemList feedItemList) {
        if (feedItemList == null || feedItemList.items == null) return;
        filterList(feedItemList.items, source -> source instanceof Aweme ? (Aweme) source : null);
    }

    public static void filter(FollowFeedList followFeedList) {
        if (followFeedList == null || followFeedList.mItems == null) return;
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
        if (!hasActiveRule() || list.isEmpty()) return;

        List snapshot = new ArrayList(list);
        List kept = new ArrayList(snapshot.size());
        int removed = 0;
        boolean removedForNonDurationRule = false;
        Object durationFallback = null;
        String durationFallbackAid = null;
        long durationFallbackMs = -1L;
        long durationFallbackDistanceMs = Long.MAX_VALUE;
        Range activeDurationRange = durationRange();

        for (Object container : snapshot) {
            Aweme aweme = extractor.extract(container);
            String reason = reason(aweme);
            if (reason == null) {
                kept.add(container);
                continue;
            }

            removed++;
            if ("duration".equals(reason)) {
                long durationMs = durationMilliseconds(aweme);
                long distanceMs = durationDistanceToRange(durationMs, activeDurationRange);
                if (durationMs >= 0L && distanceMs < durationFallbackDistanceMs) {
                    durationFallback = container;
                    durationFallbackAid = safeAid(aweme);
                    durationFallbackMs = durationMs;
                    durationFallbackDistanceMs = distanceMs;
                }
            } else {
                removedForNonDurationRule = true;
            }

            if (BaseSettings.DEBUG.get()) {
                String aid = safeAid(aweme);
                Logger.printInfo(() -> "[BlueIT AdvancedFeed] aid=" + aid + " filtered=" + reason);
            }
        }

        // A completely empty FYP batch is interpreted by TikTok 46.4.3 as a feed
        // failure and shows "Something went wrong" instead of requesting the next page.
        // If the advanced filter would empty a batch solely because of the duration
        // rule, retain exactly the closest-to-range non-ad item. Base FeedItemsFilter
        // has already removed ads before this method is called, so this never restores
        // an advertisement. It turns a hard error into at most one controlled duration
        // outlier for an otherwise unusable batch.
        if (kept.isEmpty()
                && removed > 0
                && !removedForNonDurationRule
                && durationFallback != null) {
            kept.add(durationFallback);
            if (BaseSettings.DEBUG.get()) {
                String aid = durationFallbackAid;
                long durationMs = durationFallbackMs;
                Logger.printInfo(() -> "[BlueIT AdvancedFeed] retained duration fallback aid="
                        + aid + " durationMs=" + durationMs);
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
                || Settings.MIN_LIKE_VIEW_RATIO_PERCENT.get() > 0
                || terms(AdvancedFeedSettings.BLOCKED_KEYWORDS.get(), TermKind.KEYWORD).length > 0
                || terms(AdvancedFeedSettings.BLOCKED_CREATORS.get(), TermKind.CREATOR).length > 0
                || terms(AdvancedFeedSettings.BLOCKED_SOUNDS.get(), TermKind.SOUND).length > 0
                || !durationRange().isUnbounded();
    }

    private static String reason(Aweme aweme) {
        if (aweme == null) return null;

        try {
            if (Settings.HIDE_PROMOTIONAL_MUSIC.get() && aweme.isWithPromotionalMusic()) {
                return "promotional_music";
            }
        } catch (Throwable ignored) {
        }
        try {
            if (Settings.HIDE_LIVE_REPLAYS.get() && aweme.isLiveReplay()) {
                return "live_replay";
            }
        } catch (Throwable ignored) {
        }

        int minimumRatio = Settings.MIN_LIKE_VIEW_RATIO_PERCENT.get();
        if (minimumRatio > 0) {
            try {
                AwemeStatistics statistics = aweme.getStatistics();
                if (statistics != null) {
                    long views = statistics.getPlayCount();
                    long likes = statistics.getDiggCount();
                    if (views > 0L && likes * 100.0d / views < minimumRatio) {
                        return "like_view_ratio";
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        String[] keywords = terms(AdvancedFeedSettings.BLOCKED_KEYWORDS.get(), TermKind.KEYWORD);
        if (keywords.length > 0) {
            String corpus = keywordCorpus(aweme);
            if (containsAny(corpus, keywords)) return "keyword";
        }

        String[] creators = terms(AdvancedFeedSettings.BLOCKED_CREATORS.get(), TermKind.CREATOR);
        if (creators.length > 0) {
            String corpus = creatorCorpus(aweme);
            if (matchesIdentity(corpus, creators)) return "creator";
        }

        String[] sounds = terms(AdvancedFeedSettings.BLOCKED_SOUNDS.get(), TermKind.SOUND);
        if (sounds.length > 0) {
            String corpus = soundCorpus(aweme);
            if (containsAny(corpus, sounds)) return "sound";
        }

        Range duration = durationRange();
        if (!duration.isUnbounded()) {
            long durationMs = durationMilliseconds(aweme);
            if (durationMs >= 0L) {
                long minMs = secondsToMilliseconds(duration.min);
                long maxMs = duration.max == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : secondsToMilliseconds(duration.max);
                if (durationMs < minMs || durationMs > maxMs) {
                    return "duration";
                }
            }
        }

        return null;
    }

    private static String keywordCorpus(Aweme aweme) {
        StringBuilder builder = new StringBuilder();
        append(builder, invokeString(aweme, "getDesc"));
        append(builder, invokeString(aweme, "getContentDesc"));
        append(builder, readStringField(aweme, "desc"));
        append(builder, readStringField(aweme, "description"));

        Object extras = firstNonNull(invoke(aweme, "getTextExtra"), readField(aweme, "textExtra"));
        if (extras instanceof Iterable<?>) {
            int count = 0;
            for (Object extra : (Iterable<?>) extras) {
                if (count++ >= 64) break;
                append(builder, invokeString(extra, "getHashtagName"));
                append(builder, invokeString(extra, "getHashTagName"));
                append(builder, invokeString(extra, "getChallengeName"));
                append(builder, readStringField(extra, "hashtagName"));
                append(builder, readStringField(extra, "hashTagName"));
            }
        }
        return builder.toString().toLowerCase(Locale.US);
    }

    private static String creatorCorpus(Aweme aweme) {
        Object author = firstNonNull(invoke(aweme, "getAuthor"), readField(aweme, "author"));
        if (author == null) return "";
        StringBuilder builder = new StringBuilder();
        append(builder, invokeString(author, "getUniqueId"));
        append(builder, invokeString(author, "getUid"));
        append(builder, invokeString(author, "getSecUid"));
        append(builder, readStringField(author, "uniqueId"));
        append(builder, readStringField(author, "uid"));
        append(builder, readStringField(author, "secUid"));
        return normalizeIdentityCorpus(builder.toString());
    }

    private static String soundCorpus(Aweme aweme) {
        Object music = firstNonNull(invoke(aweme, "getMusic"), readField(aweme, "music"));
        if (music == null) return "";
        StringBuilder builder = new StringBuilder();
        append(builder, invokeString(music, "getIdStr"));
        append(builder, invokeString(music, "getMid"));
        append(builder, invokeString(music, "getTitle"));
        append(builder, invokeString(music, "getAuthorName"));
        append(builder, invokeString(music, "getOwnerHandle"));
        append(builder, readStringField(music, "idStr"));
        append(builder, readStringField(music, "mid"));
        append(builder, readStringField(music, "title"));
        append(builder, readStringField(music, "authorName"));
        return builder.toString().toLowerCase(Locale.US);
    }

    /**
     * Returns the exact TikTok video duration in milliseconds.
     *
     * TikTok 46.4.3 stores the JSON `duration` value in Video.videoLength (int, ms).
     * pilotLength is the exact 46.4.3 `real_duration` companion. A zero-valued
     * videoLength must not mask a positive pilotLength: Java reflection returns the
     * primitive field as a non-null boxed zero, so firstNonNull(videoLength,
     * pilotLength) incorrectly skipped the real secondary value on dev.9.
     */
    private static long durationMilliseconds(Aweme aweme) {
        Object video = firstNonNull(invoke(aweme, "getVideo"), readField(aweme, "video"));
        if (video != null) {
            long videoLength = positiveLong(readField(video, "videoLength"));
            if (videoLength > 0L) return videoLength;

            long pilotLength = positiveLong(readField(video, "pilotLength"));
            if (pilotLength > 0L) return pilotLength;

            long fallbackMs = normalizedDuration(invoke(video, "getDuration"));
            if (fallbackMs >= 0L) return fallbackMs;

            fallbackMs = normalizedDuration(readField(video, "duration"));
            if (fallbackMs >= 0L) return fallbackMs;
        }

        long awemeFallbackMs = normalizedDuration(invoke(aweme, "getDuration"));
        if (awemeFallbackMs >= 0L) return awemeFallbackMs;
        return normalizedDuration(readField(aweme, "duration"));
    }

    private static long positiveLong(Object value) {
        if (!(value instanceof Number)) return -1L;
        long number = ((Number) value).longValue();
        return number > 0L ? number : -1L;
    }

    private static long normalizedDuration(Object value) {
        return normalizeDurationToMilliseconds(value);
    }

    private static long normalizeDurationToMilliseconds(Object value) {
        if (!(value instanceof Number)) return -1L;
        long raw = ((Number) value).longValue();
        if (raw < 0L) return -1L;
        // Legacy wrappers sometimes expose seconds. Values above ten minutes are safely
        // interpreted as milliseconds; smaller fallback values are treated as seconds.
        return raw > 600L ? raw : raw * 1000L;
    }

    private static long secondsToMilliseconds(long seconds) {
        if (seconds <= 0L) return 0L;
        if (seconds >= Long.MAX_VALUE / 1000L) return Long.MAX_VALUE;
        return seconds * 1000L;
    }

    private static long durationDistanceToRange(long durationMs, Range range) {
        if (durationMs < 0L || range == null || range.isUnbounded()) return Long.MAX_VALUE;
        long minMs = secondsToMilliseconds(range.min);
        long maxMs = range.max == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : secondsToMilliseconds(range.max);
        if (durationMs < minMs) return minMs - durationMs;
        if (durationMs > maxMs && maxMs != Long.MAX_VALUE) return durationMs - maxMs;
        return 0L;
    }

    private static boolean containsAny(String corpus, String[] needles) {
        if (corpus == null || corpus.isEmpty()) return false;
        for (String needle : needles) {
            if (!needle.isEmpty() && corpus.contains(needle)) return true;
        }
        return false;
    }

    private static boolean matchesIdentity(String corpus, String[] identities) {
        if (corpus == null || corpus.isEmpty()) return false;
        String padded = " " + corpus + " ";
        for (String identity : identities) {
            String normalized = normalizeIdentity(identity);
            if (!normalized.isEmpty() && padded.contains(" " + normalized + " ")) return true;
        }
        return false;
    }

    private static String normalizeIdentityCorpus(String value) {
        if (value == null) return "";
        String[] pieces = value.toLowerCase(Locale.US).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String piece : pieces) {
            String normalized = normalizeIdentity(piece);
            if (normalized.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(normalized);
        }
        return result.toString();
    }

    private static String normalizeIdentity(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.US);
        while (normalized.startsWith("@")) normalized = normalized.substring(1);
        return normalized;
    }

    private static String[] terms(String source, TermKind kind) {
        String normalizedSource = source == null ? "" : source;
        switch (kind) {
            case KEYWORD:
                if (!normalizedSource.equals(cachedKeywordsSource)) {
                    cachedKeywords = parseTerms(normalizedSource, false);
                    cachedKeywordsSource = normalizedSource;
                }
                return cachedKeywords;
            case CREATOR:
                if (!normalizedSource.equals(cachedCreatorsSource)) {
                    cachedCreators = parseTerms(normalizedSource, true);
                    cachedCreatorsSource = normalizedSource;
                }
                return cachedCreators;
            case SOUND:
            default:
                if (!normalizedSource.equals(cachedSoundsSource)) {
                    cachedSounds = parseTerms(normalizedSource, false);
                    cachedSoundsSource = normalizedSource;
                }
                return cachedSounds;
        }
    }

    private static String[] parseTerms(String source, boolean identity) {
        if (source == null || source.trim().isEmpty()) return new String[0];
        String[] raw = source.split("[,;\\n\\r]+");
        ArrayList<String> result = new ArrayList<>();
        for (String item : raw) {
            String term = identity ? normalizeIdentity(item) : item.trim().toLowerCase(Locale.US);
            if (!term.isEmpty() && !result.contains(term)) result.add(term);
        }
        return result.toArray(new String[0]);
    }

    private static Range durationRange() {
        String source = AdvancedFeedSettings.DURATION_SECONDS.get();
        if (source == null) source = "";
        if (source.equals(cachedDurationSource)) return cachedDuration;
        cachedDuration = parseRange(source);
        cachedDurationSource = source;
        return cachedDuration;
    }

    private static Range parseRange(String source) {
        if (source == null || source.trim().isEmpty()) return new Range(0L, Long.MAX_VALUE);
        String[] pieces = source.trim().split("-", 2);
        try {
            long min = pieces[0].trim().isEmpty() ? 0L : Math.max(0L, Long.parseLong(pieces[0].trim()));
            long max = pieces.length < 2 || pieces[1].trim().isEmpty()
                    ? Long.MAX_VALUE
                    : Math.max(0L, Long.parseLong(pieces[1].trim()));
            return min <= max ? new Range(min, max) : new Range(max, min);
        } catch (NumberFormatException ignored) {
            return new Range(0L, Long.MAX_VALUE);
        }
    }

    private static String safeAid(Aweme aweme) {
        if (aweme == null) return "null";
        try { return aweme.getAid(); } catch (Throwable ignored) { return "unknown"; }
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof String ? (String) value : null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof String ? (String) value : null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(value.trim());
    }

    private enum TermKind { KEYWORD, CREATOR, SOUND }

    private static final class Range {
        final long min;
        final long max;
        Range(long min, long max) { this.min = min; this.max = max; }
        boolean isUnbounded() { return min == 0L && max == Long.MAX_VALUE; }
    }

    @FunctionalInterface
    private interface AwemeExtractor {
        Aweme extract(Object source);
    }
}
