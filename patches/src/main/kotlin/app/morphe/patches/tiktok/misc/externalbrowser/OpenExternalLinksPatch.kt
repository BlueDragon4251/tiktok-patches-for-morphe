/*
 * Thanks to lyyako for the original implementation and help with this patch.
 *
 * Originally adapted for TikTok 43.8.3; ported through TikTok 46.4.3:
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.externalbrowser

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructionsWithLabels
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

        SparkThirdRouterOpenFingerprint.uniqueMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static/range {p0 .. p1}, $EXTENSION_CLASS_DESCRIPTOR->openSparkThirdContext(Landroid/content/Context;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :external_browser_spark_router_original
                return-void
            """,
            ExternalLabel(
                "external_browser_spark_router_original",
                SparkThirdRouterOpenFingerprint.uniqueMethod.getInstruction(0),
            ),
        )

        StoryLinkSheetFingerprint.uniqueMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static/range {p0 .. p1}, $EXTENSION_CLASS_DESCRIPTOR->openStoryLink(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :external_browser_story_original
                return-void
            """,
            ExternalLabel(
                "external_browser_story_original",
                StoryLinkSheetFingerprint.uniqueMethod.getInstruction(0),
            ),
        )

        val superOnCreateIndex = SparkActivityOnCreateFingerprint.uniqueMethod.uniqueInstructionIndex(
            "SparkActivity super.onCreate(Bundle)") { instruction ->
            val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_SUPER && ref?.name == "onCreate" &&
                ref.parameterTypes == listOf("Landroid/os/Bundle;") && ref.returnType == "V"
        }
        SparkActivityOnCreateFingerprint.uniqueMethod.addInstructionsWithLabels(
            superOnCreateIndex + 1,
            """
                invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->openSparkActivity(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :external_browser_spark_activity_original
                return-void
            """,
            ExternalLabel(
                "external_browser_spark_activity_original",
                SparkActivityOnCreateFingerprint.uniqueMethod.getInstruction(superOnCreateIndex + 1),
            ),
        )
    }
}
