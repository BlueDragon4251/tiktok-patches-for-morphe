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
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.returnEarly
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
                "invoke-static {v$register}, $EXTENSION_CLASS_DESCRIPTOR->patchVideoObject(Lcom/ss/android/ugc/aweme/feed/model/Video;)V",
            )
            }
        }

        CommentImageWatermarkFingerprint.uniqueMethod.apply {
            val drawBitmapIndex = findInstructionIndicesReversedOrThrow {
                opcode.name == "invoke-virtual" &&
                    this is ReferenceInstruction &&
                    reference.toString().contains("->drawBitmap(Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V")
            }.first()
            val drawInstr = getInstruction<FiveRegisterInstruction>(drawBitmapIndex)
            val canvasReg = drawInstr.registerC
            val bitmapReg = drawInstr.registerD
            val xReg = drawInstr.registerE
            val yReg = drawInstr.registerF
            val paintReg = drawInstr.registerG
            removeInstructions(drawBitmapIndex, 1)
            addInstructionsWithLabels(
                drawBitmapIndex,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldRemoveWatermark()Z
                    move-result v$xReg
                    if-nez v$xReg, :skip_watermark
                    const/4 v$xReg, 0x0
                    invoke-virtual {v$canvasReg, v$bitmapReg, v$xReg, v$yReg, v$paintReg}, Landroid/graphics/Canvas;->drawBitmap(Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V
                    :skip_watermark
                    nop
                """,
            )
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
        StickerPreviewSourceFingerprint.uniqueMethod.apply {
            val bindCallIndices = implementation!!.instructions.withIndex()
                .filter { (_, instruction) ->
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == stickerPreviewBinderMethod.definingClass &&
                            reference.name == stickerPreviewBinderMethod.name &&
                            reference.returnType == "V" &&
                            reference.parameterTypes.size == 4 &&
                            reference.parameterTypes[1] == "Z" &&
                            reference.parameterTypes[2] == "Ljava/lang/String;" &&
                            reference.parameterTypes[3] == "Ljava/util/Map;"
                    } == true
                }
                .map { it.index }
                .toList()

            if (bindCallIndices.isEmpty()) {
                throw app.morphe.patcher.patch.PatchException("Downloads: could not find resolved sticker preview bind calls.")
            }

            bindCallIndices.asReversed().forEach { bindCallIndex ->
                val bindInstruction = implementation!!.instructions[bindCallIndex]
                val previewRegister = when (bindInstruction) {
                    is FiveRegisterInstruction -> bindInstruction.registerD
                    is RegisterRangeInstruction -> bindInstruction.startRegister + 1
                    else -> throw app.morphe.patcher.patch.PatchException("Downloads: unsupported sticker preview bind instruction.")
                }
                val registerProvider = getFreeRegisterProvider(bindCallIndex, 2, previewRegister)
                val previewTempRegister = registerProvider.getFreeRegister()
                val sourceTempRegister = registerProvider.getFreeRegister()
                if (previewTempRegister > 15 || sourceTempRegister > 15) {
                    throw app.morphe.patcher.patch.PatchException("Downloads: could not allocate low registers for sticker source association.")
                }
                addInstructions(
                    bindCallIndex,
                    """
                        move-object/from16 v$previewTempRegister, v$previewRegister
                        move-object/from16 v$sourceTempRegister, p2
                        invoke-static {v$previewTempRegister, v$sourceTempRegister}, $STICKER_EXTENSION_CLASS_DESCRIPTOR->registerStickerSource(Ljava/lang/Object;Ljava/lang/Object;)V
                    """,
                )
            }
        }

        DownloadSuccessCoroutineFingerprint.uniqueMethod.apply {
            val fieldReferences = implementation!!.instructions.mapNotNull { it.getReference<FieldReference>() }
            val body = implementation!!.instructions
            val pathField = body.withIndex().mapNotNull { (index, instruction) ->
                val ref = instruction.getReference<MethodReference>()
                if (ref?.name != "<init>" || ref.parameterTypes != listOf("Ljava/lang/String;")) return@mapNotNull null
                val owner = classDefByOrNull(ref.definingClass)
                if (ref.definingClass != "Ljava/io/File;" && owner?.superclass != "Ljava/io/File;") return@mapNotNull null
                val read = body.getOrNull(index - 1)
                val field = read?.getReference<FieldReference>()
                field?.takeIf { it.definingClass == definingClass && it.type == "Ljava/lang/String;" &&
                    read.opcode == Opcode.IGET_OBJECT && (read as OneRegisterInstruction).registerA == instruction.argumentRegisters().last() }
            }.distinctBy { it.toString() }.singleOrThrow("Download success path field")
            val awemeField = fieldReferences.filter { it.definingClass == definingClass && it.type == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" }
                .distinctBy { it.toString() }.singleOrThrow("Download success Aweme field")
            requireLocals(2)

            addInstructions(
                0,
                """
                    iget-object v0, p0, $pathField
                    iget-object v1, p0, $awemeField
                    invoke-static {v0, v1}, $FILENAME_FORMATTER_CLASS_DESCRIPTOR->renameDownloadedMedia(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v0
                    iput-object v0, p0, $pathField
                    invoke-static {v0, v1}, $ORIGINAL_PHOTO_DOWNLOADER_DESCRIPTOR->onTikTokDownloadCompleted(Ljava/lang/String;Lcom/ss/android/ugc/aweme/feed/model/Aweme;)V
                """,
            )
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
