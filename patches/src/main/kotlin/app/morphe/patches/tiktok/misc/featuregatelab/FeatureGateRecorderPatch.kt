/*
 * Copyright 2026 BlueIT contributors
 */
package app.morphe.patches.tiktok.misc.featuregatelab

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

@Suppress("unused")
val featureGateRecorderPatch = bytecodePatch(
    name = "Feature Gate Recorder",
    description = "Adds BlueIT Learn mode for recording newly observed TikTok configuration candidates while using a feature.",
    default = true,
) {
    dependsOn(featureGateLabPatch)
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableFeatureGateRecorder()V",
        )
    }
}
