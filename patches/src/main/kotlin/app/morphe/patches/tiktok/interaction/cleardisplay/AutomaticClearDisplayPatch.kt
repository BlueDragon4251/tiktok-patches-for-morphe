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
    description = "Automatically enters TikTok clear-display mode after a configurable delay for each newly played video.",
    default = true,
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

        // Capture TikTok's real ClearModePanelComponent and current item context. The 46.7.3
        // controller first attempts TikTok's native request route and falls back to the current
        // per-video clear-display event when the old PINCH_ZOOM enum is obfuscated/removed.
        ClearModePanelResetFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p1}, $CONTROLLER->updatePanelContext(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
