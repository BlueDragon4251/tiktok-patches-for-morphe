package app.morphe.patches.tiktok.misc.comment

import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private val clipboardTextHelperFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/content/Context;", "Lcom/bytedance/bpea/basics/Cert;"),
    custom = { method, owner -> CommentCopyContracts.helperBoundary(method, owner) },
)

@Suppress("unused")
val copyCommentsWithoutUsernamePatch = bytecodePatch(
    name = "Copy comments without username",
    description = "Copies only the comment text without including the creator's username.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktokVerified())
    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCopyCommentsWithoutUsername()V")
        // Read-only discovery does not authorize editing this relocated helper.
        val nativeHelper = clipboardTextHelperFingerprint.uniqueReadOnlyOriginalMethod
        val helper = ImmutableMethodReference(nativeHelper.definingClass, nativeHelper.name, nativeHelper.parameterTypes, nativeHelper.returnType)
        val callers = Fingerprint(custom = { method, _ ->
            method.implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.toString() == helper.toString()
            } == true && runCatching { CommentCopyContracts.sites(method, helper) }.isSuccess
        })
        callers.allMatches(3..3).forEach { match ->
            val method = match.method
            CommentCopyContracts.sites(method, helper).asReversed().forEach { site -> method.addInstructions(site.index, site.code) }
        }
    }
}
