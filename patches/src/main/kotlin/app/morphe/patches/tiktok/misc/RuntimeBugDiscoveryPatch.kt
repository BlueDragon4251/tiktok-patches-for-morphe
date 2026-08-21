package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.cleardisplay.OnClearDisplayEventFingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val runtimeBugDiscoveryPatch = bytecodePatch(
    name = "BlueIT runtime bug discovery",
    description = "Temporary TikTok 46.4.3 evidence collector for Clear Display and feed contexts.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val eventType = OnClearDisplayEventFingerprint.method.parameters.firstOrNull()?.type
        println("[BlueITRuntimeDiscovery] clearEventType=$eventType")

        classDefForEach { classDef ->
            if (classDef.type == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;") {
                println("[BlueITRuntimeDiscovery] FEED_ITEM_LIST class=${classDef.type} superclass=${classDef.superclass}")
                classDef.fields.forEach { field ->
                    println("[BlueITRuntimeDiscovery] FEED_ITEM_LIST_FIELD ${field.name}:${field.type} access=${field.accessFlags}")
                }
                classDef.methods.forEach { method ->
                    println("[BlueITRuntimeDiscovery] FEED_ITEM_LIST_METHOD ${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}")
                }
            }

            for (method in classDef.methods) {
                val impl = method.implementation ?: continue
                var refsEvent = false
                var refsScale = false
                var callsFeedGetItems = false
                val refs = linkedSetOf<String>()
                val strings = linkedSetOf<String>()

                for (instruction in impl.instructions) {
                    when (val reference = (instruction as? ReferenceInstruction)?.reference) {
                        is MethodReference -> {
                            val text = "${reference.definingClass}->${reference.name}(${reference.parameterTypes.joinToString("")})${reference.returnType}"
                            if (eventType != null && (reference.definingClass == eventType || reference.parameterTypes.contains(eventType) || reference.returnType == eventType)) refsEvent = true
                            if (reference.definingClass.contains("ScaleGestureDetector") || reference.name in setOf("onScale", "onScaleBegin", "onScaleEnd", "getScaleFactor", "getCurrentSpan", "getPreviousSpan", "getPointerCount")) refsScale = true
                            if (reference.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" && reference.name == "getItems") callsFeedGetItems = true
                            if (refs.size < 80) refs += text
                        }
                        is FieldReference -> {
                            val text = "${reference.definingClass}->${reference.name}:${reference.type}"
                            if (eventType != null && (reference.definingClass == eventType || reference.type == eventType)) refsEvent = true
                            if (reference.type.contains("ScaleGestureDetector")) refsScale = true
                            if (refs.size < 80) refs += text
                        }
                        is TypeReference -> {
                            if (eventType != null && reference.type == eventType) refsEvent = true
                            if (reference.type.contains("ScaleGestureDetector")) refsScale = true
                        }
                        is StringReference -> if (strings.size < 40) strings += reference.string.take(180)
                    }
                }

                val classHint = classDef.type.contains("/feed/") || classDef.type.contains("/profile/") ||
                    classDef.type.contains("/user/") || classDef.type.contains("/detail/") ||
                    classDef.type.contains("ClearMode", ignoreCase = true) || classDef.type.contains("gesture", ignoreCase = true)

                if (refsEvent || (refsScale && classHint)) {
                    println("[BlueITRuntimeDiscovery] CLEAR_GESTURE ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType} event=$refsEvent scale=$refsScale strings=${strings.joinToString(" || ")} refs=${refs.joinToString(" || ")}")
                }

                if (callsFeedGetItems) {
                    println("[BlueITRuntimeDiscovery] FEED_GETITEMS_CALLER ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType} strings=${strings.joinToString(" || ")} refs=${refs.joinToString(" || ")}")
                }
            }
        }
    }
}
