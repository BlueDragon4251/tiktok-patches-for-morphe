/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/PlaybackSpeedPatch.kt
 */
package app.morphe.patches.tiktok.interaction.speed

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint

@Suppress("unused")
val playbackSpeedPatch = bytecodePatch(
    name = "Playback speed",
    description = "Enables playback-speed controls for all videos and remembers the selected speed between videos.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        val selectedSpeed = GetSpeedFingerprint.uniqueMethod
        val remember = PlaybackSpeedContracts.site(selectedSpeed) { classDefByOrNull(it) }
        selectedSpeed.addInstructions(remember.index, remember.code)

        val frame = OnRenderFirstFrameFingerprint.uniqueMethod
        val restore = PlaybackSpeedContracts.frameSite(frame) { classDefByOrNull(it) }
        frame.addInstructions(restore.index, restore.code)

        // Keep the extension speed entry point available to TikTok callers.
    }
}

