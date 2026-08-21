package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Temporary patch-time probe for the device-confirmed 46.4.3 FYP render bypass.
 * Finds the callers/data-source methods immediately above VideoViewCell.bind so the
 * production filter can skip/remove rejected Aweme items before a recycled cell binds.
 * Remove after the final production hook is identified.
 */
@Suppress("unused")
val runtimeFeed4643BindCallerDiscoveryPatch = bytecodePatch(
    name = "BlueIT 46.4.3 FYP bind caller discovery",
    description = "Temporary exact TikTok 46.4.3 homepage_hot VideoViewCell caller discovery.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val videoViewCell = "Lcom/ss/android/ugc/aweme/feed/adapter/VideoViewCell;"
        val aweme = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
        val bindNames = setOf("LJI", "J0", "LLLLLILLIL", "v0")

        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                val instructions = impl.instructions.toList()
                val methodRefs = instructions.mapNotNull { it.getReference<MethodReference>() }
                val stringRefs = instructions.mapNotNull { it.getReference<StringReference>()?.string }

                val callsVideoBind = methodRefs.any {
                    it.definingClass == videoViewCell && it.name in bindNames
                }
                val hasHomepageHot = stringRefs.any { it == "homepage_hot" }
                val hasBindMarker = stringRefs.any { it.contains("VideoViewCell_bind") || it.contains("VideoViewCell.bind") }
                val carriesAweme = method.parameterTypes.any { it.toString() == aweme } ||
                    method.returnType == aweme ||
                    methodRefs.any { ref ->
                        ref.parameterTypes.any { it.toString() == aweme } || ref.returnType == aweme
                    }
                val listSource = methodRefs.any { ref ->
                    (ref.definingClass == "Ljava/util/List;" && (ref.name == "get" || ref.name == "remove")) ||
                        (ref.definingClass.endsWith("/FeedItemList;") && (ref.name == "getItems" || ref.name == "setItems"))
                }

                if (!callsVideoBind && !hasBindMarker && !(hasHomepageHot && (carriesAweme || listSource))) {
                    return@forEach
                }

                val sig = "${classDef.type}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
                println(
                    "[BlueITFeedBind4643] BEGIN $sig access=${method.accessFlags} " +
                        "callsVideoBind=$callsVideoBind homepageHot=$hasHomepageHot bindMarker=$hasBindMarker " +
                        "carriesAweme=$carriesAweme listSource=$listSource"
                )
                instructions.forEachIndexed { index, instruction ->
                    val methodRef = instruction.getReference<MethodReference>()
                    val fieldRef = instruction.getReference<FieldReference>()
                    val stringRef = instruction.getReference<StringReference>()
                    val ref = when {
                        methodRef != null -> "M:${methodRef.definingClass}->${methodRef.name}(${methodRef.parameterTypes.joinToString("")})${methodRef.returnType}"
                        fieldRef != null -> "F:${fieldRef.definingClass}->${fieldRef.name}:${fieldRef.type}"
                        stringRef != null -> "S:${stringRef.string.replace("\n", "\\n").take(180)}"
                        else -> "-"
                    }
                    println("[BlueITFeedBind4643] I $index ${instruction.opcode} $ref")
                }
                println("[BlueITFeedBind4643] END $sig")
            }
        }
    }
}
