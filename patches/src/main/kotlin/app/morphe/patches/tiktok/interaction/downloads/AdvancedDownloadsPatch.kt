/*
 * Copyright 2026 BlueIT contributors
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

@Suppress("unused")
val advancedDownloadsPatch = bytecodePatch(
    name = "Download quality selector",
    description = "Selects automatic, highest, or target video quality and lets BlueIT choose the preferred TikTok download stream.",
    default = true,
) {
    dependsOn(downloadsPatch)
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAdvancedDownloads()V",
        )
    }
}
