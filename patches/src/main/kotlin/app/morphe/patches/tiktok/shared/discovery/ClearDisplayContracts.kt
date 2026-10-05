package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object ClearDisplayContracts {
    const val PANEL = "Lcom/ss/android/ugc/feed/platform/panel/clearmode/ClearModePanelComponent;"
    const val EVENT = "$PANEL->onClearModeEvent(LX/0AuQ;)V"
    const val RESET = "$PANEL->vq(LX/06SG;Z)V"
    const val STATE = "LX/0De5;->LJIIJJI(Lcom/bytedance/common/utility/collection/WeakHandler;ZLjava/lang/String;Lcom/ss/android/ugc/aweme/feed/model/Aweme;JII)V"
    const val EVENT_MODE = "experimental-clear-display-boolean-read"
    const val RESET_MODE = "experimental-clear-display-final-reset"
    const val STATE_MODE = "experimental-clear-display-log-state"
    val modes = setOf(EVENT_MODE, RESET_MODE, STATE_MODE)
    const val AUTOMATIC = "Lapp/morphe/extension/tiktok/cleardisplay/AutomaticClearDisplayController;"
    const val REMEMBER = "Lapp/morphe/extension/tiktok/cleardisplay/RememberClearDisplayPatch;"
    private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
    data class Site(val index: Int, val code: String)
    private fun panel(method: Method, owner: ClassDef) {
        if (method.definingClass != PANEL || owner.superclass != "Lcom/ss/android/ugc/feed/platform/panel/BasePanelComponent;" ||
            "Lcom/ss/android/ugc/feed/platform/panel/clearmode/IClearModePanelComponent;" !in owner.interfaces ||
            method.accessFlags != 17 || method.returnType != "V") throw PatchException("Changed clear-mode panel role")
    }
    fun eventSite(method: Method, owner: ClassDef): Site {
        panel(method, owner)
        val event = method.parameterTypes.singleOrNull()?.toString() ?: throw PatchException("Missing clear event parameter")
        if (method.name != "onClearModeEvent" || !event.startsWith("LX/")) throw PatchException("Changed clear event signature")
        val insns = method.implementation!!.instructions.toList()
        val markers = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        if (!markers.containsAll(listOf("onClearModeEvent type: ", " isClean: ", " enterMethod: ")))
            throw PatchException("Missing native clear event log relationship")
        val firstRead = insns.indexOfFirst { it.opcode == Opcode.IGET_BOOLEAN }
        if (firstRead < 0) throw PatchException("Missing native clear-state read")
        val read = insns[firstRead] as TwoRegisterInstruction
        val field = (insns[firstRead] as ReferenceInstruction).reference as FieldReference
        val consume = insns.getOrNull(firstRead + 1)
        val aliases = ReceiverAliases.atEveryInstruction(method, method.parameterRegister(0))
        if (field.definingClass != event || field.type != "Z" || read.registerB !in aliases[firstRead].orEmpty() ||
            consume?.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
            ((consume as? ReferenceInstruction)?.reference as? MethodReference)?.toString() !=
                "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;" ||
            consume!!.argumentRegisters().getOrNull(1) != read.registerA)
            throw PatchException("Clear state is not the original event's typed boolean")
        return Site(firstRead + 1, "invoke-static/range {v${read.registerA} .. v${read.registerA}}, $REMEMBER->rememberClearDisplayState(Z)V")
    }
    fun eventClass(owner: ClassDef): String {
        val method = owner.methods.filter { runCatching { eventSite(it, owner) }.isSuccess }.singleOrThrow("Unique native clear event")
        return method.parameterTypes.single().toString().removePrefix("L").removeSuffix(";").replace('/', '.')
    }
    fun resetSite(method: Method, owner: ClassDef): Site {
        panel(method, owner)
        val params = method.parameterTypes.map(CharSequence::toString)
        if (params.size != 2 || !params[0].startsWith("LX/") || params[1] != "Z") throw PatchException("Changed panel reset signature")
        val insns = method.implementation!!.instructions.toList()
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
        val event = owner.methods.filter { runCatching { eventSite(it, owner) }.isSuccess }.singleOrThrow("Unique native clear event").parameterTypes.single().toString()
        if ("resetClearMode" !in refs || "$event-><init>(ZILjava/lang/String;Ljava/lang/String;)V" !in refs ||
            "${params[0]}->getAweme()$AWEME" !in refs ||
            insns.none { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { r ->
                r.parameterTypes.map(CharSequence::toString) == listOf("Lcom/ss/android/ugc/governance/eventbus/IEvent;") &&
                    r.returnType == "Lcom/ss/android/ugc/governance/eventbus/IEvent;"
            } == true }) throw PatchException("Changed native reset/event dispatch relationship")
        val index = insns.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        if (index < 0) throw PatchException("No final clear reset return")
        val temp = ScratchContracts.locals(method, index, emptySet(), 1).single()
        return Site(index, "const-string v$temp, \"${eventClass(owner)}\"\ninvoke-static/range {v$temp .. v$temp}, $AUTOMATIC->onPanelReset(Ljava/lang/String;)V")
    }
    fun logStateBoundary(method: Method, owner: ClassDef): Boolean = runCatching {
        val params = listOf("Lcom/bytedance/common/utility/collection/WeakHandler;", "Z", "Ljava/lang/String;", AWEME, "J", "I", "I")
        if (method.accessFlags != 25 || method.returnType != "V" || owner.superclass != "Ljava/lang/Object;" ||
            owner.interfaces.isNotEmpty() || method.parameterTypes.map(CharSequence::toString) != params) return false
        val insns = method.implementation!!.instructions.toList()
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
        val ownFields = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? FieldReference) }
            .filter { it.definingClass == method.definingClass }.map { it.type }.toSet()
        refs.containsAll(listOf("Ljava/lang/System;->currentTimeMillis()J", "Landroid/os/Message;->obtain(Landroid/os/Handler;Ljava/lang/Runnable;)Landroid/os/Message;",
            "Landroid/os/Message;->what:I", "Landroid/os/Handler;->hasMessages(I)Z", "Landroid/os/Handler;->removeMessages(I)V",
            "Landroid/os/Looper;->myQueue()Landroid/os/MessageQueue;")) && ownFields == setOf(AWEME, "Z", "F", "I", "J") &&
            insns.filterIsInstance<WideLiteralInstruction>().map { it.wideLiteral }.containsAll(listOf(101L, 1000L)) &&
            insns.count { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { r ->
                r.name == "<init>" && r.returnType == "V" && r.parameterTypes.map(CharSequence::toString) ==
                    listOf("Z", AWEME, "Ljava/lang/String;", "J", "F", "I", "J", "F", "I", "J")
            } == true } == 1
    }.getOrDefault(false)
    fun mode(method: Method, owner: ClassDef, accepted: String): String? = when (accepted) {
        EVENT -> EVENT_MODE.takeIf { runCatching { eventSite(method, owner) }.isSuccess }
        RESET -> RESET_MODE.takeIf { runCatching { resetSite(method, owner) }.isSuccess }
        STATE -> STATE_MODE.takeIf { logStateBoundary(method, owner) }
        else -> null
    }
    fun mutationAllowed(mode: String, method: Method, owner: ClassDef, index: Int?, code: String?, operation: String): Boolean = runCatching {
        if (mode == STATE_MODE) operation == "return-early" && index == 0 && code == "return-void" && logStateBoundary(method, owner)
        else if (operation != "insert") false
        else (if (mode == EVENT_MODE) eventSite(method, owner) else resetSite(method, owner)).let {
            it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace)
        }
    }.getOrDefault(false)
}
