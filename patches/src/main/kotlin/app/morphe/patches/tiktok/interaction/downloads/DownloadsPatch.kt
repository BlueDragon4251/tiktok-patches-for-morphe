/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/downloads/DownloadsPatch.kt
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.removeInstructions
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.replaceInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.returnEarly
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/download/DownloadsPatch;"
private const val STICKER_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/download/StickerGallerySaver;"
private const val FILENAME_FORMATTER_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/download/DownloadFilenameFormatter;"
private const val ORIGINAL_PHOTO_DOWNLOADER_DESCRIPTOR = "Lapp/morphe/extension/tiktok/download/OriginalPhotoModeDownloader;"

@Suppress("unused")
val downloadsPatch = bytecodePatch(
    name = "Downloads",
    description = "Adds watermark-free downloads, comment sticker saving, configurable folders, and filename templates.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDownload()V",
        )

        AclCommonShareFingerprint.uniqueMethod.returnEarly(0)
        AclCommonShare2Fingerprint.uniqueMethod.returnEarly(2)

        AclCommonShare3Fingerprint.uniqueMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldRemoveWatermark()Z
                move-result v0
                if-eqz v0, :noremovewatermark
                const/4 v0, 0x1
                return v0
                :noremovewatermark
                nop
            """,
        )

        AwemeGetVideoFingerprint.uniqueMethod.apply {
            findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_OBJECT }.forEach { returnIndex ->
            val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
            addInstructions(
                returnIndex,
                "invoke-static/range {v$register .. v$register}, $EXTENSION_CLASS_DESCRIPTOR->patchVideoObject(Lcom/ss/android/ugc/aweme/feed/model/Video;)V",
            )
            }
        }

        HookEvidence.diagnosticCandidates("downloads.commentImage", setOf("image/jpeg", "is_pending"))
        CommentImageWatermarkFingerprint.uniqueMethod.apply {
            replaceInstruction(CommentWatermarkContracts.drawIndex(this), CommentWatermarkContracts.replacement(this))
        }

        StickerPreviewBinderFingerprint.uniqueMethod.apply {
            findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_VOID }.forEach { returnIndex ->
            addInstructions(
                returnIndex,
                "invoke-static/range {p0 .. p1}, $STICKER_EXTENSION_CLASS_DESCRIPTOR->attachSaveImageButton(Landroid/view/View;Ljava/lang/Object;)V",
            )
            }
        }

        val stickerPreviewBinderMethod = StickerPreviewBinderFingerprint.uniqueMethod
        val sourceMatch = StickerPreviewSourceFingerprint.uniqueMatch()
        HookEvidence.stickerSourceTarget(sourceMatch.originalMethod, stickerPreviewBinderMethod)
        sourceMatch.method.apply {
            StickerSourceContracts.sites(this, stickerPreviewBinderMethod).asReversed().forEach { site ->
                addInstructions(site.index, site.code)
            }
        }

        DownloadSuccessCoroutineFingerprint.uniqueMethod.apply {
            addInstructions(0, DownloadSuccessContracts.code(this) { classDefByOrNull(it) })
        }

        DownloadUriFingerprint.uniqueMethod.apply {
            findInstructionIndicesReversedOrThrow {
                getReference<FieldReference>().let { ref ->
                    ref?.definingClass == "Landroid/os/Environment;" && ref.name.startsWith("DIRECTORY_")
                }
            }.forEach { fieldIndex ->
                val pathRegister = getInstruction<OneRegisterInstruction>(fieldIndex).registerA
                val builderRegister = getInstruction<FiveRegisterInstruction>(fieldIndex + 1).registerC
                removeInstructions(fieldIndex, 4)
                addInstructions(
                    fieldIndex,
                    """
                        invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->getDownloadPath()Ljava/lang/String;
                        move-result-object v$pathRegister
                        invoke-virtual { v$builderRegister, v$pathRegister }, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                    """,
                )
            }
        }
    }
}
