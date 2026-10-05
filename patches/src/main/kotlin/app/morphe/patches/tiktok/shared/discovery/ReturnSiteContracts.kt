package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

/** Result-only transformations preserve every native instruction and branch.
 * Parameter-based hooks additionally require the input register to survive all paths.
 */
internal object ReturnSiteContracts {
    const val FOLLOW = "experimental-follow-response-return"
    const val FINAL = "experimental-follow-final-return"
    const val ITEMS = "experimental-follow-items-return"
    const val VE = "experimental-ve-config-return"
    const val STICKER = "experimental-sticker-binder-return"
    const val SCHEMA = "experimental-activity-center-schema-return"
    const val PROGRESS = "experimental-player-progress-entry"
    val modes = setOf(FOLLOW, FINAL, ITEMS, VE, STICKER, SCHEMA, PROGRESS)
    private const val FOLLOW_LIST = "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;"
    private const val VE_OWNER = "Lcom/ss/android/vesdk/VEConfigCenter;"
    private const val RUNTIME = "Lapp/morphe/extension/tiktok/featuregatelab/FeatureGateLabRuntime;"
    // Verified stable native descriptors; these are explicit sites rather than fingerprints.
    private val ve = mapOf(
        "$VE_OWNER->getValue(Ljava/lang/String;Z)Ljava/lang/Boolean;" to "overrideVeBoolean",
        "$VE_OWNER->getValue(Ljava/lang/String;I)I" to "overrideVeInt",
        "$VE_OWNER->getValue(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;" to "overrideVeString",
    )
    private const val SCHEMA_TARGET = "Lcom/ss/android/ugc/tiktok/pns/activitycenter/EnterActivityCenterAction;->getSchema(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    private fun schemaShape(method: Method): Boolean =
        method.definingClass == SCHEMA_TARGET.substringBefore("->") && method.name == "getSchema" &&
            method.returnType == "Ljava/lang/String;" && method.accessFlags == 18 &&
            method.parameterTypes.map(CharSequence::toString) in listOf(
                listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;"),
                listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Landroid/net/Uri;"))
    fun explicitTarget(method: Method): String? = when {
        method.toString() in ve -> method.toString()
        schemaShape(method) -> SCHEMA_TARGET
        else -> null
    }
    private fun unchangedInput(method: Method, register: Int): Boolean =
        method.implementation?.instructions?.none { i ->
            i.opcode.setsRegister() && i is OneRegisterInstruction &&
                (i.registerA == register || i.opcode.setsWideRegister() && i.registerA + 1 == register)
        } == true
    private fun calls(method: Method): List<MethodReference> = method.implementation?.instructions?.mapNotNull {
        if (!it.opcode.name.startsWith("invoke-")) null
        else (it as? ReferenceInstruction)?.reference as? MethodReference
    }?.toList().orEmpty()
    fun mode(method: Method, owner: ClassDef, accepted: String): String? {
        val body = method.implementation ?: return null
        val refs = calls(method)
        val insns = body.instructions.toList()
        val static = AccessFlags.STATIC.isSet(method.accessFlags)
        val progress = "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;->onPlayProgressChange(Ljava/lang/String;JJ)V"
        if (accepted == progress && method.toString() == progress && method.accessFlags == 17 &&
            owner.superclass == "Lcom/ss/android/ugc/aweme/feed/controller/BaseController;" &&
            insns.any { it.opcode == Opcode.IPUT_WIDE && (it as? ReferenceInstruction)?.reference?.toString() ==
                "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;->lastPosition:J" } &&
            refs.any { it.toString() == "Lcom/ss/android/ugc/aweme/player/sdk/api/OnUIPlayListener;->onPlayProgressChange(Ljava/lang/String;JJ)V" }) return PROGRESS
        if (accepted == "LX/1N7W;->LIZ(LX/0lqn;LX/04zA;)$FOLLOW_LIST" &&
            method.returnType == FOLLOW_LIST && method.accessFlags == 9 && method.parameterTypes.size == 2 &&
            method.parameterTypes.all { it.startsWith("LX/") } &&
            refs.any { it.toString() == "$FOLLOW_LIST->getItems()Ljava/util/List;" } &&
            refs.any { it.toString() == "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeed;->setFromPreload(Z)V" } &&
            refs.count { it.returnType == FOLLOW_LIST } == 1 && insns.any { it.opcode == Opcode.RETURN_OBJECT }) return FOLLOW
        if (accepted == "LX/1N7X;->LJIILL($FOLLOW_LIST)V" &&
            method.accessFlags == 17 && method.returnType == "V" && method.parameterTypes.map(CharSequence::toString) == listOf(FOLLOW_LIST) &&
            unchangedInput(method, method.parameterRegister(0, FOLLOW_LIST)) &&
            refs.any { it.toString() == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;->isAd()Z" } &&
            refs.any { it.toString() == "$FOLLOW_LIST->setItems(Ljava/util/List;)V" } &&
            refs.any { it.toString() == "$FOLLOW_LIST->setInsertedResults(Ljava/util/List;)V" } &&
            insns.any { it.opcode == Opcode.RETURN_VOID }) return FINAL
        if (accepted == "$FOLLOW_LIST->getItems()Ljava/util/List;" && method.toString() == accepted &&
            owner.superclass == "Lcom/ss/android/ugc/aweme/base/api/BaseResponse;" &&
            method.accessFlags in setOf(1, 17) && body.registerCount == 2 && insns.size == 2 &&
            insns[0].opcode == Opcode.IGET_OBJECT && insns[1].opcode == Opcode.RETURN_OBJECT &&
            (insns[0] as TwoRegisterInstruction).registerB == 1 &&
            (insns[0] as TwoRegisterInstruction).registerA == (insns[1] as OneRegisterInstruction).registerA &&
            ((insns[0] as ReferenceInstruction).reference as? FieldReference)?.let {
                it.definingClass == FOLLOW_LIST && it.type == "Ljava/util/List;" &&
                    owner.fields.count { field -> field.name == it.name && field.type == it.type } == 1
            } == true) return ITEMS
        if (accepted in ve && method.toString() == accepted && method.accessFlags == 1 &&
            owner.superclass == "Ljava/lang/Object;" && method.parameterRegister(0, "Ljava/lang/String;") < 16 &&
            unchangedInput(method, method.parameterRegister(0, "Ljava/lang/String;")) &&
            insns.filter { it.opcode in setOf(Opcode.RETURN, Opcode.RETURN_OBJECT) }.all {
                (it as OneRegisterInstruction).registerA < 16 } &&
            refs.any { it.definingClass == "Lcom/ss/android/vesdk/VEGetABManager;" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;",
                    if (method.returnType == "Ljava/lang/Boolean;") "Z" else method.returnType, "Z") &&
                it.returnType == (if (method.returnType == "Ljava/lang/Boolean;") "Z" else method.returnType) &&
                it.name == when(method.returnType) { "I" -> "getIntValue"; "Ljava/lang/Boolean;" -> "getBooleanValue"; else -> "getStringValue" } } &&
            insns.any { (it as? ReferenceInstruction)?.reference?.toString() == "$VE_OWNER->sConfigs:Ljava/util/HashMap;" } &&
            insns.any { it.opcode == if (method.returnType == "I") Opcode.RETURN else Opcode.RETURN_OBJECT }) return VE
        if (accepted == "LX/1L9I;->LIZ(LX/1L9K;ZLjava/lang/String;Ljava/util/Map;)V" &&
            owner.superclass == "Landroid/widget/LinearLayout;" && !static && method.accessFlags == 17 &&
            method.returnType == "V" && method.parameterTypes.size == 4 && method.parameterTypes[0].startsWith("LX/") &&
            method.parameterTypes.drop(1) == listOf("Z", "Ljava/lang/String;", "Ljava/util/Map;") &&
            unchangedInput(method, method.parameterRegister(0) - 1) && unchangedInput(method, method.parameterRegister(0)) &&
            refs.any { it.toString() == "Landroid/view/View;->getLayoutParams()Landroid/view/ViewGroup${'$'}LayoutParams;" } &&
            refs.any { it.toString() == "Landroid/view/View;->findViewById(I)Landroid/view/View;" } &&
            listOf("Lcom/ss/android/ugc/aweme/base/model/UrlModel;", "Lcom/bytedance/lighten/loader/SmartImageView;").all { type ->
                insns.any { ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == type }
            } && insns.any { it.opcode == Opcode.RETURN_VOID }) return STICKER
        if (accepted == SCHEMA_TARGET && schemaShape(method) && insns.filter { it.opcode == Opcode.RETURN_OBJECT }.all {
                (it as OneRegisterInstruction).registerA < 16 } &&
            insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
                .containsAll(listOf("activity_center_entrance_v2", "special", "schemaPath", "url", "urlQuery", "schemaQuery")) &&
            refs.any { it.toString() == "Landroid/net/Uri${'$'}Builder;->appendQueryParameter(Ljava/lang/String;Ljava/lang/String;)Landroid/net/Uri${'$'}Builder;" }) return SCHEMA
        return null
    }
    fun mutationAllowed(mode: String, method: Method, index: Int?, code: String?): Boolean {
        val i = index?.let { method.implementation?.instructions?.elementAtOrNull(it) } ?: return false
        fun compact(value: String?) = value?.filterNot(Char::isWhitespace)
        val expected = when(mode) {
            PROGRESS -> {
                if (index != 0) return false
                "invoke-static/range {p1 .. p5}, Lapp/morphe/extension/tiktok/seen/SeenVideoHistory;->onPlayProgressChange(Ljava/lang/String;JJ)V"
            }
            FOLLOW -> {
                if (i.opcode != Opcode.RETURN_OBJECT) return false
                val r = (i as OneRegisterInstruction).registerA
                "if-eqz v$r, :morphe_skip_filter_$index\ninvoke-static/range {v$r .. v$r}, Lapp/morphe/extension/tiktok/feedfilter/FeedItemsFilter;->filter($FOLLOW_LIST)V\n:morphe_skip_filter_$index\nnop"
            }
            FINAL -> {
                if (i.opcode != Opcode.RETURN_VOID) return false
                "invoke-static/range {p1 .. p1}, Lapp/morphe/extension/tiktok/feedfilter/FeedItemsFilter;->filterFinal($FOLLOW_LIST)V"
            }
            ITEMS -> {
                if (i.opcode != Opcode.RETURN_OBJECT) return false
                "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/tiktok/feedfilter/FeedItemsFilter;->filter($FOLLOW_LIST)V"
            }
            VE -> {
                val returnOpcode = if (method.returnType == "I") Opcode.RETURN else Opcode.RETURN_OBJECT
                if (i.opcode != returnOpcode) return false
                val r = (i as OneRegisterInstruction).registerA
                val name = ve[method.toString()] ?: return false
                val move = if (returnOpcode == Opcode.RETURN) "move-result" else "move-result-object"
                "invoke-static {p1, v$r}, $RUNTIME->$name(Ljava/lang/String;${method.returnType})${method.returnType}\n$move v$r"
            }
            SCHEMA -> {
                if (i.opcode != Opcode.RETURN_OBJECT) return false
                val r = (i as OneRegisterInstruction).registerA
                "invoke-static {v$r}, $RUNTIME->transformActivityCenterSchema(Ljava/lang/String;)Ljava/lang/String;\nmove-result-object v$r"
            }
            STICKER -> {
                if (i.opcode != Opcode.RETURN_VOID) return false
                "invoke-static/range {p0 .. p1}, Lapp/morphe/extension/tiktok/download/StickerGallerySaver;->attachSaveImageButton(Landroid/view/View;Ljava/lang/Object;)V"
            }
            else -> return false
        }
        if (mode !in setOf(FOLLOW, FINAL, ITEMS)) return compact(code) == compact(expected)
        // The three independently configured feed features share the same native
        // response boundary. Each still gets its exact typed hook and branch label.
        val feed = "Lapp/morphe/extension/tiktok/feedfilter/FeedItemsFilter;"
        val variants = listOf(
            expected,
            expected.replace(feed, "Lapp/morphe/extension/tiktok/feedfilter/AdvancedFeedFilter;")
                .replace("morphe_skip_filter_", "blueit_skip_advanced_filter_"),
            expected.replace(feed, "Lapp/morphe/extension/tiktok/feedfilter/SeenVideoFeedFilter;")
                .replace("morphe_skip_filter_", "blueit_skip_seen_filter_"),
        )
        return variants.any { compact(code) == compact(it) }
    }
}
