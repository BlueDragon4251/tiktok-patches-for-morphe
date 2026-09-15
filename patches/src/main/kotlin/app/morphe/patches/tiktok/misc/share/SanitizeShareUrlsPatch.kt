/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/share/SanitizeShareUrlsPatch.kt
 */
package app.morphe.patches.tiktok.misc.share

import app.morphe.patches.tiktok.shared.discovery.parameterRegister
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/share/ShareUrlSanitizer;"

@Suppress("unused")
val sanitizeShareUrlsPatch = bytecodePatch(
    name = "Sanitize sharing links",
    description = "Removes tracking parameters from TikTok links before they are shared.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        ShareUrlTrackerFingerprint.uniqueMethod.apply {
            val urlRegister = parameterRegister(1, "Ljava/lang/String;")

            addInstructions(
                0,
                """
                    invoke-static/range {v$urlRegister .. v$urlRegister}, $EXTENSION_CLASS_DESCRIPTOR->stripAllQueryParams(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                    return-object v$urlRegister
                """,
            )
        }
    }
}
