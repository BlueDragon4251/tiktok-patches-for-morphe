package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object PublishDateContracts {
    const val MODE = "experimental-publish-date-visibility-results"
    private const val OWNER = "Lcom/ss/android/ugc/aweme/feed/assem/videoauthorinfo/VideoAuthorInfoVM;"
    const val ACCEPTED = "$OWNER->paramSync2StateAccept(LX/06Ty;Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;)LX/06Ty;"
    data class Gate(val index: Int, val register: Int) {
        val code get() = "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/publishdate/AlwaysShowPublishDatePatch;->showPostTimeForMainFeeds(Z)Z\nmove-result v$register"
    }
    fun gates(method: Method): List<Gate> {
        if (method.definingClass != OWNER || method.name != "paramSync2StateAccept" || method.accessFlags != 17 ||
            method.parameterTypes.size != 2 || method.parameterTypes[0] != method.returnType || !method.returnType.startsWith("LX/") ||
            method.parameterTypes[1] != "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;")
            throw PatchException("Changed post-time state boundary")
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("No post-time body")
        val start = insns.withIndex().filter { (_, i) ->
            ((i as? ReferenceInstruction)?.reference as? StringReference)?.string == "v3"
        }.singleOrThrow("Unique post-time v3 marker").index
        val end = insns.withIndex().filter { (index, i) ->
            index > start && i.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                (i as? ReferenceInstruction)?.reference?.toString() == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;->getCreateTime()J" &&
                insns.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_WIDE &&
                insns.drop(index + 2).take(4).any { it.opcode == Opcode.CMP_LONG }
        }.singleOrThrow("Unique post-time timestamp comparison").index
        val gates = insns.withIndex().filter { (index, i) ->
            val ref = (i as? ReferenceInstruction)?.reference as? MethodReference
            index in start..end && i.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                ref?.parameterTypes?.map(CharSequence::toString) == listOf("Ljava/lang/String;") &&
                ref.returnType == "Z" && i.argumentRegisters().size == 1
        }.map { (index, _) -> Gate(index + 2, method.resultRegister(index, "Z")) }
        if (gates.size != 5) throw PatchException("Expected five typed post-time gates, got ${gates.size}")
        return gates
    }
    fun mode(method: Method, owner: ClassDef, accepted: String): String? = MODE.takeIf {
        accepted == ACCEPTED && owner.type == OWNER &&
            owner.superclass == "Lcom/ss/android/ugc/aweme/feed/assem/base/FeedBaseViewModel;" &&
            runCatching { gates(method) }.isSuccess
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        fun compact(value: String?) = value?.filterNot(Char::isWhitespace)
        gates(method).any { it.index == index && compact(it.code) == compact(code) }
    }.getOrDefault(false)
}
