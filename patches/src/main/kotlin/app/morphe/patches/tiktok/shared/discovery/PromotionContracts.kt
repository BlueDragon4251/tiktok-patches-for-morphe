package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object PromotionContracts {
    const val MODE = "experimental-normal-pendant-parse-result"
    const val ACCEPTED = "LX/07TS;->LIZ(Ljava/util/HashMap;Ljava/util/List;Z)V"
    private const val MODEL = "Lcom/bytedance/touchpoint/api/model/NormalPendant;"
    data class Site(val index: Int, val register: Int) {
        val code get() = "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;->filterNormalPendant(Ljava/lang/Object;)Ljava/lang/Object;\nmove-result-object v$register"
    }
    fun site(method: Method): Site {
        if (method.accessFlags != 17 || method.returnType != "V" || method.parameterTypes.map(CharSequence::toString) !=
            listOf("Ljava/util/HashMap;", "Ljava/util/List;", "Z")) throw PatchException("Changed pendant parser shape")
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("No pendant parser body")
        if (insns.none { ((it as? ReferenceInstruction)?.reference as? TypeReference)?.type == "Lcom/bytedance/touchpoint/data/parser/notify/PendantViewModel;" } ||
            insns.none { ((it as? ReferenceInstruction)?.reference as? FieldReference)?.toString() == "Lcom/bytedance/touchpoint/api/model/TouchPoint;->data:Ljava/lang/String;" })
            throw PatchException("Missing native touchpoint/pendant view-model relationship")
        return insns.withIndex().mapNotNull { (index, i) ->
            val ref = ((i as? ReferenceInstruction)?.reference as? MethodReference) ?: return@mapNotNull null
            if (i.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
                ref.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/String;", "Ljava/lang/Class;") || ref.returnType != "Ljava/lang/Object;") return@mapNotNull null
            val klass = insns.getOrNull(index - 1) ?: return@mapNotNull null
            if (klass.opcode != Opcode.CONST_CLASS || ((klass as? ReferenceInstruction)?.reference as? TypeReference)?.type != MODEL ||
                i.argumentRegisters().size != 2 || i.argumentRegisters()[1] != (klass as OneRegisterInstruction).registerA) return@mapNotNull null
            val register = method.resultRegister(index, "Ljava/lang/Object;")
            val next = insns.getOrNull(index + 2)
            val moved = next?.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) &&
                (next as TwoRegisterInstruction).registerB == register
            val cast = if (moved) insns.getOrNull(index + 3) else next
            val castRegister = if (moved) (next as TwoRegisterInstruction).registerA else register
            if (cast?.opcode != Opcode.CHECK_CAST || (cast as OneRegisterInstruction).registerA != castRegister ||
                ((cast as? ReferenceInstruction)?.reference as? TypeReference)?.type != MODEL)
                throw PatchException("Missing typed NormalPendant result consumption")
            Site(index + 2, register)
        }.singleOrThrow("Unique actual NormalPendant class parse")
    }
    fun mode(method: Method, owner: ClassDef, accepted: String): String? = MODE.takeIf {
        accepted == ACCEPTED && owner.superclass == "Ljava/lang/Object;" && runCatching { site(method) }.isSuccess
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        site(method).let { it.index == index && code?.filterNot(Char::isWhitespace) == it.code.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
