package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object CommentCopyContracts {
    const val MODE = "experimental-comment-copy-typed-builder"
    const val COMMENT = "Lcom/ss/android/ugc/aweme/comment/model/Comment;"
    private const val COLLECT = "Lcom/ss/android/ugc/aweme/favorites/business/comment/CommentCollectViewHolder;"
    private const val APPEND = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
    private const val SANITIZER = "Lapp/morphe/extension/tiktok/comment/CommentCopySanitizer;->sanitizeCopiedCommentText(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    private val params = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/content/Context;", "Lcom/bytedance/bpea/basics/Cert;")
    private val accepted = mapOf(
        "collect" to "$COLLECT->copy($COMMENT${params[3]})V",
        "context" to "LX/1BaG;->LIZ(${params[2]}$COMMENT)V",
        "callback" to "Lkotlin/jvm/internal/AwS375S0200000_28_I1;->invoke\$155(Lkotlin/jvm/internal/AwS375S0200000_28_I1;Ljava/lang/Object;)Ljava/lang/Object;")
    private fun ref(i: Instruction?) = ((i as? ReferenceInstruction)?.reference as? MethodReference)
    fun helperBoundary(method: Method, owner: ClassDef?): Boolean = runCatching {
        if (method.accessFlags != 9 || method.returnType != "V" || method.parameterTypes.map(CharSequence::toString) != params ||
            owner?.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty()) return false
        val insns = method.implementation!!.instructions.toList()
        if (insns.size != 8 || insns[0].opcode !in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) ||
            ((insns[0] as ReferenceInstruction).reference as? StringReference)?.string != "clipboard" ||
            insns[1].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
            ref(insns[1])?.parameterTypes?.map(CharSequence::toString) != listOf(params[2], params[0]) || ref(insns[1])?.returnType != "Ljava/lang/Object;" ||
            insns[1].argumentRegisters() != listOf(method.parameterRegister(2), (insns[0] as OneRegisterInstruction).registerA)) return false
        val manager = method.resultRegister(1, "Ljava/lang/Object;")
        if (insns[3].opcode != Opcode.CHECK_CAST || (insns[3] as OneRegisterInstruction).registerA != manager ||
            ((insns[3] as ReferenceInstruction).reference as? TypeReference)?.type != "Landroid/content/ClipboardManager;" ||
            insns[4].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) || ref(insns[4])?.toString() !=
                "Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;" ||
            insns[4].argumentRegisters() != listOf(method.parameterRegister(0), method.parameterRegister(1))) return false
        val clip = method.resultRegister(4, "Landroid/content/ClipData;")
        insns[6].opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) && ref(insns[6])?.returnType == "V" &&
            ref(insns[6])?.parameterTypes?.map(CharSequence::toString) == listOf("Landroid/content/ClipboardManager;", "Landroid/content/ClipData;", params[3]) &&
            insns[6].argumentRegisters() == listOf(manager, clip, method.parameterRegister(3)) && insns[7].opcode == Opcode.RETURN_VOID &&
            manager != clip && manager !in (0..3).map { method.parameterRegister(it) } && clip !in (0..3).map { method.parameterRegister(it) }
    }.getOrDefault(false)
    fun helperFor(method: Method, resolve: (String) -> ClassDef?): MethodReference {
        val refs = method.implementation!!.instructions.mapNotNull { ref(it) }.filter {
            it.returnType == "V" && it.parameterTypes.map(CharSequence::toString) == params
        }.distinctBy { it.toString() }.filter { r -> resolve(r.definingClass)?.let { owner ->
            owner.methods.singleOrNull { it.toString() == r.toString() }?.let { helperBoundary(it, owner) }
        } == true }
        return refs.singleOrThrow("Unique actual read-only clipboard helper")
    }
    private fun target(method: Method, owner: ClassDef): String? {
        val types = method.parameterTypes.map(CharSequence::toString)
        val role = when {
            method.definingClass == COLLECT && method.name == "copy" && method.accessFlags == 17 && method.returnType == "V" &&
                types == listOf(COMMENT, params[3]) && owner.superclass == "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;" -> "collect"
            method.accessFlags == 17 && method.returnType == "V" && types == listOf(params[2], COMMENT) &&
                owner.superclass == "Ljava/lang/Object;" && owner.interfaces.size == 1 && owner.interfaces.single().startsWith("LX/") -> "context"
            method.accessFlags == 4169 && method.returnType == "Ljava/lang/Object;" && types == listOf(owner.type, "Ljava/lang/Object;") &&
                owner.type.startsWith("Lkotlin/jvm/internal/") && owner.superclass?.startsWith("LX/") == true &&
                "Lkotlin/jvm/functions/Function1;" in owner.interfaces && method.implementation!!.instructions.any {
                    ref(it)?.toString() == "Lcom/ss/android/ugc/now/interaction/assem/CommentItem;->getComment()$COMMENT"
                } -> "callback"
            else -> return null
        }
        return accepted.getValue(role)
    }
    data class Site(val index: Int, val copied: Int, val text: Int, val temps: List<Int>) {
        val code get() = if (temps.isEmpty()) "invoke-static {v$copied, v$text}, $SANITIZER\nmove-result-object v$copied"
            else "move-object/from16 v${temps[0]}, v$copied\nmove-object/from16 v${temps[1]}, v$text\n" +
                "invoke-static {v${temps[0]}, v${temps[1]}}, $SANITIZER\nmove-result-object v${temps[0]}\nmove-object/16 v$copied, v${temps[0]}"
    }
    fun sites(method: Method, helper: MethodReference): List<Site> {
        val body = method.implementation ?: throw PatchException("Missing comment copy body")
        val insns = body.instructions.toList()
        if (insns.none { ref(it)?.toString() == "$COMMENT->getUser()Lcom/ss/android/ugc/aweme/profile/model/User;" })
            throw PatchException("Missing native comment/user builder")
        val offsets = IntArray(insns.size)
        var offset = 0
        insns.forEachIndexed { n, i -> offsets[n] = offset; offset += i.codeUnits }
        val byOffset = offsets.withIndex().associate { it.value to it.index }
        val entries = body.tryBlocks.flatMap { it.exceptionHandlers }.map { it.handlerCodeAddress }.toMutableSet()
        insns.forEachIndexed { n, i -> if (i is OffsetInstruction) {
            if (i.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)) {
                val payload = insns[byOffset[offsets[n] + i.codeOffset] ?: throw PatchException("Missing copy switch") ] as SwitchPayload
                entries += payload.switchElements.map { offsets[n] + it.offset }
            } else if (i.opcode.name.startsWith("if-") || i.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) entries += offsets[n] + i.codeOffset
        } }
        val calls = insns.withIndex().filter { ref(it.value)?.toString() == helper.toString() }
        if (calls.size != 1) throw PatchException("Expected exactly one native comment clipboard call")
        return calls.map { call ->
            if (call.value.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) throw PatchException("Changed clipboard invocation")
            val copied = call.value.argumentRegisters()[1]
            val assignment = insns.take(call.index).withIndex().lastOrNull { (_, i) -> i.opcode == Opcode.MOVE_RESULT_OBJECT &&
                (i as OneRegisterInstruction).registerA == copied }?.index ?: throw PatchException("No copied String result")
            val producer = ref(insns.getOrNull(assignment - 1)) ?: throw PatchException("Missing copied String producer")
            val append = insns.getOrNull(assignment - 2) ?: throw PatchException("Missing comment append")
            val textCall = insns.getOrNull(assignment - 4)
            if (producer.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/StringBuilder;") || producer.returnType != params[0] ||
                insns[assignment - 1].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
                ref(append)?.toString() != APPEND || append.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
                ref(textCall)?.toString() != "$COMMENT->getText()Ljava/lang/String;" || textCall!!.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE))
                throw PatchException("Changed typed comment/StringBuilder result flow")
            val text = method.resultRegister(assignment - 4, params[0])
            val args = append.argumentRegisters()
            if (args.size != 2 || args[1] != text || insns[assignment - 1].argumentRegisters() != listOf(args[0]) || copied == text ||
                (assignment - 1..assignment + 1).any { offsets[it] in entries } ||
                insns.subList(assignment + 1, call.index).any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                    (it.registerA == copied || it.opcode.setsWideRegister() && it.registerA + 1 == copied) })
                throw PatchException("Changed comment/copy liveness or an incoming branch bypasses the typed builder")
            Site(assignment + 1, copied, text, if (maxOf(copied, text) <= 15) emptyList()
                else ScratchContracts.locals(method, assignment + 1, setOf(copied, text), 2))
        }
    }
    fun explicitTarget(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): String? = target(method, owner)?.takeIf {
        runCatching { sites(method, helperFor(method, resolve)) }.isSuccess
    }
    fun mode(method: Method, owner: ClassDef, expected: String, resolve: (String) -> ClassDef?): String? = MODE.takeIf {
        expected in accepted.values && explicitTarget(method, owner, resolve) == expected
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        sites(method, helperFor(method, resolve)).any { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
