/*
 * Thanks to lyyako for the original implementation and help with this patch.
 *
 * Originally adapted for TikTok 43.8.3; ported through TikTok 46.4.3:
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.publishdate

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.shared.discovery.PublishDateContracts
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint


@Suppress("unused")
val alwaysShowPublishDatePatch = bytecodePatch(
    name = "Always show publish date",
    description = "Always shows the publish date in video author information. Thanks to lyyako for the original implementation.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAlwaysShowPublishDate()V",
        )

        VideoAuthorInfoStateFingerprint.uniqueMethod.showPostTimeForMainFeeds()
    }
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.showPostTimeForMainFeeds() {
    PublishDateContracts.gates(this).asReversed().forEach { site -> addInstructions(site.index, site.code) }
}
