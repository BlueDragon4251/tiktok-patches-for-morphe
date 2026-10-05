package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object PlayerFrameContracts {
    const val PLAYER = "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;"
    const val ACCEPTED = "$PLAYER->onRenderFirstFrame(LX/09y1;)V"
    const val MODE = "experimental-player-first-frame-dispatch"
    data class Site(val index: Int, val code: String)
    fun boundary(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean = runCatching {
        val param = method.parameterTypes.singleOrNull()?.toString() ?: return false
        if (method.definingClass != PLAYER || method.name != "onRenderFirstFrame" || method.accessFlags != 17 || method.returnType != "V" ||
            !param.startsWith("LX/") || owner.superclass != "Lcom/ss/android/ugc/aweme/feed/controller/BaseController;" ||
            "Lcom/ss/android/ugc/aweme/player/sdk/api/OnUIPlayListener;" !in owner.interfaces) return false
        val insns = method.implementation!!.instructions.toList()
        val ctorIndex = insns.withIndex().filter { (_, i) ->
            i.opcode in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) &&
                ((i as? ReferenceInstruction)?.reference as? MethodReference)?.let { r ->
                    r.name == "<init>" && r.parameterTypes.map(CharSequence::toString) == listOf(PLAYER, param) && r.returnType == "V"
                } == true
        }.singleOrThrow("Unique player first-frame runnable").index
        val ctor = (insns[ctorIndex] as ReferenceInstruction).reference as MethodReference
        val args = insns[ctorIndex].argumentRegisters()
        val aliases = ReceiverAliases.atEveryInstruction(method)
        val inputAliases = ReceiverAliases.atEveryInstruction(method, method.parameterRegister(0))
        if (args.size != 3 || args[1] !in aliases[ctorIndex].orEmpty() || args[2] !in inputAliases[ctorIndex].orEmpty() ||
            "Ljava/lang/Runnable;" !in resolve(ctor.definingClass)?.interfaces.orEmpty()) return false
        val runnable = args[0]
        val allocation = insns.getOrNull(ctorIndex - 1)
        if (allocation?.opcode != Opcode.NEW_INSTANCE || (allocation as OneRegisterInstruction).registerA != runnable ||
            ((allocation as? ReferenceInstruction)?.reference as? TypeReference)?.type != ctor.definingClass ||
            insns.drop(ctorIndex + 1).any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                (it.registerA == runnable || it.opcode.setsWideRegister() && it.registerA + 1 == runnable) }) return false
        insns.any { i -> ((i as? ReferenceInstruction)?.reference as? MethodReference)?.let { r ->
            r.definingClass == ctor.definingClass && r.name == "run" && r.parameterTypes.isEmpty() && r.returnType == "V" &&
                i.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) && i.argumentRegisters() == listOf(runnable)
        } == true } && insns.any { i -> ((i as? ReferenceInstruction)?.reference as? MethodReference)?.let { r ->
            i.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) && r.returnType == "V" &&
                r.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/Choreographer;", "Ljava/lang/Runnable;") &&
                i.argumentRegisters().getOrNull(1) == runnable
        } == true }
    }.getOrDefault(false)
    fun restoreSite(method: Method, panel: ClassDef): Site {
        val index = method.implementation!!.instructions.toList().indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (index < 0) throw PatchException("No player first-frame return")
        val temp = ScratchContracts.locals(method, index, emptySet(), 1).single()
        return Site(index, "const-string v$temp, \"${ClearDisplayContracts.eventClass(panel)}\"\n" +
            "invoke-static/range {v$temp .. v$temp}, ${ClearDisplayContracts.AUTOMATIC}->onRenderFirstFrame(Ljava/lang/String;)V\n" +
            "invoke-static/range {v$temp .. v$temp}, ${ClearDisplayContracts.REMEMBER}->restoreClearDisplayState(Ljava/lang/String;)V")
    }
    fun mode(method: Method, owner: ClassDef, accepted: String, resolve: (String) -> ClassDef?): String? =
        MODE.takeIf { accepted == ACCEPTED && boundary(method, owner, resolve) }
    fun mutationAllowed(method: Method, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        val enable = "invoke-static {}, ${ClearDisplayContracts.AUTOMATIC}->enablePatch()V"
        val site = restoreSite(method, resolve(ClearDisplayContracts.PANEL) ?: return false)
        (index == 0 && code?.filterNot(Char::isWhitespace) == enable.filterNot(Char::isWhitespace)) ||
            (index == site.index && code?.filterNot(Char::isWhitespace) == site.code.filterNot(Char::isWhitespace))
    }.getOrDefault(false)
}
