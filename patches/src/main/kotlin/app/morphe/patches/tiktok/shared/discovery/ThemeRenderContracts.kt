package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

/** Reviewed native render and startup bodies from both SHA-pinned APKs. */
internal object ThemeRenderContracts {
    const val APP = "Lcom/ss/android/ugc/aweme/app/host/AwemeHostApplication;"
    const val APP_HOOK = "app.morphe.patches.tiktok.misc.extension.ApplicationContextFingerprint::"
    const val DRAW_HOOK = "app.morphe.patches.tiktok.layout.theme.FeedCaptionDrawFingerprint::"
    const val APP_MODE = "experimental-application-context"
    const val DRAW_MODE = "experimental-feed-caption-draw"
    private const val APP_47 = "c0c6b8cc446ea3e9f61a4433e235850df3b79c73d8672e5781a5065aeb6a5378"
    fun appBoundary(method: Method, owner: ClassDef): Boolean = runCatching {
        method.definingClass == APP && owner.type == APP && owner.superclass == "Landroid/app/Application;" &&
            method.name == "attachBaseContext" && method.accessFlags == 17 && method.returnType == "V" &&
            method.parameterTypes == listOf("Landroid/content/Context;") && method.implementation!!.instructions.any { i ->
                i.opcode == Opcode.INVOKE_SUPER && (i as ReferenceInstruction).reference.toString() ==
                    "Landroid/app/Application;->attachBaseContext(Landroid/content/Context;)V" &&
                    i.argumentRegisters() == listOf(method.implementation!!.registerCount - 2, method.parameterRegister(0))
            }
    }.getOrDefault(false)
    fun drawBoundary(method: Method, owner: ClassDef): Boolean = runCatching {
        if (owner.superclass != "Landroid/view/View;" || method.name != "onDraw" || method.accessFlags != 17 ||
            method.parameterTypes != listOf("Landroid/graphics/Canvas;") || method.returnType != "V") return false
        val body = method.implementation ?: return false
        if (body.registerCount != 5 || body.tryBlocks.isNotEmpty()) return false
        val ins = body.instructions.toList()
        if (ins.map { it.opcode } != listOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_VIRTUAL, Opcode.IGET_OBJECT,
            Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.INT_TO_FLOAT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.INT_TO_FLOAT, Opcode.INVOKE_VIRTUAL,
            Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL, Opcode.RETURN_VOID)) return false
        val field = (ins[2] as ReferenceInstruction).reference as FieldReference
        val get = ins[2] as TwoRegisterInstruction
        if (field.definingClass != owner.type || field.type != "Landroid/text/Layout;" || get.registerA != 2 || get.registerB != 3) return false
        val calls = listOf(0 to "Landroid/view/View;->onDraw(Landroid/graphics/Canvas;)V",
            1 to "Landroid/graphics/Canvas;->save()I", 4 to "Landroid/view/View;->getPaddingLeft()I",
            7 to "Landroid/view/View;->getPaddingTop()I", 10 to "Landroid/graphics/Canvas;->translate(FF)V",
            11 to "Landroid/text/Layout;->draw(Landroid/graphics/Canvas;)V", 12 to "Landroid/graphics/Canvas;->restore()V")
        calls.all { (index, ref) -> (ins[index] as ReferenceInstruction).reference.toString() == ref } &&
            ins[11].argumentRegisters() == listOf(2, 4) && ins[0].argumentRegisters() == listOf(3, 4) &&
            owner.methods.any { it.name == "getTextLayout" && it.parameterTypes.isEmpty() && it.returnType == "Landroid/text/Layout;" &&
                it.implementation?.instructions?.firstOrNull()?.let { i -> i.opcode == Opcode.IGET_OBJECT &&
                    (i as ReferenceInstruction).reference.toString() == field.toString() } == true }
    }.getOrDefault(false)
    fun site(method: Method): ThemeContracts.Site = if (method.definingClass == APP) {
        ThemeContracts.Site(0, "invoke-static/range {p1 .. p1}, Lapp/morphe/extension/shared/Utils;->primeContext(Landroid/content/Context;)V")
    } else ThemeContracts.Site(11, "invoke-static {v3, v2}, Lapp/morphe/extension/tiktok/theme/ThemeCaptionRenderer;->beforeDraw(Landroid/view/View;Landroid/text/Layout;)V")
    fun mode(method: Method, owner: ClassDef, accepted: String, contracts: FixtureContracts.Reviewed): String? {
        val digest = FixtureContracts.portableSignature(method)
        return when {
            accepted == contracts.hookMethods[APP_HOOK] && appBoundary(method, owner) &&
                digest in setOf(contracts.portableMethods[accepted], APP_47) -> APP_MODE
            accepted == contracts.hookMethods[DRAW_HOOK] && drawBoundary(method, owner) &&
                digest == contracts.portableMethods[accepted] -> DRAW_MODE
            else -> null
        }
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        site(method).let { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
