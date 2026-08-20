package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.getReference
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
                "[BlueITGestureDiscovery] CLASS ${classDef.type} " +
                    "interfaces=${classDef.interfaces.joinToString(",")}",
            )
            gestureMethods.forEach { method ->
                val refs = linkedSetOf<String>()
                method.implementation?.instructions?.forEach { instruction ->
                    when (val reference = instruction.getReference<Any>()) {
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
        println("[BlueITGestureDiscovery] TOTAL_CLASSES=$classCount")
    }
}
