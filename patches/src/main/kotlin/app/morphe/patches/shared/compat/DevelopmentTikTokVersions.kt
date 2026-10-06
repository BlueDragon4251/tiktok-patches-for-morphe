package app.morphe.patches.shared.compat

import app.morphe.patcher.patch.AppTarget

/** User-authorized device test target; this is not a qualified fixture or stable compatibility. */
internal object DevelopmentTikTokVersions {
    const val version = "47.1.3"
    const val versionCode = 2024701030L
    const val sha256 = "8b5569f592a5534652ae460ef1d9e7f7394b5b7fdde44ae64f106d76767e2622"

    fun targets(development: Boolean): List<AppTarget> = if (development)
        listOf(AppTarget(version = version, versionCode = versionCode.toInt())) else emptyList()
    val targets get() = targets(ReleaseChannel.development)

    /** Permit the same portable contracts already proven by CI, for these exact bytes only. */
    fun permitsPortableTest(packageName: String, actualVersion: String, actualVersionCode: Long,
                            actualSha256: String, development: Boolean = ReleaseChannel.development): Boolean =
        development && packageName == "com.zhiliaoapp.musically" && actualVersion == version &&
            actualVersionCode == versionCode && actualSha256 == sha256
}
