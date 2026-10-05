package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** The source method must call the already validated preview binder. */
internal object StickerSourceContracts {
    const val MODE = "experimental-sticker-source-association"
    private const val ITEM = "Lcom/ss/android/ugc/aweme/im/common/model/StickerItem;"
    private val parameters = listOf("Ljava/lang/String;", ITEM, "Landroid/view/View;", "Z", "Ljava/lang/String;",
        "Ljava/util/Map;", "Lkotlin/jvm/functions/Function0;", "Lkotlin/jvm/functions/Function0;", "Lkotlin/jvm/functions/Function0;")
    data class Site(val index: Int, val code: String)
    fun boundary(method: Method, binder: Method): Boolean {
        val insns = method.implementation?.instructions?.toList() ?: return false
        if (method.accessFlags != 17 || method.returnType != "V" ||
            method.parameterTypes.map(CharSequence::toString) != parameters ||
            binder.accessFlags != 17 || binder.returnType != "V" || binder.parameterTypes.size != 4) return false
        val item = method.parameterRegister(1, ITEM)
        if (insns.any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                (it.registerA == item || it.opcode.setsWideRegister() && it.registerA + 1 == item) }) return false
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }.map { it.toString() }
        return refs.containsAll(listOf(
            "Lcom/ss/android/ugc/aweme/im/common/model/SetSticker;->getSetId()Ljava/lang/Long;",
            "Lcom/ss/android/ugc/aweme/im/common/model/SetSticker;->getStaticUrl()Lcom/ss/android/ugc/aweme/im/common/model/StickerUrlStruct;")) &&
            refs.any { it == binder.toString() }
    }
    fun sites(method: Method, binder: Method): List<Site> {
        if (!boundary(method, binder)) throw PatchException("Changed sticker source/binder argument boundary")
        val item = method.parameterRegister(1, ITEM)
        return method.implementation!!.instructions.withIndex().mapNotNull { (index, i) ->
            if ((i as? ReferenceInstruction)?.reference?.toString() != binder.toString()) return@mapNotNull null
            if (i.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) || i.argumentRegisters().size != 5)
                throw PatchException("Unsupported sticker binder invoke in $method")
            val preview = i.argumentRegisters()[1]
            val (previewTemp, itemTemp) = ScratchContracts.locals(method, index, setOf(preview, item), 2)
            Site(index, """
                move-object/from16 v$previewTemp, v$preview
                move-object/from16 v$itemTemp, p2
                invoke-static {v$previewTemp, v$itemTemp}, Lapp/morphe/extension/tiktok/download/StickerGallerySaver;->registerStickerSource(Ljava/lang/Object;Ljava/lang/Object;)V
            """.trimIndent())
        }.toList()
    }
    fun mutationAllowed(method: Method, binder: Method, index: Int?, code: String?): Boolean = runCatching {
        fun compact(value: String?) = value?.filterNot(Char::isWhitespace)
        sites(method, binder).any { it.index == index && compact(it.code) == compact(code) }
    }.getOrDefault(false)
}
