package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** Semantic selection plus an exact allowed operation at a typed native boundary. */
internal object SiteContracts {
    const val FEED_COMMIT = "experimental-feed-commit-sites"
    const val SHARE_RETURN = "experimental-share-return"
    const val SEARCH_RETURN = "experimental-search-return"
    const val SEEKBAR_PARAMETER = "experimental-seekbar-parameter"
    val modes = setOf(FEED_COMMIT, SHARE_RETURN, SEARCH_RETURN, SEEKBAR_PARAMETER)
    private const val FEED = "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
    private const val MESSAGE_OBJ = "Landroid/os/Message;->obj:Ljava/lang/Object;"
    private const val GUARD = "Lapp/morphe/extension/tiktok/feedfilter/ForYouFeedGuard;"

    fun mode(method: Method, accepted: String): String? {
        val body = method.implementation ?: return null
        val insns = body.instructions.toList()
        val strings = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        if (body.registerCount < 1 || !method.definingClass.startsWith("LX/")) return null
        fun reference(index: Int) = (insns[index] as? ReferenceInstruction)?.reference?.toString()
        return when (accepted) {
            "LX/07fn;->run()V" -> FEED_COMMIT.takeIf {
                method.name == "run" && method.parameterTypes.isEmpty() && method.returnType == "V" &&
                    method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
                    strings.any { it.startsWith("Feed0VVManager@") } &&
                    strings.containsAll(listOf("full_feed_commit_process_data", "homepage_hot", "filter_show_ad",
                        "filter_installed_ad", "fyp", "soft_ads", "roi2")) &&
                    insns.indices.any { insns[it].opcode == Opcode.INVOKE_INTERFACE &&
                        reference(it) == "Ljava/util/concurrent/Callable;->call()Ljava/lang/Object;" } &&
                    insns.indices.any { insns[it].opcode == Opcode.CHECK_CAST && reference(it) == FEED } &&
                    insns.indices.count { insns[it].opcode == Opcode.IPUT_OBJECT && reference(it) == MESSAGE_OBJ } == 1
            }
            "LX/1GG6;->LIZ(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;)Ljava/lang/String;" -> SHARE_RETURN.takeIf {
                method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value) &&
                    method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;", "Ljava/lang/String;",
                        "Ljava/lang/String;", "Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;") &&
                    method.returnType == "Ljava/lang/String;" && body.registerCount in 4..258 &&
                    strings.containsAll(listOf("utm_campaign", "share_link_id"))
            }
            "LX/04uK;->LIZLLL()Z" -> SEARCH_RETURN.takeIf {
                method.parameterTypes.isEmpty() && method.returnType == "Z" &&
                    method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value) &&
                    strings.any { it.startsWith("non_personalized_search_state_") } &&
                    insns.any { it.opcode == Opcode.RETURN }
            }
            "LX/06W4;->setSeekBarShowType(I)V" -> SEEKBAR_PARAMETER.takeIf {
                method.name == "setSeekBarShowType" && method.parameterTypes.map(CharSequence::toString) == listOf("I") &&
                    method.returnType == "V" && method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
                    body.registerCount in 2..256 && strings.contains("seekbar show type change, change to:") &&
                    insns.indices.any { reference(it)?.endsWith("->setCanDrag(Z)V") == true }
            }
            else -> null
        }
    }

    fun mutationAllowed(mode: String, method: Method, index: Int?, code: String?): Boolean {
        val body = method.implementation ?: return false
        val at = index ?: return false
        val insns = body.instructions.toList()
        val instruction = insns.getOrNull(at) ?: return false
        fun reference(index: Int) = (insns.getOrNull(index) as? ReferenceInstruction)?.reference?.toString()
        val lines = code?.trim()?.lines()?.map(String::trim)?.filter(String::isNotEmpty) ?: return false
        return when (mode) {
            FEED_COMMIT -> {
                val cast = insns.getOrNull(at - 1)
                val (register, call) = when {
                    cast?.opcode == Opcode.CHECK_CAST && reference(at - 1) == FEED ->
                        (cast as OneRegisterInstruction).registerA to "$GUARD->markAndFilter($FEED)V"
                    instruction.opcode == Opcode.IPUT_OBJECT && reference(at) == MESSAGE_OBJ ->
                        (instruction as OneRegisterInstruction).registerA to "$GUARD->filterBeforeUiCommit(Ljava/lang/Object;)V"
                    else -> return false
                }
                lines == listOf("invoke-static/range {v$register .. v$register}, $call")
            }
            SHARE_RETURN -> {
                val register = method.parameterRegister(1, "Ljava/lang/String;")
                at == 0 && lines == listOf(
                    "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/share/ShareUrlSanitizer;->stripAllQueryParams(Ljava/lang/String;)Ljava/lang/String;",
                    "move-result-object v$register", "return-object v$register")
            }
            SEARCH_RETURN -> {
                if (instruction.opcode != Opcode.RETURN) return false
                val register = (instruction as OneRegisterInstruction).registerA
                lines == listOf(
                    "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;->enableNonPersonalizedSearch(Z)Z",
                    "move-result v$register")
            }
            SEEKBAR_PARAMETER -> {
                val register = method.parameterRegister(0, "I")
                at == 0 && lines == listOf(
                    "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/seekbar/SeekbarPatch;->overrideSeekbarShowType(I)I",
                    "move-result v$register")
            }
            else -> false
        }
    }
}
