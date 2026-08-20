package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** TEMPORARY: exact 46.4.3 evidence collector; remove after Gesture Remapper hook is resolved. */
@Suppress("unused")
val gestureDiscoveryPatch = bytecodePatch(
    name = "BlueIT gesture discovery",
    description = "Temporary internal 46.4.3 gesture evidence collector.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        var classCount = 0
        classDefForEach { classDef ->
            val gestureMethods = classDef.methods.filter { method ->
                method.name in setOf(
                    "onSingleTapConfirmed",
                    "onDoubleTap",
                    "onDoubleTapEvent",
                    "onLongPress",
                    "onSingleTapUp",
                )
            }
            if (gestureMethods.isEmpty()) return@classDefForEach

            classCount++
            println(
                "[BlueITGestureDiscovery] CLASS ${classDef.type} superclass=${classDef.superclass} " +
                    "interfaces=${classDef.interfaces.joinToString(",")}",
            )
            gestureMethods.forEach { method ->
                val refs = linkedSetOf<String>()
                method.implementation?.instructions?.forEach { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    when (reference) {
                        is MethodReference -> refs += "M:${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                        is FieldReference -> refs += "F:${reference.definingClass}->${reference.name}:${reference.type}"
                        is TypeReference -> refs += "T:${reference.type}"
                        is StringReference -> refs += "S:${reference.string.take(120)}"
                    }
                }
                println(
                    "[BlueITGestureDiscovery] METHOD ${method.definingClass}->${method.name}" +
                        "(${method.parameterTypes.joinToString("")})${method.returnType} " +
                        "access=${method.accessFlags} refs=${refs.take(35).joinToString(" || ")}",
                )
            }
        }

        val focusedTypes = setOf("LX/0QeR;", "LX/0Qqd;", "LX/0Qdk;")
        classDefForEach { classDef ->
            if (classDef.type !in focusedTypes) return@classDefForEach
            println(
                "[BlueITGestureFocus] CLASS ${classDef.type} superclass=${classDef.superclass} " +
                    "interfaces=${classDef.interfaces.joinToString(",")}",
            )
            classDef.fields.forEach { field ->
                println("[BlueITGestureFocus] FIELD ${classDef.type}->${field.name}:${field.type} access=${field.accessFlags}")
            }
            classDef.methods.forEach { method ->
                val implementation = method.implementation
                println(
                    "[BlueITGestureFocus] METHOD ${method.definingClass}->${method.name}" +
                        "(${method.parameterTypes.joinToString("")})${method.returnType} access=${method.accessFlags} " +
                        "registers=${implementation?.registerCount ?: -1}",
                )
                if (implementation == null) return@forEach
                implementation.instructions.forEachIndexed { index, instruction ->
                    val details = ArrayList<String>()
                    when (instruction) {
                        is FiveRegisterInstruction -> details += "regs=${instruction.registerC},${instruction.registerD},${instruction.registerE},${instruction.registerF},${instruction.registerG} count=${instruction.registerCount}"
                        is RegisterRangeInstruction -> details += "range=${instruction.startRegister}+${instruction.registerCount}"
                        is TwoRegisterInstruction -> details += "regs=${instruction.registerA},${instruction.registerB}"
                        is OneRegisterInstruction -> details += "reg=${instruction.registerA}"
                    }
                    when (instruction) {
                        is NarrowLiteralInstruction -> details += "lit=${instruction.narrowLiteral}"
                        is WideLiteralInstruction -> details += "lit=${instruction.wideLiteral}"
                    }
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    when (reference) {
                        is MethodReference -> details += "ref=M:${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                        is FieldReference -> details += "ref=F:${reference.definingClass}->${reference.name}:${reference.type}"
                        is TypeReference -> details += "ref=T:${reference.type}"
                        is StringReference -> details += "ref=S:${reference.string.take(160)}"
                    }
                    println(
                        "[BlueITGestureFocus] I ${method.definingClass}->${method.name} #$index ${instruction.opcode}" +
                            if (details.isEmpty()) "" else " ${details.joinToString(" ")}",
                    )
                }
            }
        }
        println("[BlueITGestureDiscovery] TOTAL_CLASSES=$classCount")
    }
}
