package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object StartupAdContracts {
    const val OWNER = "Lcom/bytedance/ies/ugc/aweme/commercialize/splash/core/SplashAdServiceImpl;"
    const val TARGET = "$OWNER->LJJII(LX/02dq;)Z"
    const val MODE = "experimental-startup-ad-display-entry"
    private fun ref(i: Instruction) = (i as? ReferenceInstruction)?.reference
    fun boundary(m: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean = runCatching {
        if (owner.type != OWNER || owner.superclass != "Ljava/lang/Object;" ||
            "Lcom/bytedance/ies/ugc/aweme/commercialize/splash/service/ISplashAdService;" !in owner.interfaces ||
            m.accessFlags != 17 || m.returnType != "Z" || m.parameterTypes.size != 1) return false
        var type: String? = m.parameterTypes.single().toString()
        val seen = hashSetOf<String>()
        while (type != "Landroid/app/Activity;" && type != null && seen.add(type)) type = resolve(type)?.superclass
        if (type != "Landroid/app/Activity;") return false
        val body = m.implementation ?: return false
        val insns = body.instructions.toList()
        if (insns.size != 36 || body.tryBlocks.isNotEmpty() || insns[0].opcode != Opcode.CONST ||
            (insns[0] as? NarrowLiteralInstruction)?.narrowLiteral != 71911 ||
            (insns[0] as OneRegisterInstruction).registerA != 0) return false
        val entry = ref(insns[1]) as? MethodReference ?: return false
        if (entry.definingClass != "Lcom/bytedance/pumbaa/utility/method_id/MethodIDManager;" ||
            entry.parameterTypes != listOf("I") || entry.returnType != "Z") return false
        val context = m.parameterRegister(0, m.parameterTypes.single().toString())
        val displays = insns.filter { i ->
            val call = ref(i) as? MethodReference
            i.opcode == Opcode.INVOKE_VIRTUAL && call?.definingClass?.startsWith("LX/") == true &&
                call.parameterTypes == listOf("I", "Landroid/content/Context;") && call.returnType == "Z" &&
                i.argumentRegisters() == listOf(0, 1, context)
        }
        displays.size == 2 && insns.count { it.opcode == Opcode.RETURN } == 3 &&
            context in ReceiverAliases.atEveryInstruction(m, context)[31].orEmpty() &&
            0 in ScratchContracts.locals(m, 0, setOf(context), 1)
    }.getOrDefault(false)
    fun code() = """
        invoke-static {}, Lapp/morphe/extension/tiktok/feedfilter/StartupAds;->shouldBlock()Z
        move-result v0
        if-eqz v0, :blueit_native_splash
        const/4 v0, 0x0
        return v0
        :blueit_native_splash
        nop
    """.trimIndent()
    fun mutationAllowed(index: Int?, value: String?) = index == 0 && value?.filterNot(Char::isWhitespace) == code().filterNot(Char::isWhitespace)
}
