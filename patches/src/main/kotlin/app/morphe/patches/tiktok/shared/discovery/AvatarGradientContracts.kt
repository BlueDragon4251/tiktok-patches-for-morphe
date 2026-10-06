package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object AvatarGradientContracts {
    const val HOOK = "app.morphe.patches.tiktok.layout.theme.AvatarGradientConfigFingerprint::"
    const val MODE = "experimental-avatar-gradient-config"
    const val CONFIG = "Lcom/ss/android/ugc/aweme/story/setting/ui/color/ColorConfig;"
    // Complete register/control-flow preserving digest of the separately reviewed 47.1.3 body.
    // APK SHA-256: 8b5569f592a5534652ae460ef1d9e7f7394b5b7fdde44ae64f106d76767e2622.
    private const val GLOBAL_47_SIGNATURE = "abee459a5b9da3c873555f93ca307a1c4abf13dd5bcd9a46f37b1388b8776655"
    private fun reference(i: Instruction) = (i as? ReferenceInstruction)?.reference

    fun boundary(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean = runCatching {
        if (method.accessFlags != 25 || method.parameterTypes != listOf(CONFIG, "Landroid/content/Context;") ||
            owner.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty()) return false
        val body = method.implementation ?: return false
        if (body.tryBlocks.isNotEmpty()) return false
        val first = reference(body.instructions.first()) as? FieldReference ?: return false
        if (first.definingClass != CONFIG || first.name != "shaderParam" ||
            first.type != "Lcom/ss/android/ugc/aweme/story/setting/ui/color/ShaderParam;") return false
        val result = resolve(method.returnType) ?: return false
        if (result.fields.map { it.type }.sorted() != listOf("[F", "[I")) return false
        val constructor = result.methods.singleOrNull { it.name == "<init>" && it.parameterTypes == listOf("[F", "[I") } ?: return false
        val constructorBody = constructor.implementation?.instructions?.toList() ?: return false
        if (constructorBody.size != 4 || constructorBody.first().opcode != Opcode.INVOKE_DIRECT ||
            (reference(constructorBody.first()) as? MethodReference)?.toString() != "Ljava/lang/Object;-><init>()V" ||
            constructorBody.last().opcode != Opcode.RETURN_VOID) return false
        val stores = constructor.implementation?.instructions?.filter { it.opcode == Opcode.IPUT_OBJECT }?.toList() ?: return false
        if (stores.size != 2 || stores.any { i ->
            val field = reference(i) as? FieldReference ?: return@any true
            field.definingClass != result.type ||
                (i as TwoRegisterInstruction).registerA != constructor.parameterRegister(if (field.type == "[F") 0 else 1) ||
                i.registerB != constructor.implementation!!.registerCount - 3
        }) return false
        site(method)
        true
    }.getOrDefault(false)

    fun site(method: Method): ThemeContracts.Site {
        val instructions = method.implementation!!.instructions.toList()
        val (callIndex, call) = instructions.withIndex().single { (_, i) ->
            val ref = reference(i) as? MethodReference
            i.opcode == Opcode.INVOKE_DIRECT && ref?.definingClass == method.returnType &&
                ref.name == "<init>" && ref.parameterTypes == listOf("[F", "[I")
        }
        val (output, positions, colors) = call.argumentRegisters()
        val allocation = instructions[callIndex - 1]
        require(allocation.opcode == Opcode.NEW_INSTANCE && (allocation as OneRegisterInstruction).registerA == output &&
            (reference(allocation) as TypeReference).type == method.returnType &&
            instructions[callIndex + 1].opcode == Opcode.RETURN_OBJECT &&
            (instructions[callIndex + 1] as OneRegisterInstruction).registerA == output &&
            output != positions && output != colors && listOf(output, positions, colors).all { it < 16 })
        return ThemeContracts.Site(callIndex - 1, """
            invoke-static {v$colors, v$positions}, Lapp/morphe/extension/tiktok/theme/ThemeAvatarGradientGuard;->isUsable([I[F)Z
            move-result v$output
            if-nez v$output, :blueit_valid_avatar_gradient
            const/4 v$output, 0x0
            return-object v$output
            :blueit_valid_avatar_gradient
            nop
        """.trimIndent())
    }

    fun mode(method: Method, owner: ClassDef, accepted: String, contracts: FixtureContracts.Reviewed,
             resolve: (String) -> ClassDef?): String? = MODE.takeIf {
        accepted == contracts.hookMethods[HOOK] && boundary(method, owner, resolve) &&
            FixtureContracts.portableSignature(method) in setOf(contracts.portableMethods[accepted], GLOBAL_47_SIGNATURE)
    }

    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        site(method).let { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
