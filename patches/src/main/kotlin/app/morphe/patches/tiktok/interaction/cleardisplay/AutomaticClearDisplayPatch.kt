package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
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
        // Mark both the settings surface and the runtime controller as installed. The actual
        // per-video trigger lives in RememberClearDisplayPatch's proven first-frame hook and passes
        // only an event class name String into extension code. No ClearModePanel/Rv0 bytecode hook
        // remains in this patch.
        SettingsStatusLoadFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutomaticClearDisplay()V
                invoke-static {}, $CONTROLLER->enablePatch()V
            """.trimIndent(),
        )
    }
}
