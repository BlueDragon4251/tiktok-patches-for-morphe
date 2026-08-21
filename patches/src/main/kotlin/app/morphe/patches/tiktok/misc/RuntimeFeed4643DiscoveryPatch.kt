package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Temporary exact-46.4.3 discovery patch. Remove after runtime feed/ad paths are identified. */
@Suppress("unused")
val runtimeFeed4643DiscoveryPatch = bytecodePatch(
    name = "BlueIT 46.4.3 late feed discovery",
    description = "Temporary exact TikTok 46.4.3 feed/ad structure discovery.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val interestingClasses = setOf(
            "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
            "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;",
            "Lcom/ss/android/ugc/aweme/feed/model/Video;",
            "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;",
            "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;",
        )
        val adHints = listOf("ad", "advert", "sponsor", "promot", "commercial", "paid", "raw")
        val feedHints = listOf("item", "insert", "append", "add", "setitems", "feed")

        classDefForEach { classDef ->
            if (classDef.type in interestingClasses) {
                println("[BlueITFeed4643] CLASS ${classDef.type} superclass=${classDef.superclass}")
                classDef.fields.forEach { field ->
                    val lower = "${field.name}:${field.type}".lowercase()
                    if (classDef.type.endsWith("/Aweme;") || adHints.any(lower::contains) || feedHints.any(lower::contains)) {
                        println("[BlueITFeed4643] FIELD ${classDef.type}->${field.name}:${field.type} access=${field.accessFlags}")
                    }
                }
                classDef.methods.forEach { method ->
                    val sig = "${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
                    val lower = sig.lowercase()
                    if (classDef.type.endsWith("/Aweme;") && adHints.any(lower::contains) ||
                        classDef.type.endsWith("/FeedItemList;") ||
                        classDef.type.endsWith("/FollowFeedList;") ||
                        classDef.type.endsWith("/FeedApi;") && feedHints.any(lower::contains)
                    ) {
                        println("[BlueITFeed4643] METHOD ${classDef.type}->$sig access=${method.accessFlags}")
                    }
                }
            }

            classDef.methods.forEach { method ->
                val impl = method.implementation ?: return@forEach
                var referencesAwemeAd = false
                var mutatesFeedList = false
                var readsVideoAd = false
                for (instruction in impl.instructions) {
                    val methodRef = instruction.getReference<MethodReference>()
                    if (methodRef != null) {
                        if (methodRef.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" &&
                            adHints.any(methodRef.name.lowercase()::contains)) {
                            referencesAwemeAd = true
                        }
                        if ((methodRef.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" ||
                             methodRef.definingClass == "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;") &&
                            feedHints.any(methodRef.name.lowercase()::contains)) {
                            mutatesFeedList = true
                        }
                    }
                    val fieldRef = instruction.getReference<FieldReference>()
                    if (fieldRef != null && fieldRef.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/Video;" &&
                        adHints.any(fieldRef.name.lowercase()::contains)) {
                        readsVideoAd = true
                    }
                }
                if (referencesAwemeAd || mutatesFeedList || readsVideoAd) {
                    println(
                        "[BlueITFeed4643] REF ${classDef.type}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}" +
                            " awemeAd=$referencesAwemeAd feedMutation=$mutatesFeedList videoAd=$readsVideoAd"
                    )
                }
            }
        }
    }
}
