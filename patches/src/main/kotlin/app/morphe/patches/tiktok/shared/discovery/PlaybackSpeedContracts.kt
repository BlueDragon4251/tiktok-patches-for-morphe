package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object PlaybackSpeedContracts {
    const val PANEL = "Lcom/ss/android/ugc/aweme/feed/panel/BaseListFragmentPanel;"
    const val ACCEPTED = "$PANEL->onFeedSpeedSelectedEvent(LX/07Wm;)V"
    const val MODE = "experimental-speed-selected-float"
    private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
    private const val EXTENSION = "Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;"
    data class Site(val index: Int, val register: Int, val setter: MethodReference) {
        val code get() = "invoke-static/range {v$register .. v$register}, $EXTENSION->rememberPlaybackSpeed(F)V"
    }
    private fun ref(i: Instruction?) = ((i as? ReferenceInstruction)?.reference as? MethodReference)
    fun setterBoundary(method: Method, owner: ClassDef): Boolean = runCatching {
        if (method.definingClass != PlayerFrameContracts.PLAYER || method.accessFlags != 17 || method.returnType != "V" ||
            method.parameterTypes.map(CharSequence::toString) != listOf("F") ||
            owner.superclass != "Lcom/ss/android/ugc/aweme/feed/controller/BaseController;") return false
        val insns = method.implementation!!.instructions.toList()
        val markers = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        if (!markers.containsAll(listOf("stage", "speed_begin", "action", "click", "begin_speed"))) return false
        val speed = method.parameterRegister(0)
        val receiver = ReceiverAliases.atEveryInstruction(method)
        val input = ReceiverAliases.atEveryInstruction(method, speed)
        val boxed = insns.withIndex().filter { (_, i) -> ref(i)?.toString() == "Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;" }
            .singleOrThrow("Unique native speed boxing")
        if (boxed.value.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
            boxed.value.argumentRegisters().singleOrNull() !in input[boxed.index].orEmpty()) return false
        method.resultRegister(boxed.index, "Ljava/lang/Float;")
        val apply = insns.withIndex().filter { (_, i) ->
            i.opcode in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) && ref(i)?.let {
                it.parameterTypes.map(CharSequence::toString) == listOf("F") && it.returnType == "V"
            } == true
        }.singleOrThrow("Unique native player-manager speed application")
        val manager = ref(insns.getOrNull(apply.index - 2)) ?: return false
        val managerCall = insns[apply.index - 2]
        val managerRegister = method.resultRegister(apply.index - 2, manager.returnType)
        manager.definingClass == PlayerFrameContracts.PLAYER && manager.name == "getPlayerManager" && manager.parameterTypes.isEmpty() &&
            manager.returnType == ref(apply.value)!!.definingClass && managerCall.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
            managerCall.argumentRegisters().singleOrNull() in receiver[apply.index - 2].orEmpty() &&
            apply.value.argumentRegisters().let { it.size == 2 && it[0] == managerRegister && it[1] in input[apply.index].orEmpty() }
    }.getOrDefault(false)
    fun site(method: Method, resolve: (String) -> ClassDef?): Site {
        val event = method.parameterTypes.singleOrNull()?.toString()
        if (method.definingClass != PANEL || method.name != "onFeedSpeedSelectedEvent" || method.accessFlags != 1 ||
            method.returnType != "V" || event?.startsWith("LX/") != true) throw PatchException("Changed speed event callback")
        val insns = method.implementation!!.instructions.toList()
        val setterCall = insns.withIndex().filter { (_, i) ->
            i.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) && ref(i)?.let {
                it.definingClass == PlayerFrameContracts.PLAYER && it.parameterTypes.map(CharSequence::toString) == listOf("F") && it.returnType == "V"
            } == true
        }.singleOrThrow("Unique actual player speed setter")
        val setter = ref(setterCall.value)!!
        val player = resolve(setter.definingClass) ?: throw PatchException("Missing actual player class")
        val nativeSetter = player.methods.filter { it.toString() == setter.toString() }.singleOrThrow("Unique native speed setter declaration")
        if (!setterBoundary(nativeSetter, player)) throw PatchException("Changed native speed setter body")
        val index = setterCall.index - 1
        val multiply = insns.getOrNull(index) as? TwoRegisterInstruction ?: throw PatchException("Missing speed multiplier")
        val args = setterCall.value.argumentRegisters()
        if (multiply.opcode != Opcode.MUL_FLOAT_2ADDR || args.size != 2 || args[1] != multiply.registerA)
            throw PatchException("Changed native speed multiplier consumption")
        val aliases = ReceiverAliases.atEveryInstruction(method)
        val controller = insns.take(index).withIndex().filter { (_, i) ->
            i.opcode == Opcode.IGET_OBJECT && (i as TwoRegisterInstruction).registerA == args[0] &&
                ((i as ReferenceInstruction).reference as FieldReference).let { it.definingClass == PANEL &&
                    it.type == "Lcom/ss/android/ugc/aweme/feed/controller/I18nPlayerController;" }
        }.singleOrThrow("Unique original panel player receiver")
        if ((controller.value as TwoRegisterInstruction).registerB !in aliases[controller.index].orEmpty() ||
            insns.subList(controller.index + 1, setterCall.index).any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                (it.registerA == args[0] || it.opcode.setsWideRegister() && it.registerA + 1 == args[0]) })
            throw PatchException("Changed panel player receiver liveness")
        val selected = ref(insns.getOrNull(index - 6))
        val multiplier = ref(insns.getOrNull(index - 2))
        if (multiply.registerA == multiply.registerB ||
            ref(insns.getOrNull(index - 9))?.toString() != "$PANEL->LJII()$AWEME" ||
            insns[index - 9].argumentRegisters().singleOrNull() !in aliases[index - 9].orEmpty() ||
            insns[index - 6].argumentRegisters().singleOrNull() != method.resultRegister(index - 9, AWEME) ||
            selected?.parameterTypes?.map(CharSequence::toString) != listOf(AWEME) || selected.returnType != "F" ||
            insns[index - 6].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
            method.resultRegister(index - 6, "F") != multiply.registerA ||
            ref(insns.getOrNull(index - 4))?.toString() != "$PANEL->LJII()$AWEME" ||
            insns[index - 4].argumentRegisters().singleOrNull() !in aliases[index - 4].orEmpty() ||
            multiplier?.parameterTypes?.map(CharSequence::toString) != listOf(AWEME) || multiplier.returnType != "F" ||
            insns[index - 2].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
            insns[index - 2].argumentRegisters().singleOrNull() != method.resultRegister(index - 4, AWEME) ||
            method.resultRegister(index - 2, "F") != multiply.registerB)
            throw PatchException("Changed selected-speed/native-content-multiplier flow")
        if (insns.subList(index - 4, index).any { it.opcode.setsRegister() && it is OneRegisterInstruction &&
                (it.registerA == multiply.registerA || it.opcode.setsWideRegister() && it.registerA + 1 == multiply.registerA) })
            throw PatchException("Selected speed was overwritten before its native multiplier")
        return Site(index, multiply.registerA, setter)
    }
    fun setterFromPanel(resolve: (String) -> ClassDef?): MethodReference {
        val panel = resolve(PANEL) ?: throw PatchException("Missing speed callback panel")
        return panel.methods.filter { it.name == "onFeedSpeedSelectedEvent" }.singleOrThrow("Unique speed event callback").let { site(it, resolve).setter }
    }
    fun frameSite(method: Method, resolve: (String) -> ClassDef?): PlayerFrameContracts.Site {
        val setter = setterFromPanel(resolve)
        val temps = ScratchContracts.locals(method, 0, emptySet(), 2)
        val speed = temps[0]; val receiver = temps[1]
        return PlayerFrameContracts.Site(0, "invoke-static {}, $EXTENSION->getPlaybackSpeed()F\nmove-result v$speed\n" +
            "move-object/from16 v$receiver, p0\ninvoke-virtual {v$receiver, v$speed}, $setter")
    }
    fun mode(method: Method, accepted: String, resolve: (String) -> ClassDef?): String? =
        MODE.takeIf { accepted == ACCEPTED && runCatching { site(method, resolve) }.isSuccess }
    fun mutationAllowed(method: Method, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        site(method, resolve).let { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
