package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint
import app.morphe.patches.tiktok.shared.discovery.ClearDisplayContracts

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

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        // Keep the settings surface aware that this optional patch is installed.
        SettingsStatusLoadFingerprint.uniqueMethod.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutomaticClearDisplay()V
                invoke-static {}, $CONTROLLER->enablePatch()V
            """.trimIndent(),
        )

        // Retain first-frame activation as a secondary fallback, but it is no longer the primary
        // per-video trigger on 46.7.3.
        OnRenderFirstFrameFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, $CONTROLLER->enablePatch()V",
        )

        val resetMatch = ClearModePanelResetFingerprint.uniqueMatch()
        val reset = ClearModePanelResetFingerprint.uniqueMethod
        val site = ClearDisplayContracts.resetSite(reset, resetMatch.originalClassDef)
        reset.addInstructions(site.index, site.code)
    }
}
