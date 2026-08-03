/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.looping

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/interaction/StopVideoLoopingPatch;"

@Suppress("unused")
val stopVideoLoopingPatch = bytecodePatch(
    name = "Stop video looping",
    description = "Stops videos at the end instead of replaying them.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableStopVideoLooping()V",
        )

        listOf(
            FeedPlayRequestFingerprint.method,
            FeedPrepareNextRequestFingerprint.method,
        ).forEach { method ->
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_DESCRIPTOR->shouldStopVideoLooping()Z
                    move-result v0
                    if-eqz v0, :continue_prepare
                    const/4 v0, 0x0
                    move-object/from16 v1, p1
                    iput-boolean v0, v1, LX/0MIK;->LJIILLIIL:Z
                """,
                ExternalLabel("continue_prepare", method.getInstruction(0)),
            )
        }

    }
}
