package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

/** The unique native description TextView getter, reviewed on both SHA-pinned APKs. */
internal object FeedDescriptionContracts {
    const val OWNER = "Lcom/ss/android/ugc/aweme/feed/assem/desc/VideoDescAssem;"
    const val TEXT = "Lcom/bytedance/tux/input/TuxTextView;"
    const val HOOK = "app.morphe.patches.tiktok.layout.theme.FeedDescriptionGetterFingerprint:Lcom/ss/android/ugc/aweme/feed/assem/desc/VideoDescAssem;:"
    const val MODE = "experimental-feed-description-getter"
    fun boundary(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean = runCatching {
        if (owner.type != OWNER || method.accessFlags != 17 || method.parameterTypes.isNotEmpty() || method.returnType != TEXT) return false
        var type: String? = TEXT
        val seen = hashSetOf<String>()
        while (type != "Landroid/widget/TextView;") {
            if (type == null || !seen.add(type)) return false
            type = resolve(type)?.superclass
        }
        val body = method.implementation ?: return false
        if (body.registerCount != 2 || body.tryBlocks.isNotEmpty()) return false
        val ins = body.instructions.toList()
        if (ins.map { it.opcode } != listOf(Opcode.IGET_OBJECT, Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT_OBJECT,
                Opcode.CHECK_CAST, Opcode.RETURN_OBJECT)) return false
        val get = ins[0] as TwoRegisterInstruction
        val field = (ins[0] as ReferenceInstruction).reference as FieldReference
        val call = (ins[1] as ReferenceInstruction).reference as MethodReference
        get.registerA == 0 && get.registerB == 1 && field.definingClass == OWNER &&
            field.type == call.definingClass && call.name == "getValue" && call.parameterTypes.isEmpty() &&
            call.returnType == "Ljava/lang/Object;" && ins[1].argumentRegisters() == listOf(0) &&
            ins.drop(2).all { (it as OneRegisterInstruction).registerA == 0 } &&
            ((ins[3] as ReferenceInstruction).reference as TypeReference).type == TEXT
    }.getOrDefault(false)

    fun site(method: Method): ThemeContracts.Site {
        val body = method.implementation!!.instructions.toList()
        require(body.last().opcode == Opcode.RETURN_OBJECT)
        val output = (body.last() as OneRegisterInstruction).registerA
        return ThemeContracts.Site(body.lastIndex,
            "invoke-static/range {v$output .. v$output}, Lapp/morphe/extension/tiktok/theme/ThemeNativeTargets;->feedDescription(Landroid/widget/TextView;)V")
    }
    fun mode(method: Method, owner: ClassDef, accepted: String, contracts: FixtureContracts.Reviewed, resolve: (String) -> ClassDef?): String? = MODE.takeIf {
        accepted == contracts.hookMethods[HOOK] && boundary(method, owner, resolve) &&
            FixtureContracts.portableSignature(method) == contracts.portableMethods[accepted]
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        site(method).let { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
