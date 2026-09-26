/*
 * Thanks to lyyako for the original implementation and help with this patch.
 *
 * Originally adapted for TikTok 43.8.3; ported through TikTok 46.4.3:
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.publishdate

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.shared.discovery.uniqueInstructionIndex
import app.morphe.patches.tiktok.shared.discovery.singleOrThrow
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/publishdate/AlwaysShowPublishDatePatch;"

@Suppress("unused")
val alwaysShowPublishDatePatch = bytecodePatch(
    name = "Always show publish date",
    description = "Always shows the publish date in video author information. Thanks to lyyako for the original implementation.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAlwaysShowPublishDate()V",
        )

        VideoAuthorInfoStateFingerprint.uniqueMethod.showPostTimeForMainFeeds()
    }
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.showPostTimeForMainFeeds() {
    val instructions = implementation!!.instructions
    val regionStart = uniqueInstructionIndex("Video author post-time v3 marker") { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        reference is StringReference && reference.string == "v3"
    }
    val regionEnd = instructions.withIndex().filter { (index, instruction) ->
        if (index <= regionStart) return@filter false
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.name == "getCreateTime" &&
            reference.parameterTypes.isEmpty() &&
            reference.returnType == "J" &&
            instructions.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_WIDE &&
            // The visibility gate compares the timestamp; the later formatting
            // path also reads it, but multiplies it for display instead.
            instructions.subList(index + 2, minOf(index + 6, instructions.size))
                .any { it.opcode == Opcode.CMP_LONG }
    }.map { it.index }.singleOrThrow("Video author timestamp comparison after v3 marker in $this")

    val gateCallIndices = instructions.withIndex()
        .filter { (index, instruction) ->
            index in regionStart..regionEnd && instruction.isStaticStringBooleanCall()
        }
        .filter { (index, _) -> getInstruction(index + 1).opcode == Opcode.MOVE_RESULT }
        .map { it.index }

    if (gateCallIndices.size != 5) throw PatchException(
        "Expected five video author post-time visibility gates, found ${gateCallIndices.size} in $this")

    gateCallIndices.asReversed().forEach { index ->
        val resultRegister = getInstruction<OneRegisterInstruction>(index + 1).registerA
        addInstructions(
            index + 2,
            """
                invoke-static/range {v$resultRegister .. v$resultRegister}, $EXTENSION_CLASS_DESCRIPTOR->showPostTimeForMainFeeds(Z)Z
                move-result v$resultRegister
            """,
        )
    }
}

private fun Any.isStaticStringBooleanCall(): Boolean {
    if ((this as? com.android.tools.smali.dexlib2.iface.instruction.Instruction)?.opcode != Opcode.INVOKE_STATIC) {
        return false
    }

    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.returnType == "Z" &&
        reference.parameterTypes == listOf("Ljava/lang/String;")
}
