package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object TakoContracts {
    const val VISIBILITY = "experimental-tako-visibility-entry"
    const val BIND = "experimental-tako-view-created"
    val modes = setOf(VISIBILITY, BIND)
    private const val OWNER = "Lcom/ss/android/ugc/aweme/feed/assem/tikbot/TakoAssem;"
    private const val BASE = "Lcom/ss/android/ugc/feed/platform/cell/BaseCellSlotComponent;"
    private const val RUNTIME = "Lapp/morphe/extension/tiktok/feedfilter/TakoAiFilter;"
    fun mode(method: Method, owner: ClassDef, accepted: String): String? {
        val insns = method.implementation?.instructions?.toList() ?: return null
        if (owner.type != OWNER || owner.superclass != BASE || method.accessFlags != 17 || method.returnType != "V") return null
        if (accepted == "$OWNER->Ar(Z)V" && method.parameterTypes.map(CharSequence::toString) == listOf("Z") &&
            method.parameterRegister(0, "Z") < 16 &&
            insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }.containsAll(listOf(
                "Lcom/bytedance/assem/arch/reused/ReusedUIAssem;->LJJJ()Landroid/view/View;",
                "Landroid/view/View;->getVisibility()I")) &&
            runCatching { ScratchContracts.locals(method, 0, emptySet(), 1) == listOf(0) }.getOrDefault(false)) return VISIBILITY
        if (accepted == "$OWNER->onViewCreated(Landroid/view/View;)V" && method.toString() == accepted && insns.size > 2 &&
            insns[0].opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) &&
            (insns[0] as TwoRegisterInstruction).registerB == method.parameterRegister(0, "Landroid/view/View;") - 1 &&
            insns[1].opcode in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE) &&
            (insns[1] as? ReferenceInstruction)?.reference?.toString() == "$BASE->onViewCreated(Landroid/view/View;)V" &&
            insns[1].argumentRegisters() == listOf((insns[0] as TwoRegisterInstruction).registerA,
                method.parameterRegister(0, "Landroid/view/View;"))) return BIND
        return null
    }
    fun visibilityCode(): String = """
        invoke-static {}, $RUNTIME->shouldHideFeedButton()Z
        move-result v0
        if-eqz v0, :morphe_keep_feed_tako_visible_state
        const/4 p1, 0x0
        :morphe_keep_feed_tako_visible_state
        nop
    """.trimIndent()
    fun mutationAllowed(mode: String, index: Int?, code: String?): Boolean {
        fun compact(value: String?) = value?.filterNot(Char::isWhitespace)
        return when(mode) {
            VISIBILITY -> index == 0 && compact(code) == compact(visibilityCode())
            BIND -> index == 2 && compact(code) == compact("invoke-static/range {p1 .. p1}, $RUNTIME->hideBoundFeedButtonView(Landroid/view/View;)V")
            else -> false
        }
    }
}
