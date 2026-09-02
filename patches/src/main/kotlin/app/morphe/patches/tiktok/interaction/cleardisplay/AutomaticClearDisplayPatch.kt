package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

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
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutomaticClearDisplay()V",
        )

        // Keep the real panel/context hook available for explicit testing, but do not include this
        // patch in the recovery default set until the real-device 46.7.3 runtime path is proven.
        ClearModePanelResetFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p1}, $CONTROLLER->updatePanelContext(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
