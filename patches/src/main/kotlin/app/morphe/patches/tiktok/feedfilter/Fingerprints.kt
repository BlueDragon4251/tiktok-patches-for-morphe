/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/feedfilter/Fingerprints.kt
 */
package app.morphe.patches.tiktok.feedfilter

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object FeedItemListGetItemsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/util/List;",
    custom = { method, classDef ->
        classDef.endsWith("/FeedItemList;") &&
            method.name == "getItems" &&
            method.parameterTypes.isEmpty()
    },
)

internal object FollowFeedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;",
    custom = { method, _ ->
        method.parameterTypes.size == 2
    },
)

internal object FollowFeedListGetItemsFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;",
    name = "getItems",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/util/List;",
    parameters = emptyList(),
)

internal object TakoAiFeedButtonSetVisibleFingerprint : Fingerprint(
    definingClass = "/feed/assem/tikbot/TakoAssem;",
    returnType = "V",
    parameters = listOf("Z"),
    custom = { method, _ ->
        val references = method.implementation?.instructions
            ?.mapNotNull { it.getReference<MethodReference>() }
            ?: emptyList()

        references.any {
            it.definingClass == "Lcom/bytedance/assem/arch/reused/ReusedUIAssem;" &&
                it.name == "LJJIJLIJ" &&
                it.returnType == "Landroid/view/View;"
        } && references.any {
            it.definingClass == "Landroid/view/View;" &&
                it.name == "getVisibility" &&
                it.returnType == "I"
        }
    },
)

internal object FollowFeedPresenterPostProcessFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;"),
    custom = { method, _ ->
        val references = method.implementation?.instructions
            ?.mapNotNull { it.getReference<MethodReference>() }
            ?: emptyList()
        references.any {
            it.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" &&
                it.name == "isAd"
        } && references.any {
            it.definingClass == "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;" &&
                it.name == "setItems"
        } && references.any {
            it.definingClass == "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;" &&
                it.name == "setInsertedResults"
        }
    },
)

internal object TakoAiFeedButtonBindFingerprint : Fingerprint(
    definingClass = "/feed/assem/tikbot/TakoAssem;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)
