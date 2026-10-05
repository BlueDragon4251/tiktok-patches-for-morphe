/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/cleardisplay/RememberClearDisplayPatch.kt
 */
package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.returnEarly

@Suppress("unused")
val rememberClearDisplayPatch = bytecodePatch(
    name = "Remember clear display",
    description = "Remembers TikTok's clear-display state between videos.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        ClearModeLogCoreFingerprint.optionalMethod?.returnEarly()
        ClearModeLogStateFingerprint.optionalMethod?.returnEarly()
        ClearModeLogPlaytimeFingerprint.optionalMethod?.returnEarly()

        val event = OnClearDisplayEventFingerprint.uniqueMethod
        val nativePanel = HookEvidence.originalClass(ClearDisplayContracts.PANEL)!!
        val eventSite = ClearDisplayContracts.eventSite(event, nativePanel)
        event.addInstructions(eventSite.index, eventSite.code)

        val frame = OnRenderFirstFrameFingerprint.uniqueMethod
        val restore = PlayerFrameContracts.restoreSite(frame, nativePanel)
        frame.addInstructions(restore.index, restore.code)
    }
}
