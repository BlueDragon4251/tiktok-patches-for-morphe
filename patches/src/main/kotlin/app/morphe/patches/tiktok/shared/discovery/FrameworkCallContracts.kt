package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Framework descriptors and actual producer/result registers are the boundary.
 * No scratch register, caller field, or native branch is changed by these edits.
 */
internal object FrameworkCallContracts {
    const val MODE = "experimental-framework-call-sites"
    private const val TELEPHONY = "Landroid/telephony/TelephonyManager;"
    private const val EXTENSION = "Lapp/morphe/extension/tiktok/spoof/sim/SpoofSimPatch;"
    private val simGetters = mapOf(
        "getSimCountryIso" to "getCountryIso", "getNetworkCountryIso" to "getCountryIso",
        "getSimOperator" to "getOperator", "getNetworkOperator" to "getOperator",
        "getSimOperatorName" to "getOperatorName", "getNetworkOperatorName" to "getOperatorName",
    )
    private val capture = setOf(
        "Landroid/app/Activity;->registerScreenCaptureCallback(Ljava/util/concurrent/Executor;Landroid/app/Activity${'$'}ScreenCaptureCallback;)V",
        "Landroid/app/Activity;->unregisterScreenCaptureCallback(Landroid/app/Activity${'$'}ScreenCaptureCallback;)V",
    )
    data class SimSite(val index: Int, val register: Int, val replacement: String) {
        val code get() = "invoke-static/range {v$register .. v$register}, $EXTENSION->$replacement(Ljava/lang/String;)Ljava/lang/String;\nmove-result-object v$register"
    }
    private fun virtual(method: Method, i: Instruction): MethodReference? {
        if (i.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)) return null
        val ref = (i as? ReferenceInstruction)?.reference as? MethodReference ?: return null
        val expectedWords = 1 + ref.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
        if (i.argumentRegisters().size != expectedWords || i.argumentRegisters().any {
                it !in 0 until method.implementation!!.registerCount }) return null
        return ref
    }
    fun simSites(method: Method): List<SimSite> = method.implementation?.instructions?.withIndex()?.mapNotNull { (index, instruction) ->
        val ref = virtual(method, instruction) ?: return@mapNotNull null
        val replacement = simGetters[ref.name] ?: return@mapNotNull null
        if (ref.definingClass != TELEPHONY || ref.parameterTypes.isNotEmpty() || ref.returnType != "Ljava/lang/String;")
            return@mapNotNull null
        // An unused result has no value to replace. A used result must be object typed.
        val register = runCatching { method.resultRegister(index, "Ljava/lang/String;") }.getOrNull()
            ?: return@mapNotNull null
        SimSite(index + 2, register, replacement)
    }?.toList().orEmpty()
    fun captureSites(method: Method): List<Int> = method.implementation?.instructions?.withIndex()?.mapNotNull { (index, instruction) ->
        index.takeIf { virtual(method, instruction)?.toString() in capture }
    }?.toList().orEmpty()
    fun boundary(method: Method): Boolean = simSites(method).isNotEmpty() || captureSites(method).isNotEmpty()
    fun mutationAllowed(method: Method, index: Int?, code: String?, operation: String): Boolean {
        if (operation == "replace") return code?.trim() == "nop" && index in captureSites(method)
        if (operation != "insert") return false
        fun compact(value: String?) = value?.filterNot(Char::isWhitespace)
        return simSites(method).any { it.index == index && compact(code) == compact(it.code) }
    }
}
