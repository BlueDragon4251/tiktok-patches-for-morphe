package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i

internal object ThemeContracts {
    const val STYLED = "LX/0547;->LIZLLL(ILandroid/content/Context;[I)Ljava/lang/Integer;"
    const val STYLED_MODE = "experimental-tux-styled-resource-relocation"
    const val PROVIDER = "LX/0VTU;->LIZIZ(LX/008m;)LX/05Pc;"
    const val PROVIDER_MODE = "experimental-compose-palette-return"
    private const val RESOLVER = "Lapp/morphe/extension/tiktok/theme/ThemeColorResolver;"
    val presets = setOf("default", "material_you", "material_you_amoled", "oled_black", "liquid_glass", "frosted_graphite",
        "midnight_neon", "rose_noir", "arctic_blue", "aurora_violet", "sunset_ember", "custom")
    data class Site(val index: Int, val code: String)
    fun tuxEntrySite(method: Method, role: String, preset: String): Site {
        if (preset !in presets || method.accessFlags != 25) throw PatchException("Unreviewed theme preset or TUX entry")
        val params = method.parameterTypes.map(CharSequence::toString)
        val suffix = when (role) {
            "direct", "semantic" -> {
                if (params != listOf("I", "Landroid/content/Context;") || method.returnType != "Ljava/lang/Integer;") throw PatchException("Changed TUX color entry")
                "resolve(ILandroid/content/Context;Ljava/lang/String;)Ljava/lang/Integer;"
            }
            "generic" -> {
                if (params != listOf("I", "Landroid/content/Context;", "Lkotlin/jvm/functions/Function1;") || method.returnType != "Ljava/lang/Object;") throw PatchException("Changed TUX generic entry")
                "resolveGeneric(ILandroid/content/Context;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
            }
            "styled" -> {
                if (params != listOf("I", "Landroid/content/Context;", "[I") || method.returnType != "Ljava/lang/Integer;") throw PatchException("Changed TUX styled entry")
                "resolveFromAttributeArray(ILandroid/content/Context;[ILjava/lang/String;)Ljava/lang/Integer;"
            }
            else -> throw PatchException("Unknown native TUX role")
        }
        if (params.indices.any { method.parameterRegister(it) > 15 }) throw PatchException("TUX parameters no longer fit the proven invocation")
        val temp = ScratchContracts.locals(method, 0, emptySet(), 1).single()
        val args = (params.indices.map { "p$it" } + "v$temp").joinToString(", ")
        return Site(0, "const-string v$temp, \"$preset\"\ninvoke-static {$args}, $RESOLVER->$suffix\nmove-result-object v$temp\n" +
            "if-eqz v$temp, :theme_${role}_original\nreturn-object v$temp\n:theme_${role}_original\nnop")
    }
    private fun ref(i: Instruction?) = ((i as? ReferenceInstruction)?.reference as? MethodReference)
    private fun subtype(type: String, parent: String, resolve: (String) -> ClassDef?): Boolean {
        val pending = ArrayDeque<String>().apply { add(type) }; val visited = hashSetOf<String>()
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (next == parent) return true
            if (!visited.add(next)) continue
            resolve(next)?.let { it.superclass?.let(pending::add); it.interfaces.forEach(pending::add) }
        }
        return false
    }
    fun paletteProviderBoundary(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean = runCatching {
        val composer = method.parameterTypes.singleOrNull()?.toString() ?: return false
        val palette = resolve(method.returnType) ?: return false
        val fields = palette.fields.toList()
        if (method.accessFlags !in setOf(9, 25) || owner.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty() ||
            resolve(composer)?.accessFlags?.and(0x200) != 0x200 || fields.count { it.type == "J" } < 200 ||
            fields.filter { it.type != "J" }.let { it.size > 4 || it.any { !it.type.startsWith("L") } }) return false
        val insns = method.implementation!!.instructions.toList()
        val group = if (insns.size == 8) 2 else if (insns.size == 5) 0 else return false
        val input = method.parameterRegister(0)
        val field = ((insns[group] as? ReferenceInstruction)?.reference as? FieldReference) ?: return false
        val get = ref(insns[group + 1]) ?: return false
        if (insns[group].opcode != Opcode.SGET_OBJECT || get.definingClass != composer ||
            get.returnType != "Ljava/lang/Object;" || get.parameterTypes.size != 1 ||
            !subtype(field.type, get.parameterTypes.single().toString(), resolve) ||
            insns[group + 1].opcode !in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) ||
            insns[group + 1].argumentRegisters() != listOf(input, (insns[group] as OneRegisterInstruction).registerA)) return false
        val result = method.resultRegister(group + 1, "Ljava/lang/Object;")
        val cast = insns[group + 3]
        if (cast.opcode != Opcode.CHECK_CAST || (cast as OneRegisterInstruction).registerA != result ||
            ((cast as ReferenceInstruction).reference as TypeReference).type != method.returnType ||
            insns.last().opcode != Opcode.RETURN_OBJECT || (insns.last() as OneRegisterInstruction).registerA != result) return false
        if (group == 2 && (insns[0].opcode != Opcode.CONST || insns[1].opcode !in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) ||
                ref(insns[1])?.definingClass != composer || ref(insns[1])?.parameterTypes?.map(CharSequence::toString) != listOf("I") || ref(insns[1])?.returnType != "V" ||
                insns[1].argumentRegisters() != listOf(input, (insns[0] as OneRegisterInstruction).registerA) ||
                insns[6].opcode !in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) || ref(insns[6])?.definingClass != composer ||
                ref(insns[6])?.parameterTypes?.isEmpty() != true || ref(insns[6])?.returnType != "V" || insns[6].argumentRegisters() != listOf(input))) return false
        input != result && input != (insns[group] as OneRegisterInstruction).registerA
    }.getOrDefault(false)
    fun paletteSite(method: Method): Site {
        val insns = method.implementation!!.instructions.toList()
        val index = insns.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.singleOrThrow("Unique palette return").index
        val register = (insns[index] as OneRegisterInstruction).registerA
        return Site(index, "invoke-static/range {v$register .. v$register}, Lapp/morphe/extension/tiktok/theme/ThemeComposeColorResolver;->mapPalette(Ljava/lang/Object;)Ljava/lang/Object;\n" +
            "move-result-object v$register\ncheck-cast v$register, ${method.returnType}")
    }
    fun mode(method: Method, owner: ClassDef, accepted: String, contracts: FixtureContracts.Reviewed,
             resolve: (String) -> ClassDef?): String? = runCatching {
        ThemeSurfaceContracts.mode(method, owner, accepted, resolve)?.let { return it }
        if (accepted == PROVIDER) return PROVIDER_MODE.takeIf { method.accessFlags == 9 &&
            method.implementation?.instructions?.count() == 5 && paletteProviderBoundary(method, owner, resolve) &&
            FixtureContracts.portableSignature(method) == contracts.portableMethods[PROVIDER] }
        if (accepted != STYLED || owner.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty()) return null
        tuxEntrySite(method, "styled", "default")
        val body = method.implementation ?: return null
        val insns = body.instructions.toMutableList()
        val resource = insns.getOrNull(1) ?: return null
        if (resource.opcode != Opcode.CONST || resource !is OneRegisterInstruction || resource.registerA != 1 ||
            resource !is WideLiteralInstruction || resource.wideLiteral !in setOf(2131100553L, 2131100558L)) return null
        // Only this reviewed Android style attribute may relocate. All other literals,
        // registers, references, branches, switch payloads and handlers remain exact.
        insns[1] = ImmutableInstruction31i(Opcode.CONST, 1, 2131100553)
        val normalized = ImmutableMethod(method.definingClass, method.name, method.parameters, method.returnType,
            method.accessFlags, method.annotations, method.hiddenApiRestrictions,
            ImmutableMethodImplementation(body.registerCount, insns, body.tryBlocks, body.debugItems))
        STYLED_MODE.takeIf { FixtureContracts.portableSignature(normalized) == contracts.portableMethods[STYLED] }
    }.getOrNull()
    fun paletteMutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        paletteSite(method).let { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        presets.any { preset -> tuxEntrySite(method, "styled", preset).let {
            it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace)
        } }
    }.getOrDefault(false)
}
