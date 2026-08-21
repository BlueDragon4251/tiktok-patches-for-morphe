package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Temporary patch-time disassembly probe for the device-confirmed 46.4.3 FYP bypass.
 * Remove after the production hook is identified.
 */
@Suppress("unused")
val runtimeFeed4643ExactDiscoveryPatch = bytecodePatch(
    name = "BlueIT 46.4.3 exact FYP path discovery",
    description = "Temporary exact TikTok 46.4.3 FYP post-process/render discovery.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val targets = mapOf(
            "LX/0MPw;" to { name: String, params: List<CharSequence>, ret: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" } ||
                    ret == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
            },
            "LX/0NAi;" to { _: String, params: List<CharSequence>, _: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" }
            },
            "LX/0NAk;" to { _: String, params: List<CharSequence>, _: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" }
            },
            "LX/0NAs;" to { _: String, params: List<CharSequence>, _: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" }
            },
            "LX/0QUQ;" to { _: String, params: List<CharSequence>, _: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" }
            },
            "Lcom/ss/android/ugc/aweme/feed/adapter/VideoViewCell;" to { _: String, params: List<CharSequence>, _: String ->
                params.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" }
            },
            "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;" to { name: String, _: List<CharSequence>, ret: String ->
                name == "LIZIZ" && ret == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
            },
        )

        classDefForEach { classDef ->
            val predicate = targets[classDef.type] ?: return@classDefForEach
            classDef.methods.forEach { method ->
                if (!predicate(method.name, method.parameterTypes, method.returnType)) return@forEach
                val impl = method.implementation ?: return@forEach
                val sig = "${classDef.type}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
                println("[BlueITFeed4643Exact] BEGIN $sig access=${method.accessFlags}")
                impl.instructions.forEachIndexed { index, instruction ->
                    val methodRef = instruction.getReference<MethodReference>()
                    val fieldRef = instruction.getReference<FieldReference>()
                    val stringRef = instruction.getReference<StringReference>()
                    val ref = when {
                        methodRef != null -> "M:${methodRef.definingClass}->${methodRef.name}(${methodRef.parameterTypes.joinToString("")})${methodRef.returnType}"
                        fieldRef != null -> "F:${fieldRef.definingClass}->${fieldRef.name}:${fieldRef.type}"
                        stringRef != null -> "S:${stringRef.string.replace("\n", "\\n").take(180)}"
                        else -> "-"
                    }
                    println("[BlueITFeed4643Exact] I $index ${instruction.opcode} $ref")
                }
                println("[BlueITFeed4643Exact] END $sig")
            }
        }
    }
}
