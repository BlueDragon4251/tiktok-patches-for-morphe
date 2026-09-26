package app.morphe.patches.tiktok.interaction.offlinevideos

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HELPER = "Lapp/morphe/extension/tiktok/offline/CustomOfflineVideosLimitPatch;"

@Suppress("unused")
val customOfflineVideosLimitPatch = bytecodePatch(
    name = "Custom offline videos limit",
    description = "Adds a custom entry to TikTok's offline videos menu with a configurable limit of up to 500 videos.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktokVerified())
    execute {
        OfflineModeSheetOptionsFingerprint.uniqueMethod.apply {
            val call = uniqueInstructionIndex("Offline sheet options list") { i ->
                i.opcode == Opcode.INVOKE_STATIC && i.getReference<MethodReference>()?.let {
                    it.parameterTypes == listOf("[Ljava/lang/Object;") && it.returnType == "Ljava/util/List;"
                } == true
            }
            val register = resultRegister(call, "Ljava/util/List;")
            addInstructions(call + 2, """
                invoke-static/range {v$register .. v$register}, $HELPER->getOfflineVideoOptions(Ljava/util/List;)Ljava/util/List;
                move-result-object v$register
            """)
        }
        // Follow the actual sheet provider. LJ/LJFF list-field names alone matched
        // 36 unrelated classes on the accepted APK, including analytics config.
        val sheet = OfflineModeSheetOptionsFingerprint.uniqueOriginalClassDef
        val provider = sheet.methods.flatMap { method ->
            method.implementation?.instructions?.mapNotNull { i ->
                i.getReference<MethodReference>()?.takeIf { i.opcode == Opcode.INVOKE_STATIC &&
                    it.parameterTypes.isEmpty() && it.returnType == "Ljava/util/List;" && it.definingClass != sheet.type }
            }.orEmpty()
        }.distinctBy { it.toString() }.filter { ref ->
            classDefByOrNull(ref.definingClass)?.methods?.any { method -> method.name == "<clinit>" &&
                method.implementation?.instructions?.any { i -> i.opcode == Opcode.SPUT_OBJECT && i.getReference<FieldReference>()?.type == "Ljava/util/List;" } == true
            } == true
        }.singleOrThrow("Offline sheet list provider")
        TikTokFingerprint(definingClass = provider.definingClass, name = provider.name,
            parameters = emptyList(), returnType = "Ljava/util/List;", id = "offline.native-options-provider").uniqueMethod.apply {
            findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(index, """
                    invoke-static/range {v$register .. v$register}, $HELPER->getOfflineVideoOptions(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$register
                """)
            }
        }
        OfflineModeOptionEnumFingerprint.uniqueMethod.apply {
            val fieldIndex = uniqueInstructionIndex("Custom offline enum field") { i ->
                i.opcode == Opcode.SPUT_OBJECT && i.getReference<FieldReference>()?.let {
                    it.definingClass == definingClass && it.type == definingClass && it.name == "DOWNLOAD_200_VIDEOS"
                } == true
            }
            val callIndex = fieldIndex - 1
            val call = getInstruction(callIndex)
            val ref = call.getReference<MethodReference>()
            val args = call.argumentRegisters()
            val stored = getInstruction<OneRegisterInstruction>(fieldIndex).registerA
            if (call.opcode !in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) || ref?.definingClass != definingClass || ref.name != "<init>" ||
                ref.parameterTypes != listOf("Ljava/lang/String;", "I", "I", "I", "I") || args.size != 6 || args[0] != stored) throw PatchException("Offline enum constructor does not feed DOWNLOAD_200_VIDEOS")
            val limit = args[3]; val minutes = args[4]; val size = args[5]
            addInstructions(callIndex, """
                invoke-static/range {v$limit .. v$limit}, $HELPER->getCustomOfflineVideoLimitOrOriginal(I)I
                move-result v$limit
                invoke-static/range {v$minutes .. v$minutes}, $HELPER->getCustomOfflineVideoMinutesOrOriginal(I)I
                move-result v$minutes
                invoke-static/range {v$size .. v$size}, $HELPER->getCustomOfflineVideoSizeMbOrOriginal(I)I
                move-result v$size
            """)
        }
    }
}
