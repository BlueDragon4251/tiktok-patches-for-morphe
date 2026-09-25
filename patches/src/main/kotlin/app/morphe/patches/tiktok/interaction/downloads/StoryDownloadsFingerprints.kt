/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

/** `aweme.getIsTikTokStory() || aweme.getAwemeType() == 40` helper used to branch story-only UI. */
internal object IsTikTokStoryFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf(AWEME),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions?.toList()
        instructions != null && instructions.size < 16 &&
            instructions.any {
                it.getReference<MethodReference>()?.let { ref ->
                    ref.definingClass == AWEME && ref.name == "getIsTikTokStory"
                } == true
            } &&
            instructions.any {
                it.getReference<MethodReference>()?.let { ref ->
                    ref.definingClass == AWEME && ref.name == "getAwemeType"
                } == true
            } &&
            instructions.any { (it as? NarrowLiteralInstruction)?.narrowLiteral == 40 }
    },
)

/** Builds the share panel actions for an aweme. */
internal object SharePanelBuilderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    // Action helpers in the same class share the download strings; these only appear in the builder.
    strings = listOf("long_press_download", "add_mention_after_publish", "series_remove"),
)

/** Adds the "save" download action to the share panel. */
internal object SharePanelAddDownloadFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("save", "panel_download_bar", "homepage_podcast"),
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.getReference<MethodReference>()?.let { ref ->
                ref.name == "isSharedStoryVisible" && ref.parameterTypes == listOf(AWEME)
            } == true
        } == true
    },
)

/** Builds the long-press panel entries. */
internal object LongPressPanelBuilderFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf("panel_download_bar", "save", "save_photo", "why_this_video"),
)
