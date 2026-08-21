package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.AdvancedFeedSettings;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary TikTok 46.4.3 runtime probe for the device-confirmed FYP duration/ad bypass.
 *
 * The normal feed diagnostics are gated behind the global debug setting, while the
 * exported Morphe diagnostic report is also used with debug disabled. These probes are
 * deliberately INFO-level and heavily rate limited so a user export can prove which
 * FeedItemList instance and which Aweme metadata reached the real runtime path.
 *
 * Remove this class once the device trace identifies the production hook/metadata fix.
 */
public final class FeedRuntimeDiagnostics {
    private static final int MAX_EVENTS = 180;
    private static final int MAX_SAMPLE_ITEMS = 6;
    private static final int MAX_AD_FIELDS = 10;
    private static final AtomicInteger EVENTS = new AtomicInteger();

    private FeedRuntimeDiagnostics() {
    }

    public static void traceList(String stage, FeedItemList feedItemList, boolean marked) {
        if (!claimEvent()) return;

        List<?> items = feedItemList == null ? null : feedItemList.items;
        int listId = feedItemList == null ? 0 : System.identityHashCode(feedItemList);
        int itemsId = items == null ? 0 : System.identityHashCode(items);
        int size = items == null ? -1 : items.size();
        int preloadAds = collectionSize(firstNonNull(
                invoke(feedItemList, "getPreloadAds"),
                readField(feedItemList, "preloadAds")
        ));
        String durationRange = AdvancedFeedSettings.DURATION_SECONDS.get();
        boolean removeAds = Settings.REMOVE_ADS.get();
        String sample = sampleItems(items);

        Logger.printInfo(() -> "[BlueIT FeedRuntime]"
                + " stage=" + stage
                + " list=" + listId
                + " items=" + itemsId
                + " marked=" + marked
                + " size=" + size
                + " preloadAds=" + preloadAds
                + " removeAds=" + removeAds
                + " durationRange=\"" + durationRange + "\""
                + " sample=\"" + sample + "\"");
    }

    public static void traceAdDecision(Aweme item, boolean filtered) {
        if (!Settings.REMOVE_ADS.get() || !claimEvent()) return;
        String details = describeAweme(item, true);
        Logger.printInfo(() -> "[BlueIT FeedRuntime] adDecision="
                + (filtered ? "FILTER" : "KEEP")
                + " " + details);
    }

    private static String sampleItems(List<?> items) {
        if (items == null || items.isEmpty()) return "empty";
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Object value : items) {
            if (!(value instanceof Aweme)) continue;
            if (count++ >= MAX_SAMPLE_ITEMS) break;
            if (builder.length() > 0) builder.append(" || ");
            builder.append(describeAweme((Aweme) value, false));
        }
        if (builder.length() == 0) {
            Object first = items.get(0);
            return "nonAweme:" + (first == null ? "null" : first.getClass().getName());
        }
        return builder.toString();
    }

    private static String describeAweme(Aweme aweme, boolean includeAdFields) {
        if (aweme == null) return "aweme=null";

        String aid;
        try {
            aid = aweme.getAid();
        } catch (Throwable ignored) {
            aid = "unknown";
        }

        Object video = firstNonNull(invoke(aweme, "getVideo"), readField(aweme, "video"));
        Object videoLength = readField(video, "videoLength");
        Object pilotLength = readField(video, "pilotLength");
        Object fallbackDuration = firstNonNull(
                invoke(video, "getDuration"),
                readField(video, "duration"),
                invoke(aweme, "getDuration"),
                readField(aweme, "duration")
        );
        long durationMs = exactDurationMs(videoLength, pilotLength, fallbackDuration);

        boolean ad = safeBoolean(aweme, "isAd");
        boolean softAd = safeBoolean(aweme, "isSoftAd");
        boolean promo = safeBoolean(aweme, "isWithPromotionalMusic");
        boolean paidContent = truthy(aweme, "isPaidContent", "isPaidContent");
        boolean paidInfo = nonNull(aweme, "getMPaidContentInfo", "mPaidContentInfo");
        boolean rawAd = nonNull(aweme, "getAwemeRawAd", "awemeRawAd");
        boolean linkAd = nonNull(aweme, "getLinkAdData", "linkAdData");
        boolean commercial = nonEmptyString(aweme, "getCommercialVideoInfo", "commercialVideoInfo");
        long adSource = numberValue(firstNonNull(invoke(aweme, "getAdAwemeSource"), readField(aweme, "adAwemeSource")));
        long adLinkType = numberValue(firstNonNull(invoke(aweme, "getAdLinkType"), readField(aweme, "adLinkType")));
        String adVideoId = stringValue(readField(video, "adVideoId"));
        String videoAdTag = stringValue(readField(video, "videoAdTag"));

        StringBuilder result = new StringBuilder();
        result.append("aid=").append(aid)
                .append(" durationMs=").append(durationMs)
                .append(" videoLength=").append(numberValue(videoLength))
                .append(" pilotLength=").append(numberValue(pilotLength))
                .append(" fallbackDuration=").append(numberValue(fallbackDuration))
                .append(" ad=").append(ad)
                .append(" softAd=").append(softAd)
                .append(" promo=").append(promo)
                .append(" paidContent=").append(paidContent)
                .append(" paidInfo=").append(paidInfo)
                .append(" rawAd=").append(rawAd)
                .append(" linkAd=").append(linkAd)
                .append(" commercial=").append(commercial)
                .append(" adSource=").append(adSource)
                .append(" adLinkType=").append(adLinkType)
                .append(" adVideoId=").append(clip(adVideoId, 48))
                .append(" videoAdTag=").append(clip(videoAdTag, 48));

        if (includeAdFields) {
            result.append(" adFields=").append(meaningfulAdFields(aweme));
        }
        return result.toString();
    }

    private static String meaningfulAdFields(Object target) {
        if (target == null) return "none";
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                if (count >= MAX_AD_FIELDS) break;
                String lower = field.getName().toLowerCase(Locale.US);
                if (!isAdFieldName(lower)) continue;
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(target);
                } catch (Throwable ignored) {
                    continue;
                }
                String summary = meaningfulValue(value);
                if (summary == null) continue;
                if (builder.length() > 0) builder.append(',');
                builder.append(field.getName()).append('=').append(summary);
                count++;
            }
            if (count >= MAX_AD_FIELDS) break;
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static boolean isAdFieldName(String lower) {
        return lower.contains("advert")
                || lower.contains("sponsor")
                || lower.contains("promot")
                || lower.contains("commercial")
                || lower.contains("paid")
                || lower.contains("awemerawad")
                || lower.contains("linkad")
                || lower.contains("nativead")
                || lower.contains("adinfo")
                || lower.contains("adlabel")
                || lower.contains("adsource")
                || lower.contains("adlink")
                || lower.equals("isad")
                || lower.contains("softad");
    }

    private static String meaningfulValue(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value ? "true" : null;
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return number == 0L ? null : Long.toString(number);
        }
        if (value instanceof CharSequence) {
            String string = value.toString().trim();
            return string.isEmpty() ? null : '"' + clip(string, 64) + '"';
        }
        if (value instanceof List<?>) {
            int size = ((List<?>) value).size();
            return size == 0 ? null : "List(" + size + ")";
        }
        return value.getClass().getSimpleName();
    }

    private static long exactDurationMs(Object videoLength, Object pilotLength, Object fallbackDuration) {
        long exact = numberValue(videoLength);
        if (exact > 0L) return exact;
        long pilot = numberValue(pilotLength);
        if (pilot > 0L) return pilot;
        long fallback = numberValue(fallbackDuration);
        if (fallback < 0L) return -1L;
        return fallback > 600L ? fallback : fallback * 1000L;
    }

    private static int collectionSize(Object value) {
        return value instanceof List<?> ? ((List<?>) value).size() : -1;
    }

    private static boolean safeBoolean(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean truthy(Object target, String methodName, String fieldName) {
        Object value = firstNonNull(invoke(target, methodName), readField(target, fieldName));
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean nonNull(Object target, String methodName, String fieldName) {
        return firstNonNull(invoke(target, methodName), readField(target, fieldName)) != null;
    }

    private static boolean nonEmptyString(Object target, String methodName, String fieldName) {
        return !stringValue(firstNonNull(invoke(target, methodName), readField(target, fieldName))).isEmpty();
    }

    private static long numberValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
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

    private static Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isEmpty()) return "-";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static boolean claimEvent() {
        return EVENTS.getAndIncrement() < MAX_EVENTS;
    }
}
