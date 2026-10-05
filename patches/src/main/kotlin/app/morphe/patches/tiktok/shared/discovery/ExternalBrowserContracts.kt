package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object ExternalBrowserContracts {
    const val MODE = "experimental-external-browser-typed-gate"
    private const val CONTEXT = "Lcom/bytedance/hybrid/spark/third/router/SparkThirdContext;"
    private const val ACTIVITY = "Lcom/bytedance/hybrid/spark/page/SparkActivity;"
    private val accepted = mapOf(
        "router" to "LX/046f;->LIZIZ(Landroid/content/Context;$CONTEXT)V",
        "story" to "LX/15NO;->LIZIZ(Lcom/ss/android/ugc/aweme/sticker/data/InteractStickerStruct;)V",
        "activity" to "$ACTIVITY->onCreate(Landroid/os/Bundle;)V")
    data class Site(val role: String, val index: Int, val register: Int, val call: String) {
        val code get() = "$call\nmove-result v$register\nif-eqz v$register, :external_browser_${role}_original\nreturn-void\n:external_browser_${role}_original\nnop"
    }
    fun site(method: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Site {
        if (method.returnType != "V") throw PatchException("External link gate must return void")
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("Missing external link body")
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
        val params = method.parameterTypes.map(CharSequence::toString)
        val role: String
        val index: Int
        val call: String
        val runtime = "Lapp/morphe/extension/tiktok/externalbrowser/ExternalBrowserPatch;"
        when {
            method.accessFlags == 9 && params == listOf("Landroid/content/Context;", CONTEXT) -> {
                if (owner.superclass != "Ljava/lang/Object;" || !refs.containsAll(listOf("ContainerId", "Context_startActivity_1",
                        "$CONTEXT->url:Ljava/lang/String;", "$CONTEXT->containerId:Ljava/lang/String;",
                        "Lcom/bytedance/hybrid/spark/third/container/SparkThirdActivity;", "Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;")))
                    throw PatchException("Changed Spark router role")
                role = "router"; index = 0
                call = "invoke-static/range {p0 .. p1}, $runtime->openSparkThirdContext(Landroid/content/Context;Ljava/lang/Object;)Z"
            }
            method.accessFlags == 17 && params == listOf("Lcom/ss/android/ugc/aweme/sticker/data/InteractStickerStruct;") -> {
                if (owner.superclass != "Ljava/lang/Object;" || !refs.containsAll(listOf("external_website_security_pop_up_window_show",
                        "Lcom/ss/android/ugc/aweme/sticker/data/InteractStickerStruct;->getType()I",
                        "Lcom/ss/android/ugc/aweme/sticker/data/InteractStickerStruct;->getAttr()Ljava/lang/String;")))
                    throw PatchException("Changed story link role")
                role = "story"; index = 0
                call = "invoke-static/range {p0 .. p1}, $runtime->openStoryLink(Ljava/lang/Object;Ljava/lang/Object;)Z"
            }
            method.definingClass == ACTIVITY && method.name == "onCreate" && method.accessFlags == 17 && params == listOf("Landroid/os/Bundle;") -> {
                if ("SparkContextContainerId" !in refs) throw PatchException("Missing Spark activity intent role")
                val visited = hashSetOf<String>()
                var type: String? = owner.superclass
                while (type != "Landroid/app/Activity;" && type != null && visited.add(type)) type = resolve(type)?.superclass
                if (type != "Landroid/app/Activity;") throw PatchException("Spark host is not an Activity")
                val superIndex = insns.withIndex().filter { (_, i) ->
                    val ref = ((i as? ReferenceInstruction)?.reference as? MethodReference)
                    i.opcode in setOf(Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE) && ref?.name == "onCreate" &&
                        ref.parameterTypes.map(CharSequence::toString) == params && ref.returnType == "V" &&
                        ref.definingClass in visited + "Landroid/app/Activity;"
                }.singleOrThrow("Unique Spark super.onCreate").index
                val args = insns[superIndex].argumentRegisters()
                val bundle = method.parameterRegister(0)
                val receiver = bundle - 1
                val aliases = ReceiverAliases.atEveryInstruction(method)
                if (args.size != 2 || args[0] !in aliases[superIndex].orEmpty() || args[1] != bundle ||
                    receiver !in aliases[superIndex + 1].orEmpty() || insns.take(superIndex).any {
                        it.opcode.setsRegister() && it is OneRegisterInstruction &&
                            (it.registerA == bundle || it.opcode.setsWideRegister() && it.registerA + 1 == bundle)
                    }) throw PatchException("Changed Spark Activity/Bundle receiver boundary")
                role = "activity"; index = superIndex + 1
                call = "invoke-static/range {p0 .. p0}, $runtime->openSparkActivity(Landroid/app/Activity;)Z"
            }
            else -> throw PatchException("Unreviewed external link boundary")
        }
        return Site(role, index, ScratchContracts.locals(method, index, emptySet(), 1).single(), call)
    }
    fun mode(method: Method, owner: ClassDef, target: String, resolve: (String) -> ClassDef?): String? {
        if (target !in accepted.values) return null
        return runCatching { site(method, owner, resolve) }.getOrNull()?.let { site -> MODE.takeIf { target == accepted[site.role] } }
    }
    fun mutationAllowed(method: Method, owner: ClassDef, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        site(method, owner, resolve).let { it.index == index && code?.filterNot(Char::isWhitespace) == it.code.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
