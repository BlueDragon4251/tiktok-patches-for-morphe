package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object TranslationContracts {
    private const val CELL = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;"
    private const val COMMENT = "Lcom/ss/android/ugc/aweme/comment/model/Comment;"
    private const val RESPONSE = "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;"
    private const val EXTENSION = "Lapp/morphe/extension/tiktok/translation/CommentBatchTranslator;"
    const val BIND = "$CELL->e7(LX/0FhG;)V"
    const val LOADED = "LX/0FhJ;->LJJI($RESPONSE" + "ZLjava/lang/String;Ljava/lang/String;LX/0HX7;Ljava/lang/String;Ljava/lang/String;LX/03Ym;II)V"
    const val COMPLETE = "LX/12dG;->run()V"
    const val BIND_MODE = "experimental-translation-initialized-observer"
    const val LOADED_MODE = "experimental-translation-native-response"
    const val COMPLETE_MODE = "experimental-translation-audio-completion"
    val modes = setOf(BIND_MODE, LOADED_MODE, COMPLETE_MODE)
    data class Site(val index: Int, val code: String)
    private fun ref(i: Instruction?) = ((i as? ReferenceInstruction)?.reference as? MethodReference)
    fun bindSite(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Site {
        if (method.definingClass != CELL || method.accessFlags != 1 || method.returnType != "V" ||
            method.parameterTypes.size != 1 || !method.parameterTypes.single().toString().startsWith("LX/") ||
            owner.superclass != "Lcom/bytedance/ies/powerlist/PowerCell;") throw PatchException("Changed native comment-cell role")
        var ancestor: String? = owner.superclass
        val visited = hashSetOf<String>()
        while (ancestor != "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;" && ancestor != null && visited.add(ancestor))
            ancestor = resolve(ancestor)?.superclass
        if (ancestor != "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;") throw PatchException("Comment cell is not a real ViewHolder")
        val insns = method.implementation!!.instructions.toList()
        if (insns.none { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "comment_panel" })
            throw PatchException("Missing native comment panel role")
        val receiverAliases = ReceiverAliases.atEveryInstruction(method)
        val inputAliases = ReceiverAliases.atEveryInstruction(method, method.parameterRegister(0))
        val superCall = insns.withIndex().filter { (_, i) -> i.opcode in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE) &&
            ref(i)?.let { it.definingClass == owner.superclass && it.name == "onBindItemView" && it.parameterTypes.size == 1 && it.returnType == "V" } == true
        }.singleOrThrow("Unique native PowerCell binding")
        val superArgs = superCall.value.argumentRegisters()
        if (superArgs.size != 2 || superArgs[0] !in receiverAliases[superCall.index].orEmpty() || superArgs[1] !in inputAliases[superCall.index].orEmpty())
            throw PatchException("Changed PowerCell binding receiver/input")
        val matches = insns.withIndex().mapNotNull { (index, i) ->
            val field = ((i as? ReferenceInstruction)?.reference as? FieldReference) ?: return@mapNotNull null
            if (i.opcode != Opcode.IPUT_OBJECT || field.type != COMMENT || "Landroidx/lifecycle/Observer;" !in resolve(field.definingClass)?.interfaces.orEmpty()) return@mapNotNull null
            val manager = (i as TwoRegisterInstruction).registerB
            val writes = ((index + 1)..minOf(index + 6, insns.lastIndex)).filter { n ->
                val next = insns[n]; val nextField = ((next as? ReferenceInstruction)?.reference as? FieldReference)
                next.opcode == Opcode.IPUT_OBJECT && (next as TwoRegisterInstruction).registerB == manager && nextField?.definingClass == field.definingClass
            }
            if (writes.size < 2) return@mapNotNull null
            val ready = writes.last() + 1
            if (index <= superCall.index || insns.subList(index + 1, ready).any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                    (it.registerA == manager || it.opcode.setsWideRegister() && it.registerA + 1 == manager) })
                throw PatchException("Changed initialized translation observer liveness")
            ready to manager
        }.distinct().singleOrThrow("Unique initialized native translation observer")
        val (index, manager) = matches
        val receiver = receiverAliases[index].orEmpty().minOrNull() ?: throw PatchException("No preserved comment-cell receiver")
        val temps = ScratchContracts.locals(method, index, setOf(manager, receiver), if (manager > 15) 2 else 1)
        val view = temps[0]; val passedManager = if (manager > 15) temps[1] else manager
        return Site(index, "move-object/from16 v$view, v$receiver\n" +
            "iget-object v$view, v$view, Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;->itemView:Landroid/view/View;\n" +
            (if (manager > 15) "move-object/from16 v$passedManager, v$manager\n" else "") +
            "invoke-static {v$view, v$passedManager}, $EXTENSION->registerCommentCell(Landroid/view/View;Ljava/lang/Object;)V")
    }
    fun loadedSite(method: Method, owner: ClassDef): Site {
        val params = method.parameterTypes.map(CharSequence::toString)
        if (method.accessFlags != 17 || method.returnType != "V" || owner.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty() ||
            params.size != 10 || params[0] != RESPONSE || params[1] != "Z" || params.slice(listOf(2, 3, 5, 6)) != List(4) { "Ljava/lang/String;" } ||
            !params[4].startsWith("LX/") || !params[7].startsWith("LX/") || params.takeLast(2) != listOf("I", "I"))
            throw PatchException("Changed native comment response callback")
        val insns = method.implementation!!.instructions.toList()
        val fields = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? FieldReference)?.toString() }
        if ("$RESPONSE->items:Ljava/util/List;" !in fields || "$RESPONSE->jsonData:Ljava/lang/String;" !in fields)
            throw PatchException("Missing native split response/items relationship")
        val read = insns.withIndex().filter { (_, i) -> i.opcode == Opcode.IGET_OBJECT &&
            ((i as? ReferenceInstruction)?.reference as? FieldReference)?.let { it.definingClass == RESPONSE && it.name == "lazySplitItemsParseTask" && it.type.startsWith("LX/") } == true
        }.singleOrThrow("Unique native lazy split-task read")
        val response = (read.value as TwoRegisterInstruction).registerB
        val clear = insns.getOrNull(read.index + 1)
        if (response !in ReceiverAliases.atEveryInstruction(method, method.parameterRegister(0))[read.index].orEmpty() ||
            clear?.opcode != Opcode.IPUT_OBJECT || (clear as TwoRegisterInstruction).registerB != response ||
            (clear as ReferenceInstruction).reference.toString() != (read.value as ReferenceInstruction).reference.toString())
            throw PatchException("Changed original response alias/consumption")
        return Site(read.index, "invoke-static/range {v$response .. v$response}, $EXTENSION->onCommentListLoaded(Ljava/lang/Object;)V")
    }
    fun completeSite(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Site {
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("Missing native completion body")
        if (method.name != "run" || method.accessFlags != 17 || method.parameterTypes.isNotEmpty() || method.returnType != "V" ||
            owner.superclass != "Ljava/lang/Object;" || owner.interfaces.size != 1 || resolve(owner.interfaces.single())?.methods?.any {
                it.name == "run" && it.returnType == "V" && it.parameterTypes.isEmpty()
            } != true || owner.fields.count { it.type == "Ljava/util/List;" } != 1 ||
            insns.none { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "MultiCommentTranslationTask startTranslate onComplete " })
            throw PatchException("Changed native audio completion callback")
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
        if (!refs.containsAll(listOf("$COMMENT->setAudioTextIsTranslated(Z)V", "$COMMENT->getCid()Ljava/lang/String;",
                "Lcom/ss/android/ugc/aweme/translation/model/TranslationResult;->contentId:Ljava/lang/String;",
                "Lcom/ss/android/ugc/aweme/translation/model/TranslationResult;->translatedContent:Ljava/lang/String;",
                "Landroidx/lifecycle/LiveData;->setValue(Ljava/lang/Object;)V"))) throw PatchException("Changed audio result/comment notification flow")
        return Site(0, "invoke-static/range {p0 .. p0}, $EXTENSION->onNativeBatchComplete(Ljava/lang/Object;)V")
    }
    fun mode(method: Method, owner: ClassDef, accepted: String, resolve: (String) -> ClassDef?): String? = when (accepted) {
        BIND -> BIND_MODE.takeIf { runCatching { bindSite(method, owner, resolve) }.isSuccess }
        LOADED -> LOADED_MODE.takeIf { runCatching { loadedSite(method, owner) }.isSuccess }
        COMPLETE -> COMPLETE_MODE.takeIf { runCatching { completeSite(method, owner, resolve) }.isSuccess }
        else -> null
    }
    fun mutationAllowed(mode: String, method: Method, owner: ClassDef, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        val site = when (mode) {
            BIND_MODE -> bindSite(method, owner, resolve)
            LOADED_MODE -> loadedSite(method, owner)
            COMPLETE_MODE -> completeSite(method, owner, resolve)
            else -> return false
        }
        site.index == index && site.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace)
    }.getOrDefault(false)
}
