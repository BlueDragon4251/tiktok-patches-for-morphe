/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val STORY_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/download/StoryDownloadsPatch;"

private fun Method.isStoryCheckCall(storyCheck: Method, index: Int): Boolean =
    implementation!!.instructions.elementAt(index).getReference<MethodReference>()?.let {
        it.definingClass == storyCheck.definingClass &&
            it.name == storyCheck.name &&
            it.parameterTypes == storyCheck.parameterTypes &&
            it.returnType == storyCheck.returnType
    } == true

@Suppress("unused")
val storyDownloadsPatch = bytecodePatch(
    name = "Story downloads",
    description = "Adds the download button to other people's stories in the share and long-press panels.",
    default = true,
) {
    dependsOn(downloadsPatch)
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableStoryDownloads()V",
        )

        val storyCheck = IsTikTokStoryFingerprint.method
        val addDownloadAction = SharePanelAddDownloadFingerprint.method

        // Share panel: TikTok only adds the download action for stories owned by the current user.
        // After the ownership check, also add it for other users' stories when enabled.
        SharePanelBuilderFingerprint.method.apply {
            val instructions = implementation!!.instructions.toList()
            val storyCheckIndex = instructions.indices.firstOrNull { isStoryCheckCall(storyCheck, it) }
                ?: throw PatchException("Story downloads: story check not found in share panel builder.")

            val ownerCheckIndex = (storyCheckIndex + 1 until instructions.size).firstOrNull { index ->
                instructions[index].opcode == Opcode.INVOKE_STATIC &&
                    instructions[index].getReference<MethodReference>()?.let {
                        it.parameterTypes == listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;") && it.returnType == "Z"
                    } == true
            } ?: throw PatchException("Story downloads: owner check not found in share panel builder.")

            val ownerResultIndex = ownerCheckIndex + 1
            if (instructions[ownerResultIndex].opcode != Opcode.MOVE_RESULT) {
                throw PatchException("Story downloads: unexpected instruction after owner check.")
            }
            val ownerRegister = getInstruction<OneRegisterInstruction>(ownerResultIndex).registerA

            // The aweme passed to the owner check is loaded from `this`; reuse that register for the call.
            val thisRegister = (ownerCheckIndex - 1 downTo storyCheckIndex).firstNotNullOfOrNull { index ->
                if (instructions[index].opcode == Opcode.IGET_OBJECT) {
                    getInstruction<TwoRegisterInstruction>(index).registerB
                } else {
                    null
                }
            } ?: throw PatchException("Story downloads: could not find panel instance register.")

            if (ownerRegister > 15 || thisRegister > 15) {
                throw PatchException("Story downloads: share panel registers out of range.")
            }

            addInstructionsWithLabels(
                ownerResultIndex + 1,
                """
                    if-nez v$ownerRegister, :story_download_done
                    invoke-static {}, $STORY_EXTENSION_CLASS_DESCRIPTOR->shouldDownloadStories()Z
                    move-result v$ownerRegister
                    if-eqz v$ownerRegister, :story_download_done
                    invoke-virtual {v$thisRegister}, ${addDownloadAction.definingClass}->${addDownloadAction.name}()V
                    const/4 v$ownerRegister, 0x0
                    :story_download_done
                    nop
                """,
            )
        }

        // Long-press panel: the "save" entry uses a story-only branch that requires ownership.
        // Treat stories as regular posts for that entry only.
        LongPressPanelBuilderFingerprint.method.apply {
            val instructions = implementation!!.instructions.toList()
            val downloadBarIndex = instructions.indexOfFirst {
                it.getReference<StringReference>()?.string == "panel_download_bar"
            }
            if (downloadBarIndex < 0) {
                throw PatchException("Story downloads: download bar marker not found in long-press panel.")
            }

            val storyCheckIndex = (downloadBarIndex - 1 downTo 0).firstOrNull { isStoryCheckCall(storyCheck, it) }
                ?: throw PatchException("Story downloads: story check not found in long-press panel.")

            val resultIndex = storyCheckIndex + 1
            if (instructions[resultIndex].opcode != Opcode.MOVE_RESULT) {
                throw PatchException("Story downloads: unexpected instruction after long-press story check.")
            }
            val resultRegister = getInstruction<OneRegisterInstruction>(resultIndex).registerA
            if (resultRegister > 15) {
                throw PatchException("Story downloads: long-press register out of range.")
            }

            addInstructions(
                resultIndex + 1,
                """
                    invoke-static {v$resultRegister}, $STORY_EXTENSION_CLASS_DESCRIPTOR->overrideIsStory(Z)Z
                    move-result v$resultRegister
                """,
            )
        }
    }
}
