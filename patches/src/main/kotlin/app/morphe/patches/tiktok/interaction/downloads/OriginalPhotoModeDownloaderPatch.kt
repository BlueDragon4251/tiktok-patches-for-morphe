package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

@Suppress("unused")
val originalPhotoModeDownloaderPatch = bytecodePatch(
    name = "Original Photo Mode downloader",
    description = "Downloads the original Photo Mode CDN assets instead of keeping TikTok's rendered copies when enabled.",
    default = true,
) {
    dependsOn(downloadsPatch)
    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableOriginalPhotoModeDownloader()V",
        )
    }
}
