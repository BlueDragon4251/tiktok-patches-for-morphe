package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object SettingsCategoryContracts {
    const val ROWS = "experimental-settings-category-list"
    const val HEADER = "experimental-settings-category-title"
    const val HEADER_TARGET = "LX/1C4e;->LJIIIIZZ(LX/1R8u;ZZLX/008m;I)V"
    const val EXTENSION = "Lapp/morphe/extension/tiktok/settings/NativeSettingsRows;"
    val modes = setOf(ROWS, HEADER)
    private fun ref(i: Instruction) = (i as? ReferenceInstruction)?.reference
    fun sortedListCall(m: Method): Int = m.implementation?.instructions?.indexOfFirst {
        val r = ref(it) as? MethodReference
        it.opcode == Opcode.INVOKE_STATIC && r?.parameterTypes == listOf("Ljava/util/Comparator;", "Ljava/lang/Iterable;") && r.returnType == "Ljava/util/List;"
    } ?: -1
    fun rowsSite(m: Method): ThemeContracts.Site {
        require(m.definingClass == ThemeSurfaceContracts.SETTINGS && m.returnType == "V" &&
            m.parameterTypes.map(CharSequence::toString).take(9) == listOf(
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/SettingsRvmpComposeViewModel;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/banner/SettingsBannerVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/top/TopGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/account/AccountGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/privacy/VisibilityGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/privacy/InteractionsGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/cache/CacheGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/content/ContentGroupVM;",
                "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/support/SupportGroupVM;") &&
            m.parameterTypes.size == 11 && m.parameterTypes.last() == "I")
        val insns = m.implementation!!.instructions.toList()
        val at = sortedListCall(m)
        require(at >= 0 && insns[at + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
        val r = (insns[at + 1] as OneRegisterInstruction).registerA
        val remember = ref(insns[at + 2]) as? MethodReference
        require(remember?.parameterTypes == listOf("Ljava/lang/Object;") && remember.returnType == "V" &&
            insns[at + 2].argumentRegisters().last() == r)
        return ThemeContracts.Site(at + 2, "invoke-static/range {v$r .. v$r}, $EXTENSION->prepare(Ljava/util/List;)Ljava/util/List;\nmove-result-object v$r")
    }
    fun headerSite(m: Method): ThemeContracts.Site {
        require(m.accessFlags == 25 && m.returnType == "V" && m.parameterTypes.size == 5 &&
            m.parameterTypes[0].startsWith("LX/") && m.parameterTypes[1] == "Z" && m.parameterTypes[2] == "Z" && m.parameterTypes[4] == "I")
        val insns = m.implementation!!.instructions.toList()
        require(insns.any { it.opcode == Opcode.PACKED_SWITCH } && insns.any {
            ref(it)?.toString() == "Ljava/lang/Enum;->ordinal()I" })
        val at = insns.withIndex().filter { (_, i) ->
            val r = ref(i) as? MethodReference
            i.opcode == Opcode.INVOKE_STATIC && r?.returnType == "Ljava/lang/String;" &&
                r.parameterTypes == listOf("I", m.parameterTypes[3])
        }.singleOrThrow("Native Support section title resource").index
        require(insns[at + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
        val r = (insns[at + 1] as OneRegisterInstruction).registerA
        val p = m.parameterRegister(0, m.parameterTypes[0].toString())
        require(p <= 15 && r <= 15 && p in ReceiverAliases.atEveryInstruction(m, p)[at].orEmpty())
        val consumer = insns.drop(at + 2).take(7).firstOrNull { i ->
            val call = ref(i) as? MethodReference
            call?.parameterTypes?.map(CharSequence::toString) == listOf("Ljava/lang/String;", "Z", "Z", "Z", "Lkotlin/jvm/functions/Function0;", m.parameterTypes[3].toString(), "I", "I") &&
                i.argumentRegisters().firstOrNull() == r
        }
        require(consumer != null)
        return ThemeContracts.Site(at + 2, "invoke-static {v$p, v$r}, $EXTENSION->headerTitle(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;\nmove-result-object v$r")
    }
    fun mode(m: Method, owner: ClassDef, accepted: String): String? = runCatching {
        when {
            accepted.startsWith("${ThemeSurfaceContracts.SETTINGS}->") -> { rowsSite(m); ROWS }
            accepted == HEADER_TARGET && owner.superclass == "Ljava/lang/Object;" && owner.interfaces.isEmpty() -> { headerSite(m); HEADER }
            else -> null
        }
    }.getOrNull()
    fun mutationAllowed(mode: String, m: Method, index: Int?, code: String?): Boolean = runCatching {
        val s = if (mode == ROWS) rowsSite(m) else headerSite(m)
        s.index == index && s.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace)
    }.getOrDefault(false)
}
