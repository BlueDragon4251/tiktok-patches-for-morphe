/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/settings/SettingsPatch.kt
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/settings/BlueITActivityHook;"

context(BytecodePatchContext)
internal fun addLegacySettingsEntryFallback() {
        // The legacy fragment is absent from the accepted 46.7.3 APK and from
        // the 47.1.3 candidate. Only resolve its reflected row types if the
        // old insertion point actually exists.
        val addSettingsMethod = AddSettingsEntryFingerprint.optionalMethod ?: return
        val createSettingsEntryMethodDescriptor =
            "$EXTENSION_CLASS_DESCRIPTOR->createSettingsEntry(" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                ")Ljava/lang/Object;"

        fun String.toClassName(): String = substring(1, length - 1).replace("/", ".")

        val settingsButtonClass = SettingsEntryFingerprint.uniqueOriginalClassDef.type.toClassName()
        val settingsButtonInfoClass = SettingsEntryInfoFingerprint.uniqueOriginalClassDef.type.toClassName()

        // The optional legacy path still requires a reviewed native insertion
        // method and row types when it is present.
        run {
            val implementation = addSettingsMethod.implementation ?: return
            val markIndex = implementation.instructions.indexOfFirst {
                it.opcode == Opcode.IGET_OBJECT &&
                    (it as? Instruction22c)?.reference?.let { ref -> ref is FieldReference && ref.name == "headerUnit" } == true
            }

            if (markIndex < 0) return

            val getUnitManager = addSettingsMethod.getInstruction(markIndex + 2)
            val addEntry = addSettingsMethod.getInstruction(markIndex + 1)

            addSettingsMethod.addInstructions(markIndex + 2, listOf(getUnitManager, addEntry))

            addSettingsMethod.addInstructions(
                markIndex + 2,
                """
                    const-string v0, "$settingsButtonClass"
                    const-string v1, "$settingsButtonInfoClass"
                    invoke-static {v0, v1}, $createSettingsEntryMethodDescriptor
                    move-result-object v0
                    check-cast v0, ${SettingsEntryFingerprint.uniqueOriginalClassDef.type}
                """,
            )
        }

}
