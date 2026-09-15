/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/PlaybackSpeedPatch.kt
 */
package app.morphe.patches.tiktok.interaction.speed

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val playbackSpeedPatch = bytecodePatch(
    name = "Playback speed",
    description = "Enables playback-speed controls for all videos and remembers the selected speed between videos.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        GetSpeedFingerprint.uniqueMethod.apply {
            // selected speed * native content multiplier feeds the stable setter.
            val setIndex = uniqueInstructionIndex("Native speed setter") { it.getReference<MethodReference>()?.toString() == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;->setSpeed(F)V" }
            val body = implementation!!.instructions
            val multiply = body.getOrNull(setIndex - 1) as? TwoRegisterInstruction
                ?: throw PatchException("Playback speed: expected multiply before setSpeed")
            if (multiply.opcode != Opcode.MUL_FLOAT_2ADDR || body[setIndex].argumentRegisters().last() != multiply.registerA) throw PatchException("Playback speed: changed selected-speed multiplier contract")
            val register = multiply.registerA
            val injectIndex = setIndex - 1

            addInstruction(
                injectIndex,
                "invoke-static { v$register }, " +
                    "Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;->rememberPlaybackSpeed(F)V",
            )
        }

        OnRenderFirstFrameFingerprint.uniqueMethod.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;->getPlaybackSpeed()F
                move-result v0
                invoke-virtual {p0, v0}, Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;->setSpeed(F)V
            """,
        )

        // Keep the extension speed entry point available to TikTok callers.
    }
}

