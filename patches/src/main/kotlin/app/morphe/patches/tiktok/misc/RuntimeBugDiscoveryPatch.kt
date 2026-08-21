package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val runtimeBugDiscoveryPatch = bytecodePatch(
    name = "BlueIT runtime bug discovery",
    description = "Temporary exact TikTok 46.4.3 instruction evidence collector.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val clearClass = "Lcom/ss/android/ugc/feed/platform/panel/clearmode/ClearModePanelComponent;"
        val feedApiClass = "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;"

        fun dumpMethod(prefix: String, method: com.android.tools.smali.dexlib2.iface.Method) {
            println("[BlueITRuntimeDiscovery2] $prefix METHOD ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType} registers=${method.implementation?.registerCount}")
            method.implementation?.instructions?.forEachIndexed { index, instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference?.toString() ?: ""
                val literal = (instruction as? NarrowLiteralInstruction)?.narrowLiteral?.toString() ?: ""
                val registers = when (instruction) {
                    is Instruction35c -> "count=${instruction.registerCount},c=${instruction.registerC},d=${instruction.registerD},e=${instruction.registerE},f=${instruction.registerF},g=${instruction.registerG}"
                    is RegisterRangeInstruction -> "start=${instruction.startRegister},count=${instruction.registerCount}"
                    is TwoRegisterInstruction -> "a=${instruction.registerA},b=${instruction.registerB}"
                    is OneRegisterInstruction -> "a=${instruction.registerA}"
                    else -> ""
                }
                println("[BlueITRuntimeDiscovery2] $prefix I=$index op=${instruction.opcode} regs=[$registers] lit=[$literal] ref=[$reference]")
            }
        }

        classDefForEach { classDef ->
            for (method in classDef.methods) {
                val isTarget =
                    (method.definingClass == clearClass && method.name in setOf("Rv0", "ap")) ||
                    (method.definingClass == feedApiClass && method.name == "LIZIZ" &&
                        method.returnType == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;")
                if (isTarget) dumpMethod("TARGET", method)

                val impl = method.implementation ?: continue
                val callsRv0 = impl.instructions.any { instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@any false
                    ref.definingClass == clearClass && ref.name == "Rv0" &&
                        ref.parameterTypes == listOf("I", "Ljava/lang/String;", "Z")
                }
                if (callsRv0) dumpMethod("RV0_CALLER", method)
            }
        }
    }
}
