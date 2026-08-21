package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** TEMPORARY: exact 46.4.3 settings group evidence collector. */
@Suppress("unused")
val settingsGroupDiscoveryPatch = bytecodePatch(
    name = "BlueIT settings group discovery",
    description = "Temporary internal TikTok 46.4.3 settings group evidence collector.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        classDefForEach { classDef ->
            val type = classDef.type
            if (!type.contains("/setting/ui/rvmpcompose/")) return@classDefForEach
            if (!type.contains("Group") && !type.contains("Setting") && !type.contains("Cell")) {
                return@classDefForEach
            }

            println(
                "[BlueITSettingsGroup] CLASS $type superclass=${classDef.superclass} " +
                    "interfaces=${classDef.interfaces.joinToString(",")}",
            )
            classDef.fields.forEach { field ->
                println(
                    "[BlueITSettingsGroup] FIELD $type->${field.name}:${field.type} access=${field.accessFlags}",
                )
            }

            classDef.methods.forEach { method ->
                val implementation = method.implementation ?: return@forEach
                val refs = linkedSetOf<String>()
                implementation.instructions.forEach { instruction ->
                    when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                        is MethodReference -> refs +=
                            "M:${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                        is FieldReference -> refs +=
                            "F:${reference.definingClass}->${reference.name}:${reference.type}"
                        is StringReference -> refs += "S:${reference.string.take(160)}"
                        is TypeReference -> refs += "T:${reference.type}"
                    }
                }

                val interesting = method.name == "defaultState" ||
                    method.name == "XN" || method.name == "ER" ||
                    refs.any {
                        it.contains("SECTION_HEADER") || it.contains("OPEN_DEBUG") ||
                            it.contains("Activity", ignoreCase = true) ||
                            it.contains("Support", ignoreCase = true)
                    }
                if (!interesting) return@forEach

                println(
                    "[BlueITSettingsGroup] METHOD ${method.definingClass}->${method.name}" +
                        "(${method.parameterTypes.joinToString("")})${method.returnType} " +
                        "refs=${refs.joinToString(" || ")}",
                )
            }
        }
    }
}
