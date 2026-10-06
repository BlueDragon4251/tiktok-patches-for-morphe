package app.morphe.patches.tiktok

import app.morphe.patches.shared.compat.DevelopmentTikTokVersions as Dev
import org.junit.Assert.*
import org.junit.Test

class DevelopmentTikTokVersionsTest {
    @Test fun stableBundlesNeitherListNorAuthorizeTheUnqualifiedTarget() {
        assertTrue(Dev.targets(false).isEmpty())
        assertFalse(Dev.permitsPortableTest("com.zhiliaoapp.musically", Dev.version, Dev.versionCode, Dev.sha256, false))
    }
    @Test fun developmentBundlesRequireTheEntirePinnedApkIdentity() {
        assertEquals(1, Dev.targets(true).size)
        assertTrue(Dev.permitsPortableTest("com.zhiliaoapp.musically", Dev.version, Dev.versionCode, Dev.sha256, true))
        assertFalse(Dev.permitsPortableTest("com.zhiliaoapp.musically", Dev.version, Dev.versionCode, "0".repeat(64), true))
        assertFalse(Dev.permitsPortableTest("com.zhiliaoapp.musically", "47.1.4", Dev.versionCode, Dev.sha256, true))
        assertFalse(Dev.permitsPortableTest("com.zhiliaoapp.musically", Dev.version, Dev.versionCode + 1, Dev.sha256, true))
        assertFalse(Dev.permitsPortableTest("com.ss.android.ugc.trill", Dev.version, Dev.versionCode, Dev.sha256, true))
    }
}
