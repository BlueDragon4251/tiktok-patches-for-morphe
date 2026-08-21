package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AdsFilter implements IFilter {
    @Override
    public boolean getEnabled() {
        return Settings.REMOVE_ADS.get();
    }

    @Override
    public boolean getFiltered(Aweme item) {
        if (item == null) return false;

        // Native flags present on the exact TikTok 46.4.3 Aweme model.
        try {
            if (item.isAd() || item.isSoftAd() || item.isWithPromotionalMusic()) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Exact 46.4.3 discovery shows additional advertising/paid-content signals that
        // are not guaranteed to flip isAd()/isSoftAd() on every rendered feed card.
        if (truthy(item, "isPaidContent", "isPaidContent")) return true;
        if (nonNull(item, "getMPaidContentInfo", "mPaidContentInfo")) return true;
        if (nonNull(item, "getAwemeRawAd", "awemeRawAd")) return true;
        if (nonNull(item, "getLinkAdData", "linkAdData")) return true;
        if (nonEmptyString(item, "getCommercialVideoInfo", "commercialVideoInfo")) return true;
        if (nonZeroNumber(item, "getAdAwemeSource", "adAwemeSource")) return true;
        if (nonZeroNumber(item, "getAdLinkType", "adLinkType")) return true;

        // The exact 46.4.3 Video model also carries adVideoId/videoAdTag. These are useful
        // for cards assembled later from preload/client-side ad paths.
        Object video = firstNonNull(invoke(item, "getVideo"), readField(item, "video"));
        if (video != null) {
            if (nonEmptyString(video, null, "adVideoId")) return true;
            if (nonEmptyString(video, null, "videoAdTag")) return true;
        }

        // Do not use hasEverAdvertised/promoteModel as hard blockers: those can describe
        // an ordinary creator post that was promoted historically rather than this view
        // being an actual paid ad. Unknown model changes therefore remain fail-open.
        return false;
    }

    private static boolean truthy(Object target, String methodName, String fieldName) {
        Object value = firstNonNull(invoke(target, methodName), readField(target, fieldName));
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean nonNull(Object target, String methodName, String fieldName) {
        return firstNonNull(invoke(target, methodName), readField(target, fieldName)) != null;
    }

    private static boolean nonZeroNumber(Object target, String methodName, String fieldName) {
        Object value = firstNonNull(invoke(target, methodName), readField(target, fieldName));
        return value instanceof Number && ((Number) value).longValue() != 0L;
    }

    private static boolean nonEmptyString(Object target, String methodName, String fieldName) {
        Object value = firstNonNull(invoke(target, methodName), readField(target, fieldName));
        return value instanceof String && !((String) value).trim().isEmpty();
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
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }
}
