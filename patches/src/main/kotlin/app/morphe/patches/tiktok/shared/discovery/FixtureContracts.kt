package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.formatter.DexFormatter
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.google.gson.JsonParser

/** Reviewed patch-time contracts for native assumptions not yet generalized. */
internal object FixtureContracts {
    fun signature(method: Method): String = buildString {
        append(method).append('|').append(method.accessFlags).append('|')
        val body = method.implementation ?: return@buildString
        append(body.registerCount).append('\n')
        body.instructions.forEach { i ->
            append(i.opcode.name)
            if (i is OneRegisterInstruction) append(" a=").append(i.registerA)
            if (i is TwoRegisterInstruction) append(" b=").append(i.registerB)
            if (i is ThreeRegisterInstruction) append(" c=").append(i.registerC)
            if (i is FiveRegisterInstruction || i is RegisterRangeInstruction) append(" args=").append(i.argumentRegisters())
            if (i is WideLiteralInstruction) append(" literal=").append(i.wideLiteral)
            if (i is ReferenceInstruction) append(" ref=").append(i.reference)
            if (i is OffsetInstruction) append(" offset=").append(i.codeOffset)
            if (i is SwitchPayload) i.switchElements.forEach { append(" case=").append(it.key).append(':').append(it.offset) }
            if (i is ArrayPayload) {
                append(" width=").append(i.elementWidth)
                i.arrayElements.forEach { append(" element=").append(it) }
            }
            append('\n')
        }
        body.tryBlocks.forEach { block ->
            append("try=").append(block.startCodeAddress).append(':').append(block.codeUnitCount)
            block.exceptionHandlers.forEach { append('|').append(it.exceptionType).append(':').append(it.handlerCodeAddress) }
            append('\n')
        }
    }.let(HookEvidence::sha256)

    /** Pin class relationships and fields as well as the method body before editing native bytecode. */
    fun classSignature(owner: ClassDef): String = HookEvidence.sha256(buildString {
        append(owner.type).append('|').append(owner.accessFlags).append('|').append(owner.superclass).append('\n')
        owner.interfaces.sorted().forEach { append("interface:").append(it).append('\n') }
        owner.fields.map { field ->
            "field:$field:${field.accessFlags}:" +
                (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
        }.sorted().forEach { append(it).append('\n') }
        owner.methods.map { "method:$it:${it.accessFlags}" }.sorted().forEach { append(it).append('\n') }
    })

    /** Register and control-flow preserving signature with only obfuscated DEX names normalized. */
    fun portableSignature(method: Method): String = portableSignature(method, relaxedMembers = false)

    /** Preserve the entire bytecode layout while ignoring only app callee names. */
    fun portableMemberSignature(method: Method): String = portableSignature(method, relaxedMembers = true)

    private fun portableSignature(method: Method, relaxedMembers: Boolean): String = HookEvidence.sha256(buildString {
        append(HookEvidence.normalizedType(method.parameterTypes.joinToString("") + ")" + method.returnType))
            .append('|').append(method.accessFlags).append('|')
        val body = method.implementation ?: return@buildString
        append(body.registerCount).append('\n')
        val tokens = (if (relaxedMembers) HookEvidence.tokensIgnoringAppMethodNames(method)
                      else HookEvidence.tokens(method)).drop(1).iterator()
        body.instructions.forEach { instruction ->
            if (instruction.opcode == com.android.tools.smali.dexlib2.Opcode.NOP) return@forEach
            append(tokens.next())
            if (instruction is OneRegisterInstruction) append(" a=").append(instruction.registerA)
            if (instruction is TwoRegisterInstruction) append(" b=").append(instruction.registerB)
            if (instruction is ThreeRegisterInstruction) append(" c=").append(instruction.registerC)
            if (instruction is FiveRegisterInstruction || instruction is RegisterRangeInstruction)
                append(" args=").append(instruction.argumentRegisters())
            if (instruction is WideLiteralInstruction) append(" literal=").append(instruction.wideLiteral)
            if (instruction is OffsetInstruction) append(" offset=").append(instruction.codeOffset)
            if (instruction is SwitchPayload) instruction.switchElements.forEach {
                append(" case=").append(it.key).append(':').append(it.offset)
            }
            if (instruction is ArrayPayload) {
                append(" width=").append(instruction.elementWidth)
                instruction.arrayElements.forEach { append(" element=").append(it) }
            }
            append('\n')
        }
        body.tryBlocks.forEach { block ->
            append("try=").append(block.startCodeAddress).append(':').append(block.codeUnitCount)
            block.exceptionHandlers.forEach {
                append('|').append(HookEvidence.normalizedType(it.exceptionType ?: "<all>"))
                    .append(':').append(it.handlerCodeAddress)
            }
            append('\n')
        }
    })

    fun portableClassSignature(owner: ClassDef): String = HookEvidence.sha256((
        listOf("access:" + owner.accessFlags, "super:" + HookEvidence.normalizedType(owner.superclass ?: "")) +
            owner.interfaces.map { "interface:" + HookEvidence.normalizedType(it) }.sorted() +
            owner.fields.map { field ->
                "field:" + HookEvidence.normalizedType(field.type) + ":" + field.accessFlags + ":" +
                    (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
            }.sorted() +
            owner.methods.map {
                "method:" + HookEvidence.normalizedMember(owner.type, it.name) + ":" + portableSignature(it)
            }.sorted()).joinToString("\n"))

    /** Only these reviewed entry hooks may survive unrelated changes to their declaring class. */
    fun methodScopedHooks(): Set<String> = ReviewedMethodScopes.hooks + setOf(
        "LX/02z2;->LJFF(IILjava/lang/String;Z)I",
        "Lcom/ss/android/ugc/aweme/live/livehostimpl/LiveHostUser;->popCaptchaV2(" +
            "Landroid/app/Activity;Ljava/lang/String;LX/1NRi;Landroidx/fragment/app/Fragment;)V",
        "Lcom/ss/android/ugc/aweme/main/MainActivity;->onCreate(Landroid/os/Bundle;)V",
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;->M9()Ljava/util/List;",
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;->QB()Ljava/util/List;",
        "Lcom/ss/android/ugc/aweme/offlinemode/ui/sheet/OfflineModeSheetPageAssem;-><clinit>()V",
        "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/support/SupportGroupVM;->defaultState()LX/06Ty;",
        "Lcom/ss/ttvideoengine/TTVideoEngine;->setLooping(Z)V",
        "LX/0AIU;->LJFF()Ljava/util/List;",
        SettingsContracts.acceptedTargets.getValue("settings.clickWrapper"),
        SettingsContracts.acceptedTargets.getValue("settings.function2"),
    )

    private val relocatedMethodScopes = setOf(
        "LX/02z2;->LJFF(IILjava/lang/String;Z)I",
        "LX/0AIU;->LJFF()Ljava/util/List;",
        SettingsContracts.acceptedTargets.getValue("settings.clickWrapper"),
        SettingsContracts.acceptedTargets.getValue("settings.function2"),
    )

    // These filters transform only the returned List. Pin the complete getter body
    // and its owner/field boundary; the untouched native callees are not injection sites.
    private val tabReturnScopes = setOf(
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;->M9()Ljava/util/List;",
        "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;->QB()Ljava/util/List;",
    )

    /** The offline provider is found by its caller and may move to another obfuscated class. */
    fun portableMethodScopeSignature(owner: ClassDef, method: Method, acceptedMethod: String): String =
        if (acceptedMethod in ReviewedMethodScopes.hooks || acceptedMethod in relocatedMethodScopes) portableReturnScopeSignature(owner, method)
        else if (acceptedMethod in tabReturnScopes) portableReturnScopeSignature(owner, method)
        else portableScopeSignature(owner, method)

    private const val AWEME_VIDEO_GETTER =
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;->getVideo()Lcom/ss/android/ugc/aweme/feed/model/Video;"
    private const val CACHED_FEED_READ =
        "LX/04Ju;->LJIJJ()Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"

    /** Reviewed nullable object-return hooks: no native instructions or scratch registers are changed. */
    fun typedReturnBoundary(method: Method, acceptedMethod: String): Boolean {
        val body = method.implementation ?: return false
        val returns = body.instructions.filter { it.opcode == Opcode.RETURN_OBJECT }.toList()
        if (returns.isEmpty() || returns.any { (it as OneRegisterInstruction).registerA >= body.registerCount })
            return false
        return when (acceptedMethod) {
            AWEME_VIDEO_GETTER -> method.toString() == acceptedMethod && method.accessFlags == AccessFlags.PUBLIC.value
            CACHED_FEED_READ -> method.definingClass.startsWith("LX/") && method.parameterTypes.isEmpty() &&
                method.returnType == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" &&
                method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value) &&
                body.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
                    .map { it.string }.toSet().containsAll(setOf("feed_use_cache_size", "tryUseCache list size "))
            else -> false
        }
    }

    fun typedReturnInjection(method: Method, index: Int?, code: String?): Boolean {
        val instruction = index?.let { method.implementation?.instructions?.elementAtOrNull(it) }
            ?: return false
        if (instruction.opcode != Opcode.RETURN_OBJECT) return false
        val register = (instruction as OneRegisterInstruction).registerA
        val call = when (method.returnType) {
            "Lcom/ss/android/ugc/aweme/feed/model/Video;" ->
                "Lapp/morphe/extension/tiktok/download/DownloadsPatch;->patchVideoObject(${method.returnType})V"
            "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" ->
                "Lapp/morphe/extension/tiktok/feedfilter/ForYouFeedGuard;->markAndFilter(${method.returnType})V"
            else -> return false
        }
        return code?.trim() == "invoke-static/range {v$register .. v$register}, $call"
    }

    private val booleanReplacementHooks = mapOf(
        "Lcom/bytedance/lobby/google/GoogleAuth;->isAvailable()Z" to
            (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value),
        "Lcom/ss/android/ugc/aweme/services/MandatoryLoginService;->enableForcedLogin(Z)Z" to AccessFlags.PUBLIC.value,
        "Lcom/ss/android/ugc/aweme/services/MandatoryLoginService;->shouldShowForcedLogin(Z)Z" to AccessFlags.PUBLIC.value,
    )

    /** These named predicates are replaced by a constant false at entry, with no native reads. */
    fun booleanReplacementBoundary(method: Method, acceptedMethod: String): Boolean =
        booleanReplacementHooks[acceptedMethod]?.let { flags ->
            method.toString() == acceptedMethod && method.accessFlags == flags &&
                method.returnType == "Z" && (method.implementation?.registerCount ?: 0) >= 1 &&
                method.implementation?.instructions?.firstOrNull() != null
        } == true

    fun booleanReplacementInjection(index: Int?, code: String?): Boolean =
        index == 0 && code?.trim()?.lines()?.map(String::trim)?.filter(String::isNotEmpty) ==
            listOf("const/4 v0, 0x0", "return v0")

    /** The declared result is a state interface; NEW_INSTANCE reveals its concrete implementation. */
    fun openDebugDiscoveryBoundary(method: Method): Boolean {
        val owner = "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/support/cells/OpenDebugCellVM;"
        val body = method.implementation ?: return false
        val insns = body.instructions.toList()
        if (method.definingClass != owner || method.name != "defaultState" ||
            method.parameterTypes.isNotEmpty() || !method.returnType.startsWith("LX/") ||
            method.accessFlags != (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) ||
            body.registerCount != 6 || body.tryBlocks.any() || insns.map { it.opcode } != listOf(
                Opcode.NEW_INSTANCE, Opcode.NEW_INSTANCE, Opcode.INVOKE_DIRECT, Opcode.CONST,
                Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT_OBJECT, Opcode.NEW_INSTANCE, Opcode.CONST_16,
                Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT, Opcode.RETURN_OBJECT)) return false
        fun type(index: Int) = (insns[index] as? ReferenceInstruction)?.reference?.toString()
        fun reg(index: Int) = (insns[index] as? OneRegisterInstruction)?.registerA
        fun call(index: Int, target: String, args: List<Int>) =
            (insns[index] as? ReferenceInstruction)?.reference?.toString() == target &&
                insns[index].argumentRegisters() == args
        val state = type(0) ?: return false
        val wrapper = type(1) ?: return false
        val lambda = type(6) ?: return false
        val titleId = (insns[3] as? WideLiteralInstruction)?.wideLiteral ?: return false
        val discriminator = (insns[7] as? WideLiteralInstruction)?.wideLiteral ?: return false
        return state.startsWith("LX/") && wrapper.startsWith("LX/") &&
            lambda.startsWith("Lkotlin/jvm/internal/AwS") && titleId in 0x7f000000L..0x7fffffffL &&
            discriminator in 0L..32767L && reg(0) == 4 && reg(1) == 3 && reg(3) == 0 &&
            reg(5) == 2 && reg(6) == 1 && reg(7) == 0 && reg(10) == 4 &&
            call(2, "$wrapper-><init>(Ljava/lang/Object;)V", listOf(3, 5)) &&
            call(4, "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;", listOf(0)) &&
            call(8, "$lambda-><init>(${owner}I)V", listOf(1, 5, 0)) &&
            call(9, "$state-><init>(Ljava/lang/Integer;$wrapper$lambda)V", listOf(4, 2, 3, 1))
    }

    /** The FYP response has unique feed markers but renamed app method references in 47.1.3. */
    fun memberRenameHooks(): Set<String> = setOf(
        "Lcom/ss/android/ugc/aweme/feed/api/FeedApi;->LIZIZ(LX/06F0;)Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;",
    )

    /** Context is p1; this hook inserts before the original first instruction. */
    fun entryHooks(): Set<String> = setOf(
        "Lcom/ss/android/ugc/aweme/legoImp/task/JatoInitTask;->run(Landroid/content/Context;)V",
    )

    private const val CACHED_FEED_ENTRY =
        "LX/04Ju;->LIZIZ(Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;)V"
    private const val FEED_ITEMS_GETTER =
        "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;->getItems()Ljava/util/List;"
    private const val CAPTCHA_V2 = "Lcom/ss/android/ugc/aweme/sec/SecApiImpl;->popCaptchaV2(" +
        "Landroid/app/Activity;Ljava/lang/String;LX/17qC;Landroidx/fragment/app/Fragment;)V"
    private const val CAPTCHA_LEGACY = "Lcom/ss/android/ugc/aweme/sec/SecApiImpl;->popCaptcha(" +
        "Landroid/app/Activity;ILX/17qC;)V"
    private const val OEC_CAPTCHA = "Lcom/tts/oecverify/verify/RiskControlService;->execute(" +
        "LX/16eW;Lcom/tts/oecverify/BdTuringCallback;)Z"

    /** The entry parameter is a FeedItemList; the return register has become an Iterator. */
    fun cachedFeedEntryBoundary(owner: ClassDef, method: Method): Boolean {
        val body = method.implementation ?: return false
        val insns = body.instructions.toList()
        val first = insns.firstOrNull() as? OffsetInstruction ?: return false
        val getItems = insns.getOrNull(1)
        val result = insns.getOrNull(2)
        val param = body.registerCount - 1
        val strings = insns.mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
            .map { it.string }.toSet()
        return owner.type == method.definingClass && owner.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
            owner.superclass == "Ljava/lang/Object;" && owner.interfaces.none() &&
            method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value) &&
            method.parameterTypes.map(CharSequence::toString) == listOf("Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;") &&
            method.returnType == "V" && body.registerCount >= 2 && body.tryBlocks.none() &&
            first.opcode == Opcode.IF_EQZ && (first as? OneRegisterInstruction)?.registerA == param &&
            first.codeOffset == insns.dropLast(1).sumOf { it.codeUnits } &&
            getItems?.opcode == Opcode.INVOKE_VIRTUAL &&
            (getItems as? ReferenceInstruction)?.reference?.toString() == FEED_ITEMS_GETTER &&
            (getItems as? FiveRegisterInstruction)?.let { it.registerCount == 1 && it.registerC == param } == true &&
            result?.opcode == Opcode.MOVE_RESULT_OBJECT && (result as? OneRegisterInstruction)?.registerA == 0 &&
            insns.lastOrNull()?.opcode == Opcode.RETURN_VOID && insns.count { it.opcode == Opcode.RETURN_VOID } == 1 &&
            "fetchFeeds, filter by is ad" in strings && "fetchFeeds, filter by is duplicate" in strings
    }

    /** Both CAPTCHA methods call this callback only on the suppressed path. */
    fun captchaEntryBoundary(owner: ClassDef, method: Method, callback: ClassDef?, v2: Boolean): Boolean {
        val body = method.implementation ?: return false
        val p = method.parameterTypes.map(CharSequence::toString)
        val callbackType = p.getOrNull(2) ?: return false
        val callbackOwner = callback ?: return false
        val callbackMethod = callbackOwner.methods.singleOrNull {
            it.name == "LIZJ" && it.parameterTypes.none() && it.returnType == "V"
        } ?: return false
        val callbackBody = callbackMethod.implementation ?: return false
        val marker = if (v2) "popCaptchaV2 - riskInfo = " else "popCaptcha - errorcode = "
        val strings = body.instructions.mapNotNull { (it as? ReferenceInstruction)?.reference as? StringReference }
            .map { it.string }.toSet()
        return owner.type == method.definingClass &&
            owner.type == "Lcom/ss/android/ugc/aweme/sec/SecApiImpl;" &&
            owner.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
            owner.superclass == "Ljava/lang/Object;" &&
            owner.interfaces.toList() == listOf("Lcom/ss/android/ugc/aweme/secapi/ISecApi;") &&
            method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
            method.name == (if (v2) "popCaptchaV2" else "popCaptcha") &&
            method.returnType == "V" &&
            (if (v2) p.size in 4..5 else p.size == 3) &&
            p[0] == "Landroid/app/Activity;" && p[1] == (if (v2) "Ljava/lang/String;" else "I") &&
            callbackType.startsWith("LX/") && callbackType.endsWith(";") &&
            (!v2 || (p[3] == "Landroidx/fragment/app/Fragment;" &&
                (p.size == 4 || p[4] == "Ljava/lang/String;"))) &&
            body.registerCount > p.size + 1 && body.tryBlocks.none() &&
            body.instructions.firstOrNull()?.opcode == Opcode.INVOKE_STATIC &&
            body.instructions.lastOrNull()?.opcode == Opcode.RETURN_VOID &&
            body.instructions.count { it.opcode == Opcode.RETURN_VOID } == 1 && marker in strings &&
            callbackOwner.type == callbackType && callbackOwner.accessFlags == AccessFlags.PUBLIC.value &&
            callbackOwner.superclass == "Ljava/lang/Object;" && callbackOwner.interfaces.none() && callbackOwner.fields.none() &&
            callbackMethod.accessFlags == AccessFlags.PUBLIC.value &&
            callbackBody.registerCount == 1 && callbackBody.tryBlocks.none() &&
            callbackBody.instructions.map { it.opcode }.toList() == listOf(Opcode.RETURN_VOID)
    }

    /** OEC only injects before the original entry. The callback and the first field read are pinned. */
    fun oecCaptchaEntryBoundary(owner: ClassDef, method: Method, callback: ClassDef?): Boolean {
        val body = method.implementation ?: return false
        val first = body.instructions.take(3).toList()
        val parameters = method.parameterTypes.map(CharSequence::toString)
        val callbackOwner = callback ?: return false
        val callbackMethods = callbackOwner.methods.toList()
        val field = (first.getOrNull(1) as? ReferenceInstruction)?.reference as? FieldReference
        val fields = owner.fields.associate { it.name to it.type }
        return owner.type == "Lcom/tts/oecverify/verify/RiskControlService;" &&
            method.definingClass == owner.type && owner.accessFlags ==
                (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
            owner.superclass == "Ljava/lang/Object;" && owner.interfaces.size == 1 &&
            owner.interfaces.single().startsWith("LX/") &&
            owner.fields.count() == 4 && fields.size == 4 &&
            fields["mDialogShowing"]?.let { it.startsWith("LX/") && it.endsWith(";") } == true &&
            fields["mForceFetchSettings"] == "Z" &&
            fields["mShowingRequestPath"] == "Ljava/lang/String;" &&
            fields["mSyncSettings"] == "Z" &&
            method.name == "execute" && method.accessFlags == AccessFlags.PUBLIC.value &&
            method.returnType == "Z" && parameters.size == 2 &&
            parameters[0].startsWith("LX/") && parameters[0].endsWith(";") &&
            parameters[1] == callbackOwner.type &&
            callbackOwner.type == "Lcom/tts/oecverify/BdTuringCallback;" &&
            callbackOwner.accessFlags ==
                (AccessFlags.PUBLIC.value or AccessFlags.INTERFACE.value or AccessFlags.ABSTRACT.value) &&
            callbackOwner.superclass == "Ljava/lang/Object;" && callbackOwner.interfaces.none() &&
            callbackOwner.fields.none() && callbackMethods.size == 2 &&
            callbackMethods.map { it.name }.toSet() == setOf("onFail", "onSuccess") &&
            callbackMethods.all { it.parameterTypes.map(CharSequence::toString) ==
                listOf("I", "Lorg/json/JSONObject;") && it.returnType == "V" &&
                it.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.ABSTRACT.value) &&
                it.implementation == null } &&
            body.registerCount == 19 && body.tryBlocks.none { it.startCodeAddress == 0 } &&
            first.getOrNull(0)?.opcode == Opcode.MOVE_OBJECT_FROM16 &&
            (first[0] as? TwoRegisterInstruction)?.let {
                it.registerA == 3 && it.registerB == body.registerCount - 3
            } == true && first.getOrNull(1)?.opcode == Opcode.IGET_OBJECT &&
            (first[1] as? TwoRegisterInstruction)?.let {
                it.registerA == 2 && it.registerB == 3
            } == true && field?.let { it.definingClass == owner.type &&
                it.name == "mShowingRequestPath" && it.type == "Ljava/lang/String;" } == true &&
            first.getOrNull(2)?.opcode == Opcode.CONST_4 &&
            (first[2] as? OneRegisterInstruction)?.registerA == 0 &&
            (first[2] as? WideLiteralInstruction)?.wideLiteral == 1L
    }

    /** The LIVE suppression path calls this callback before returning from the entry. */
    fun requireLiveCaptchaCallback(callback: ClassDef?, descriptor: String) {
        if (callback == null || callback.type != descriptor ||
            callback.accessFlags and AccessFlags.INTERFACE.value == 0 ||
            callback.methods.count { it.name == "LIZJ" && it.parameterTypes.none() &&
                it.returnType == "V" && it.accessFlags and AccessFlags.ABSTRACT.value != 0 &&
                it.implementation == null } != 1)
            throw PatchException("Changed LIVE CAPTCHA callback contract for $descriptor; injection refused")
    }

    fun entrySignature(owner: ClassDef, method: Method): String {
        val body = method.implementation
            ?: throw PatchException("No original entry body for $method")
        if (owner.type != method.definingClass || body.instructions.count() < 3 ||
            body.instructions.take(3).any { it.opcode == com.android.tools.smali.dexlib2.Opcode.NOP })
            throw PatchException("Invalid reviewed entry boundary for $method")
        val tokens = HookEvidence.tokens(method).drop(1)
        return HookEvidence.sha256(buildString {
            append("owner:").append(owner.type).append('\n')
            append("access:").append(owner.accessFlags).append('\n')
            append("super:").append(HookEvidence.normalizedType(owner.superclass ?: "")).append('\n')
            owner.interfaces.map { HookEvidence.normalizedType(it) }.sorted().forEach {
                append("interface:").append(it).append('\n')
            }
            owner.fields.map {
                "field:" + HookEvidence.normalizedType(it.type) + ":" + it.accessFlags + ":" +
                    (it.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
            }.sorted().forEach { append(it).append('\n') }
            append("method:").append(HookEvidence.normalizedType(
                method.parameterTypes.joinToString("") + ")" + method.returnType)).append('\n')
            append("flags:").append(method.accessFlags).append('\n')
            append("registers:").append(body.registerCount).append('\n')
            append("try-at-entry:").append(body.tryBlocks.any { it.startCodeAddress == 0 }).append('\n')
            body.instructions.take(3).forEachIndexed { index, instruction ->
                append(tokens[index])
                if (instruction is OneRegisterInstruction) append(" a=").append(instruction.registerA)
                if (instruction is TwoRegisterInstruction) append(" b=").append(instruction.registerB)
                if (instruction is ThreeRegisterInstruction) append(" c=").append(instruction.registerC)
                if (instruction is WideLiteralInstruction) append(" literal=").append(instruction.wideLiteral)
                if (instruction is OffsetInstruction) append(" offset=").append(instruction.codeOffset)
                append('\n')
            }
        })
    }

    /** The target body is pinned separately. This pins its class hierarchy and fields it reads. */
    fun portableScopeSignature(owner: ClassDef, method: Method): String =
        portableScopeSignature(owner, method, allowSelfCalls = false)

    /** A return-site hook can inspect a sibling method while preserving its own injection boundary. */
    fun portableReturnScopeSignature(owner: ClassDef, method: Method): String =
        portableScopeSignature(owner, method, allowSelfCalls = true)

    /** Pin the same-owner call closure as well as the return hook's field and class boundary. */
    fun portableCallScopeSignature(owner: ClassDef, method: Method): String =
        HookEvidence.sha256(portableCallScopeBodies(owner, method).joinToString("\n"))

    /** Diagnostic hashes in call traversal order; no unreviewed bytes are accepted from these. */
    fun portableCallScopeTrace(owner: ClassDef, method: Method): List<String> =
        portableCallScopeBodies(owner, method).mapIndexed { index, body ->
            "$index:${HookEvidence.sha256(body)}"
        }

    /** Bounded evidence for the two changed nodes in the Top-Tab call closure. */
    fun portableCallScopeDetails(owner: ClassDef, method: Method): List<String> {
        val details = mutableListOf<String>()
        portableCallScopeBodies(owner, method, details)
        return details
    }

    private fun portableCallScopeBodies(owner: ClassDef, method: Method,
                                       details: MutableList<String>? = null): List<String> {
        val visited = mutableSetOf<String>()
        val bodies = mutableListOf<String>()
        fun visit(current: Method) {
            if (!visited.add(current.toString())) return
            if (visited.size > 64) throw PatchException("Scoped call closure too large in ${owner.type}")
            val scope = portableScopeSignature(owner, current, allowSelfCalls = true)
            val descriptor = HookEvidence.normalizedType(
                current.parameterTypes.joinToString("") + ")" + current.returnType)
            bodies += descriptor + ":" + portableSignature(current) + ":" + scope
            val index = bodies.lastIndex
            if (details != null && index in 3..4) {
                val instructions = current.implementation!!.instructions.toList()
                details += "$index:$current registers=${current.implementation!!.registerCount} " +
                    "scope=$scope instructions=${instructions.size} " +
                    instructions.take(120).map { instruction ->
                        buildString {
                            append(instruction.opcode.name)
                            if (instruction is OneRegisterInstruction) append(" a=").append(instruction.registerA)
                            if (instruction is TwoRegisterInstruction) append(" b=").append(instruction.registerB)
                            if (instruction is WideLiteralInstruction) append(" literal=").append(instruction.wideLiteral)
                            if (instruction is ReferenceInstruction) append(" ref=").append(instruction.reference)
                            if (instruction is OffsetInstruction) append(" offset=").append(instruction.codeOffset)
                        }
                    }
            }
            current.implementation!!.instructions.mapNotNull { instruction ->
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
            }.filter { it.definingClass == owner.type }.distinctBy { it.toString() }.forEach { reference ->
                val callee = owner.methods.singleOrNull { it.toString() == reference.toString() }
                    ?: throw PatchException("Missing scoped callee $reference")
                if (callee.implementation == null) throw PatchException("No scoped callee body $reference")
                visit(callee)
            }
        }
        visit(method)
        if (visited.size < 2) throw PatchException("No same-owner callee in ${owner.type}")
        return bodies
    }

    private fun portableScopeSignature(owner: ClassDef, method: Method, allowSelfCalls: Boolean): String {
        if (method.definingClass != owner.type || method.implementation == null)
            throw PatchException("No original method body in scoped owner ${owner.type}")
        val fields = method.implementation!!.instructions.mapNotNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
        }.filter { it.definingClass == owner.type }.map { reference ->
            val field = owner.fields.singleOrNull { it.name == reference.name && it.type == reference.type }
                ?: throw PatchException("Unresolved scoped field $reference in ${owner.type}")
            "field:" + HookEvidence.normalizedMember(owner.type, field.name) + ":" +
                HookEvidence.normalizedType(field.type) + ":" + field.accessFlags + ":" +
                (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
        }.sorted()
        if (!allowSelfCalls && method.implementation!!.instructions.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                    ?.let { it.definingClass == owner.type && it.name != method.name } == true
            }) throw PatchException("Scoped hook calls another method on ${owner.type}")
        return HookEvidence.sha256((listOf(
            "access:" + owner.accessFlags,
            "super:" + HookEvidence.normalizedType(owner.superclass ?: ""),
        ) + owner.interfaces.map { "interface:" + HookEvidence.normalizedType(it) }.sorted() + fields)
            .joinToString("\n"))
    }

    data class Reviewed(val packageName: String, val versionCode: Long, val apkSha256: String,
                        val methods: Map<String, String>, val classes: Map<String, String>,
                        val portableMethods: Map<String, String> = emptyMap(),
                        val portableClasses: Map<String, String> = emptyMap(),
                        val hookMethods: Map<String, String> = emptyMap(),
                        val scopedMethods: Map<String, String> = emptyMap(),
                        val memberRenameMethods: Map<String, String> = emptyMap(),
                        val memberRenameScopes: Map<String, String> = emptyMap(),
                        val entryMethods: Map<String, String> = emptyMap())

    data class Selection(val contracts: Reviewed, val experimental: Boolean)

    /** An unknown APK may only reuse native hooks byte-for-byte from the baseline.
     * The method and its entire declaring class are separately checked before each edit.
     */
    fun select(packageName: String, versionCode: Long, actualSha256: String, exact: Reviewed?,
               baseline: Reviewed, experimentalOptIn: Boolean): Selection {
        if (packageName != baseline.packageName)
            throw PatchException("Expected global TikTok ${baseline.packageName}, got $packageName")
        if (exact?.apkSha256 == actualSha256) {
            if (versionCode != exact.versionCode)
                throw PatchException("Fixture version code changed: expected ${exact.versionCode}, got $versionCode")
            return Selection(exact, experimental = false)
        }
        if (!experimentalOptIn)
            throw PatchException("Unreviewed TikTok APK SHA-256: $actualSha256. Use TIKTOK_EXPERIMENTAL_PORTABLE=1 only for explicitly experimental patching")
        return Selection(baseline, experimental = true)
    }

    fun requireMatch(method: Method, expected: String?) {
        if (expected == null) throw PatchException("Missing reviewed fixture contract for $method; run discovery and review before enabling this APK")
        val actual = signature(method)
        if (expected != actual) throw PatchException("Changed fixture contract for $method: expected $expected, got $actual. Register, reference, literal or control-flow layout changed; injection refused")
    }

    fun requireNativeMatch(method: Method, owner: ClassDef, contracts: Reviewed, experimental: Boolean) {
        if (method.definingClass != owner.type || contracts.classes[owner.type] != classSignature(owner))
            throw PatchException("Changed class contract for ${owner.type}: inheritance, fields or method set changed${if (experimental) "; unreviewed APK injection refused" else ""}")
        requireMatch(method, contracts.methods[method.toString()])
    }

    /** A relocated hook needs the accepted hook identity and the complete portable contract. */
    fun requirePortableMatch(method: Method, owner: ClassDef, acceptedMethod: String, contracts: Reviewed,
                             callback: ClassDef? = null, nativeClasses: Map<String, ClassDef> = emptyMap()): String {
        if (method.definingClass != owner.type)
            throw PatchException("Portable hook owner mismatch for $method")
        val oldOwner = acceptedMethod.substringBefore("->")
        val expectedClass = contracts.portableClasses[oldOwner]
            ?: throw PatchException("Missing accepted portable class contract for $oldOwner")
        val expectedMethod = contracts.portableMethods[acceptedMethod]
            ?: throw PatchException("Missing accepted portable method contract for $acceptedMethod")
        val fullClassMatches = portableClassSignature(owner) == expectedClass
        val methodMatches = portableSignature(method) == expectedMethod
        val scopedOwnerMatches = oldOwner == owner.type ||
            ((acceptedMethod in relocatedMethodScopes || acceptedMethod in ReviewedMethodScopes.hooks) && HookEvidence.normalizedType(oldOwner) ==
                HookEvidence.normalizedType(owner.type))
        val scopedMatch = !fullClassMatches && methodMatches && acceptedMethod in methodScopedHooks() &&
            scopedOwnerMatches && contracts.scopedMethods[acceptedMethod]?.let {
                portableMethodScopeSignature(owner, method, acceptedMethod) == it
            } == true
        val memberRenameMatch = !methodMatches && acceptedMethod in memberRenameHooks() &&
            oldOwner == owner.type && method.returnType == "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;" &&
            method.implementation?.instructions?.count { it.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_OBJECT } == 1 &&
            listOf("fyp", "first_feed_duration").all { marker ->
                HookEvidence.tokens(method).any { it.contains("s:$marker") }
            } && contracts.memberRenameMethods[acceptedMethod]?.let {
                portableMemberSignature(method) == it
            } == true && contracts.memberRenameScopes[acceptedMethod]?.let {
                portableReturnScopeSignature(owner, method) == it
            } == true
        val entryMatch = !methodMatches && acceptedMethod in entryHooks() && oldOwner == owner.type &&
            method.returnType == "V" && method.parameterTypes.map(CharSequence::toString) ==
                listOf("Landroid/content/Context;") && contracts.entryMethods[acceptedMethod]?.let {
                    entrySignature(owner, method) == it
                } == true
        val feedEntryMatch = (!fullClassMatches || !methodMatches) && acceptedMethod == CACHED_FEED_ENTRY &&
            HookEvidence.normalizedType(oldOwner) == HookEvidence.normalizedType(owner.type) &&
            cachedFeedEntryBoundary(owner, method)
        val captchaEntryMatch = (!fullClassMatches || !methodMatches) &&
            acceptedMethod in setOf(CAPTCHA_V2, CAPTCHA_LEGACY) && oldOwner == owner.type &&
            captchaEntryBoundary(owner, method, callback, acceptedMethod == CAPTCHA_V2)
        val oecEntryMatch = (!fullClassMatches || !methodMatches) && acceptedMethod == OEC_CAPTCHA &&
            oldOwner == owner.type && oecCaptchaEntryBoundary(owner, method, callback)
        val siteMode = DownloadSuccessContracts.mode(method, owner, acceptedMethod, nativeClasses::get)
            ?: AvatarGradientContracts.mode(method, owner, acceptedMethod, contracts, nativeClasses::get)
            ?: ExternalBrowserContracts.mode(method, owner, acceptedMethod, nativeClasses::get)
            ?: ClearDisplayContracts.mode(method, owner, acceptedMethod)
            ?: PlayerFrameContracts.mode(method, owner, acceptedMethod, nativeClasses::get)
            ?: PlaybackSpeedContracts.mode(method, acceptedMethod, nativeClasses::get)
            ?: CommentCopyContracts.mode(method, owner, acceptedMethod, nativeClasses::get)
            ?: TranslationContracts.mode(method, owner, acceptedMethod, nativeClasses::get)
            ?: ThemeContracts.mode(method, owner, acceptedMethod, contracts, nativeClasses::get)
            ?: PromotionContracts.mode(method, owner, acceptedMethod)
            ?: DownloadPathContracts.mode(method, owner, acceptedMethod)
            ?: PublishDateContracts.mode(method, owner, acceptedMethod)
            ?: TakoContracts.mode(method, owner, acceptedMethod)
            ?: ReturnSiteContracts.mode(method, owner, acceptedMethod)
            ?: SettingsCategoryContracts.mode(method, owner, acceptedMethod)
            ?: StartupAdContracts.MODE.takeIf { acceptedMethod == StartupAdContracts.TARGET && StartupAdContracts.boundary(method, owner, nativeClasses::get) }
            ?: SiteContracts.mode(method, acceptedMethod)
            ?: SettingsContracts.mode(method, acceptedMethod).takeIf {
                owner.superclass == SettingsContracts.BASE ||
                    acceptedMethod == SettingsContracts.acceptedTargets.getValue("settings.composeTitle")
            }
            ?: CommentWatermarkContracts.MODE.takeIf {
                acceptedMethod == CommentWatermarkContracts.ACCEPTED && owner.superclass == "Ljava/lang/Object;" &&
                    CommentWatermarkContracts.boundary(method)
            }
        val typedReturnMatch = typedReturnBoundary(method, acceptedMethod)
        val booleanReplacementMatch = booleanReplacementBoundary(method, acceptedMethod)
        val classMatches = fullClassMatches || scopedMatch
        if ((!classMatches || !methodMatches) && !memberRenameMatch && !entryMatch && !feedEntryMatch &&
            !captchaEntryMatch && !oecEntryMatch && !booleanReplacementMatch && !typedReturnMatch && siteMode == null) {
            val changed = buildList {
                if (!classMatches) add("class (field types, inheritance or member structure)")
                if (!methodMatches) add("method (registers, literals, references, branches, switch or exception paths)")
            }
            val trace = if (acceptedMethod ==
                "Lcom/ss/android/ugc/aweme/main/assems/tabs/TabAbilityAssem;->M9()Ljava/util/List;" &&
                methodMatches && !classMatches) "; callScopeTrace=${portableCallScopeTrace(owner, method)}" +
                "; callScopeDetails=${portableCallScopeDetails(owner, method)}"
                else ""
            throw PatchException("Changed portable contract for $method: ${changed.joinToString(" and ")}; injection refused$trace")
        }
        return when {
            siteMode != null -> siteMode
            typedReturnMatch -> "experimental-typed-return"
            booleanReplacementMatch -> "experimental-boolean-replacement"
            memberRenameMatch -> "experimental-member-rename"
            entryMatch -> "experimental-entry-contract"
            feedEntryMatch -> "experimental-feed-entry"
            captchaEntryMatch -> "experimental-captcha-entry"
            oecEntryMatch -> "experimental-oec-entry"
            scopedMatch -> "experimental-method-scope"
            else -> "experimental-semantic-contract"
        }
    }

    fun loadOrNull(version: String): Reviewed? {
        val stream = FixtureContracts::class.java.getResourceAsStream("/tiktok-contracts/$version.json") ?: return null
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        if (root.get("version").asString != version || root.get("schema").asInt != 1)
            throw PatchException("Invalid reviewed fixture contract for TikTok $version")
        fun entries(name: String) = root.getAsJsonObject(name).entrySet().associate { it.key to it.value.asString }
        return Reviewed(root.get("package").asString, root.get("versionCode").asLong,
            root.get("sha256").asString, entries("methods"), entries("classes"),
            if (root.has("portableMethods")) entries("portableMethods") else emptyMap(),
            if (root.has("portableClasses")) entries("portableClasses") else emptyMap(),
            if (root.has("hookMethods")) entries("hookMethods") else emptyMap(),
            if (root.has("scopedMethods")) entries("scopedMethods") else emptyMap(),
            if (root.has("memberRenameMethods")) entries("memberRenameMethods") else emptyMap(),
            if (root.has("memberRenameScopes")) entries("memberRenameScopes") else emptyMap(),
            if (root.has("entryMethods")) entries("entryMethods") else emptyMap())
    }

    fun load(version: String): Reviewed = loadOrNull(version)
        ?: throw PatchException("No reviewed injection contracts for TikTok $version")
}
