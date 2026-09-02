package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint

private const val CONTROLLER =
    "Lapp/morphe/extension/tiktok/cleardisplay/AutomaticClearDisplayController;"

@Suppress("unused")
val automaticClearDisplayPatch = bytecodePatch(
    name = "Automatic clear display",
    description = "Experimental recovery opt-in: automatically enters TikTok clear-display mode after a configurable delay for each newly played video.",
    default = false,
) {
    dependsOn(
        sharedExtensionPatch,
        rememberClearDisplayPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4673())

    execute {
        // Keep the settings surface aware that this optional patch is installed.
        SettingsStatusLoadFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutomaticClearDisplay()V
                invoke-static {}, $CONTROLLER->enablePatch()V
            """.trimIndent(),
        )

        // Runtime activation must not depend on the user opening BlueIT settings first. The proven
        // first-frame path always runs before RememberClearDisplayPatch calls onRenderFirstFrame at
        // the method return, so enabling the controller here makes cold-start/feed behavior reliable
        // while still keeping stale preferences inert when this optional patch is not included.
        OnRenderFirstFrameFingerprint.method.addInstruction(
            0,
            "invoke-static {}, $CONTROLLER->enablePatch()V",
        )
    }
}
