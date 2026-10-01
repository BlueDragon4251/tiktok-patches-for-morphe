/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/seekbar/Fingerprints.kt
 */
package app.morphe.patches.tiktok.interaction.seekbar

import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val AWEME_CLASS = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

private fun isTargetClass(classDef: ClassDef): Boolean =
    classDef.methods.any { sibling ->
        sibling.implementation?.instructions?.any { instruction ->
            (instruction as? ReferenceInstruction)?.reference?.let { reference ->
                reference is StringReference &&
                    (reference.string == "homepage_hot" || reference.string == "FeedRecommendFragment")
            } ?: false
        } == true
    }

internal object VanillaLongFilterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(AWEME_CLASS),
    custom = { method, classDef ->
        isTargetClass(classDef) && (method.implementation?.instructions?.count() ?: 0) > 20
    },
)

internal object ShouldShowProgressBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(AWEME_CLASS),
    custom = { method, classDef ->
        run {
            val calls = method.implementation?.instructions?.mapNotNull { it.getReference<MethodReference>() }.orEmpty()
            isTargetClass(classDef) && classDef.methods.any { it.returnType == "Lcom/ss/android/ugc/aweme/feed/assem/ability/IFeedPanelPlatformAbility;" } &&
                calls.size == 2 && calls.all { it.parameterTypes == listOf(AWEME_CLASS) && it.returnType == "Z" } &&
                calls.any { it.definingClass == "Lcom/ss/android/ugc/aweme/feed/model/AwemeExtKt;" && it.name == "isAdTraffic" }
        }
    },
)

internal object SetSeekBarShowTypeFingerprint : Fingerprint(
    parameters = listOf("I"), returnType = "V",
    strings = listOf("seekbar show type change, change to:"),
)
