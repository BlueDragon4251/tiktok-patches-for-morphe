/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/spoof/sim/SpoofSimPatch.kt
 */

package app.morphe.patches.tiktok.misc.spoof.sim

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.tiktok.shared.discovery.FrameworkCallContracts


@Suppress("unused")
val simSpoofPatch = bytecodePatch(
    name = "SIM spoof",
    description = "Spoofs SIM country and operator information retrieved by TikTok, with country presets for easier setup.",
    default = true,
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        var siteCount = 0
        classDefForEach { owner ->
            owner.methods.forEach { source ->
                val sites = FrameworkCallContracts.simSites(source)
                if (sites.isNotEmpty()) {
                    val method = mutableClassDefBy(owner).findMutableMethodOf(source)
                    sites.asReversed().forEach { site -> method.addInstructions(site.index, site.code) }
                    siteCount += sites.size
                }
            }
        }
        if (siteCount == 0) throw PatchException("No typed Android telephony results for SIM spoof")
        println("[BlueIT Framework Contract] SIM spoof: $siteCount typed results")

        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSimSpoof()V",
        )
    }
}
