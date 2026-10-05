/*
 * Thanks to lyyako for the original implementation and help with this patch.
 *
 * Originally adapted for TikTok 43.8.3; ported through TikTok 46.4.3:
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.externalbrowser

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.ExternalBrowserContracts
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.shared.discovery.uniqueInstructionIndex
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/externalbrowser/ExternalBrowserPatch;"

@Suppress("unused")
val openExternalLinksPatch = bytecodePatch(
    name = "Open external links directly",
    description = "Opens profile and story website links in the system browser instead of TikTok's in-app browser. Thanks to lyyako for the original implementation.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableExternalBrowser()V",
        )

        listOf(SparkThirdRouterOpenFingerprint, StoryLinkSheetFingerprint, SparkActivityOnCreateFingerprint).forEach { fingerprint ->
            val match = fingerprint.uniqueMatch()
            val method = fingerprint.uniqueMethod
            val site = ExternalBrowserContracts.site(method, match.originalClassDef) { classDefByOrNull(it) }
            method.addInstructions(site.index, site.code)
        }
    }
}
