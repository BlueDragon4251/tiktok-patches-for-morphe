package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** The settings chain resolves these roles from OpenDebug's actual state and callbacks. */
internal object SettingsContracts {
    const val ACTIVITY = "Lcom/bytedance/ies/ugc/aweme/commercialize/compliance/personalization/AdPersonalizationActivity;"
    const val BASE = "Lcom/bytedance/ies/foundation/activity/BaseActivity;"
    const val EXTENSION = "Lapp/morphe/extension/tiktok/settings/BlueITActivityHook;"
    const val CREATE = "experimental-settings-create"
    const val BACK = "experimental-settings-back"
    const val TITLE = "experimental-settings-title"
    val modes = setOf(CREATE, BACK, TITLE)
    val acceptedTargets = mapOf(
        "settings.composeTitle" to "LX/1C4V;->LIZ(LX/0mGA;ZZLX/008m;I)V",
        "settings.clickWrapper" to "Lkotlin/jvm/internal/AwS398S0200000_21_I1;->invoke\$60(Lkotlin/jvm/internal/AwS398S0200000_21_I1;)Ljava/lang/Object;",
        "settings.function2" to "Lkotlin/jvm/internal/AwS567S0100000_21_I1;->invoke\$13(Lkotlin/jvm/internal/AwS567S0100000_21_I1;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
    )

    data class ActivitySite(val index: Int, val receiver: Int, val scratch: Int)

    private fun Method.address(index: Int) = implementation!!.instructions.take(index).sumOf { it.codeUnits }
    private fun Method.protected(index: Int): Boolean {
        val address = address(index)
        return implementation!!.tryBlocks.any { address >= it.startCodeAddress && address < it.startCodeAddress + it.codeUnitCount }
    }

    /** Only straight-line, unprotected writes before any read may kill a scratch value.
     * No exception edge or branch is ignored, and invoke/range uses all argument words.
     */
    private fun Method.scratch(index: Int, receiver: Int): Int {
        val insns = implementation!!.instructions.toList()
        val locals = implementation!!.registerCount - parameterTypes.size - 1
        for (register in 0 until minOf(locals, 256)) {
            if (register == receiver) continue
            for (at in index until minOf(index + 3, insns.size)) {
                val insn = insns[at]
                if (protected(at) || insn is OffsetInstruction || insn.opcode in returnOpcodes) break
                val reads = when (insn) {
                    is FiveRegisterInstruction, is RegisterRangeInstruction -> insn.argumentRegisters()
                    is ThreeRegisterInstruction -> listOf(insn.registerB, insn.registerC)
                    is TwoRegisterInstruction -> listOf(insn.registerB)
                    else -> emptyList()
                }
                if (register in reads) break
                // Explicit whitelist: these writes cannot read their own destination.
                if (insn.opcode in setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16,
                        Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
                        Opcode.IGET_BOOLEAN) && (insn as OneRegisterInstruction).registerA == register)
                    return register
                // Do not infer safety beyond throwing or unknown instructions.
                if (insn.opcode !in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)) break
            }
        }
        throw PatchException("No proven unprotected scratch overwrite at $index in $this")
    }

    fun activitySite(method: Method, create: Boolean): ActivitySite {
        val body = method.implementation ?: throw PatchException("Settings activity has no body")
        if (method.definingClass != ACTIVITY || method.returnType != "V" ||
            method.accessFlags != (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) ||
            method.parameterTypes.map(CharSequence::toString) != if (create) listOf("Landroid/os/Bundle;") else emptyList<String>())
            throw PatchException("Changed settings activity signature: $method")
        val insns = body.instructions.toList()
        val receiver = body.registerCount - method.parameterTypes.size - 1
        val at = if (create) {
            val superIndex = insns.withIndex().filter { (_, insn) ->
                insn.opcode in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE) &&
                    (insn as? ReferenceInstruction)?.reference?.toString() == "$BASE->onCreate(Landroid/os/Bundle;)V"
            }.singleOrThrow("Settings super.onCreate").index
            // Follow copies of the declared receiver and Bundle through the entire prefix.
            val aliases = mutableMapOf(receiver to receiver, receiver + 1 to receiver + 1)
            for (insn in insns.take(superIndex)) {
                if (insn is OffsetInstruction || insn.opcode in returnOpcodes)
                    throw PatchException("Nonlinear settings onCreate prefix")
                if (insn.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)) {
                    insn as TwoRegisterInstruction
                    aliases[insn.registerA] = aliases[insn.registerB] ?: -1
                } else if (insn.opcode.setsRegister()) {
                    aliases.remove((insn as OneRegisterInstruction).registerA)
                }
            }
            val args = insns[superIndex].argumentRegisters()
            if (args.size != 2 || aliases[args[0]] != receiver || aliases[args[1]] != receiver + 1)
                throw PatchException("Settings super.onCreate arguments are not this and Bundle")
            superIndex + 1
        } else {
            val strings = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
            if (!strings.containsAll(listOf("exit_personalize_data", "pa_toggle_final_status")) ||
                insns.none { (it as? ReferenceInstruction)?.reference?.toString() == "$BASE->finish()V" })
                throw PatchException("Missing personalization back navigation anchors")
            0
        }
        if (method.protected(at)) throw PatchException("Settings injection lies inside an exception range")
        return ActivitySite(at, receiver, method.scratch(at, receiver))
    }

    fun mode(method: Method, accepted: String): String? = when (accepted) {
        "$ACTIVITY->onCreate(Landroid/os/Bundle;)V" -> runCatching { activitySite(method, true); CREATE }.getOrNull()
        "$ACTIVITY->onBackPressed()V" -> runCatching { activitySite(method, false); BACK }.getOrNull()
        acceptedTargets.getValue("settings.composeTitle") -> TITLE.takeIf {
            method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value) &&
                method.returnType == "V" && method.parameterTypes.size == 5 &&
                method.parameterTypes[1] == "Z" && method.parameterTypes[2] == "Z" && method.parameterTypes[4] == "I" &&
                runCatching { titleSite(method) }.isSuccess
        }
        else -> null
    }

    fun titleSite(method: Method): Pair<Int, Int> {
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("No Compose title body")
        val call = insns.withIndex().filter { (_, insn) ->
            insn.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                (insn as? ReferenceInstruction)?.reference?.toString() == "Landroid/content/Context;->getString(I)Ljava/lang/String;"
        }.singleOrThrow("OpenDebug Compose title getString").index
        val register = method.resultRegister(call, "Ljava/lang/String;")
        if (register > 255 || method.protected(call + 2)) throw PatchException("Unsupported Compose title boundary")
        return call + 2 to register
    }

    fun activityCode(site: ActivitySite, create: Boolean): String {
        val name = if (create) "initialize" else "handleBackPressed"
        val label = if (create) "do_not_open" else "blueit_service_settings_not_handled"
        return """
            invoke-static/range {v${site.receiver} .. v${site.receiver}}, $EXTENSION->$name($ACTIVITY)Z
            move-result v${site.scratch}
            if-eqz v${site.scratch}, :$label
            return-void
        """.trimIndent()
    }

    fun mutationAllowed(mode: String, method: Method, index: Int?, code: String?): Boolean {
        fun lines(value: String?) = value?.trim()?.lines()?.map(String::trim)?.filter(String::isNotEmpty)
        return runCatching {
            when (mode) {
                CREATE, BACK -> {
                    val site = activitySite(method, mode == CREATE)
                    index == site.index && lines(code) == lines(activityCode(site, mode == CREATE))
                }
                TITLE -> {
                    val (at, register) = titleSite(method)
                    index == at && lines(code) == listOf("const-string v$register, \"BlueIT Service\"")
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}
