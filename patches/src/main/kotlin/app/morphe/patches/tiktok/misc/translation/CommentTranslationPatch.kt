package app.morphe.patches.tiktok.misc.translation

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

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

        val cell = BaseCommentCellBindFingerprint.uniqueMethod
        val cellSite = TranslationContracts.bindSite(cell, HookEvidence.originalClass(cell.definingClass)!!, HookEvidence::originalClass)
        cell.addInstructions(cellSite.index, cellSite.code)

        val loaded = CommentListLoadedFingerprint.uniqueMethod
        val loadedSite = TranslationContracts.loadedSite(loaded, HookEvidence.originalClass(loaded.definingClass)!!)
        loaded.addInstruction(loadedSite.index, loadedSite.code)

        MultiCommentTranslationStartFingerprint.uniqueMethod.apply {
            val start = parameterRegister(0, "Ljava/util/List;")
            val end = parameterRegister(2, "Z")
            if (end != start + 2) throw PatchException("Batch translation requires three contiguous argument words")
            addInstruction(0, "invoke-static/range {v$start .. v$end}, $EXTENSION_CLASS_DESCRIPTOR->onNativeBatchStart(Ljava/lang/Object;Ljava/lang/Object;Z)V")
        }

        val complete = MultiCommentTranslationCompleteFingerprint.uniqueMethod
        val completeSite = TranslationContracts.completeSite(complete, HookEvidence.originalClass(complete.definingClass)!!, HookEvidence::originalClass)
        complete.addInstructions(completeSite.index, completeSite.code)
    }
}
