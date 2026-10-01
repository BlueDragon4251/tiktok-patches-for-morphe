package app.morphe.patches.tiktok.misc.translation

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.getReference
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/translation/CommentBatchTranslator;"

@Suppress("unused")
val commentTranslationPatch = bytecodePatch(
    name = "Translate comments",
    description = "Adds comment translation controls using TikTok's translation system, with selectable language exclusions.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCommentTranslation()V",
        )

        BaseCommentCellBindFingerprint.uniqueMethod.apply {
            val instructions = implementation!!.instructions
            val managerMatch = instructions.withIndex().mapNotNull { (index, instruction) ->
                val field = instruction.getReference<FieldReference>()
                    ?: return@mapNotNull null
                if (instruction.opcode != Opcode.IPUT_OBJECT ||
                    field.type != "Lcom/ss/android/ugc/aweme/comment/model/Comment;" ||
                    "Landroidx/lifecycle/Observer;" !in classDefBy(field.definingClass).interfaces ||
                    instruction !is TwoRegisterInstruction
                ) {
                    return@mapNotNull null
                }

                val managerRegister = instruction.registerB
                var matchingWrites = 0
                var lastWriteIndex = index
                val searchEnd = (index + 6).coerceAtMost(instructions.lastIndex)
                for (candidateIndex in (index + 1)..searchEnd) {
                    val candidate = instructions[candidateIndex]
                    val candidateField = candidate.getReference<FieldReference>()
                    if (candidate.opcode == Opcode.IPUT_OBJECT &&
                        candidate is TwoRegisterInstruction &&
                        candidate.registerB == managerRegister &&
                        candidateField?.definingClass == field.definingClass
                    ) {
                        matchingWrites++
                        lastWriteIndex = candidateIndex
                    }
                }

                if (matchingWrites >= 2) lastWriteIndex to managerRegister else null
            }.distinct().singleOrThrow("Translate comments initialized observer")
            val (managerReadyIndex, managerRegister) = managerMatch

            addInstructions(
                managerReadyIndex + 1,
                """
                    move-object/from16 v0, p0
                    iget-object v0, v0, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                    invoke-static {v0, v$managerRegister}, $EXTENSION_CLASS_DESCRIPTOR->registerCommentCell(Landroid/view/View;Ljava/lang/Object;)V
                """,
            )
        }

        CommentListLoadedFingerprint.uniqueMethod.apply {
            val responseReadyIndex = uniqueInstructionIndex("Loaded comment response") { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT && instruction.getReference<FieldReference>()?.let {
                    it.definingClass == "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;" && it.name == "lazySplitItemsParseTask"
                } == true
            }
            val responseRegister = getInstruction<TwoRegisterInstruction>(responseReadyIndex).registerB

            addInstruction(
                responseReadyIndex,
                "invoke-static/range {v$responseRegister .. v$responseRegister}, $EXTENSION_CLASS_DESCRIPTOR->onCommentListLoaded(Ljava/lang/Object;)V",
            )
        }

        MultiCommentTranslationStartFingerprint.uniqueMethod.apply {
            val start = parameterRegister(0, "Ljava/util/List;")
            val end = parameterRegister(2, "Z")
            if (end != start + 2) throw PatchException("Batch translation requires three contiguous argument words")
            addInstruction(0, "invoke-static/range {v$start .. v$end}, $EXTENSION_CLASS_DESCRIPTOR->onNativeBatchStart(Ljava/lang/Object;Ljava/lang/Object;Z)V")
        }

        MultiCommentTranslationCompleteFingerprint.uniqueMethod.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->onNativeBatchComplete(Ljava/lang/Object;)V
            """,
        )
    }
}
