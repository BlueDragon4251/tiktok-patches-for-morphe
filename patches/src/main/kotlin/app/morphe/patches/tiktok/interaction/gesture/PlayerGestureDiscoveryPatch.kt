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

/** TEMPORARY: exact 46.4.3 player evidence collector for Gesture Remapper seek actions. */
@Suppress("unused")
val playerGestureDiscoveryPatch = bytecodePatch(
    name = "BlueIT player gesture discovery",
    description = "Temporary internal 46.4.3 player/seek evidence collector.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val focusedTypes = setOf(
            "LX/0MBU;",
            "LX/0M6r;",
            "LX/0SZR;",
            "LX/0Sa7;",
            "LX/0Sa4;",
            "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;",
            "Lcom/ss/android/ugc/feed/platform/panel/player/PlayerComponentTemp;",
            "Lcom/ss/android/ugc/aweme/feed/panel/BaseListFragmentPanel;",
        )

        classDefForEach { classDef ->
            val related = classDef.type in focusedTypes ||
                classDef.interfaces.any { it in focusedTypes } ||
                classDef.superclass in focusedTypes
            if (!related) return@classDefForEach

            println(
                "[BlueITPlayerGesture] CLASS ${classDef.type} superclass=${classDef.superclass} " +
                    "interfaces=${classDef.interfaces.joinToString(",")}",
            )
            classDef.fields.forEach { field ->
                println("[BlueITPlayerGesture] FIELD ${classDef.type}->${field.name}:${field.type} access=${field.accessFlags}")
            }

            classDef.methods.forEach { method ->
                val implementation = method.implementation
                val refs = linkedSetOf<String>()
                implementation?.instructions?.forEach { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    when (reference) {
                        is MethodReference -> refs += "M:${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                        is FieldReference -> refs += "F:${reference.definingClass}->${reference.name}:${reference.type}"
                        is TypeReference -> refs += "T:${reference.type}"
                        is StringReference -> refs += "S:${reference.string.take(120)}"
                    }
                }

                val lower = method.name.lowercase()
                val interestingName = lower.contains("seek") || lower.contains("position") ||
                    lower.contains("duration") || lower.contains("pause") || lower.contains("resume") ||
                    lower.contains("play") || lower.contains("progress") || lower.contains("speed")
                val interestingSignature = method.returnType == "J" || method.returnType == "F" ||
                    method.parameterTypes.any { it == "J" || it == "F" }
                val interestingRefs = refs.any {
                    it.contains("getCurrentPosition") || it.contains("getDuration") ||
                        it.contains("seek") || it.contains("pause") || it.contains("resume") ||
                        it.contains("setSpeed") || it.contains("PlayerController") || it.contains("0MBU")
                }
                val dump = classDef.type != "Lcom/ss/android/ugc/aweme/feed/panel/BaseListFragmentPanel;" ||
                    interestingName || interestingSignature || interestingRefs
                if (!dump) return@forEach

                println(
                    "[BlueITPlayerGesture] METHOD ${method.definingClass}->${method.name}" +
                        "(${method.parameterTypes.joinToString("")})${method.returnType} access=${method.accessFlags} " +
                        "registers=${implementation?.registerCount ?: -1} refs=${refs.take(60).joinToString(" || ")}",
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
                        "[BlueITPlayerGesture] I ${method.definingClass}->${method.name} #$index ${instruction.opcode}" +
                            if (details.isEmpty()) "" else " ${details.joinToString(" ")}",
                    )
                }
            }
        }
    }
}
