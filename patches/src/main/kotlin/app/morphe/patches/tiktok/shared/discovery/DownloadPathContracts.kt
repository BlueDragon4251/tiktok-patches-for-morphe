package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object DownloadPathContracts {
    const val MODE = "experimental-download-folder-builder"
    const val ACCEPTED = "LX/01Cd;->LIZLLL(Landroid/content/Context;Ljava/lang/String;)Landroid/net/Uri;"
    private const val APPEND = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
    data class Site(val index: Int, val register: Int, val builder: Int) {
        val code get() = "invoke-static {}, Lapp/morphe/extension/tiktok/download/DownloadsPatch;->getDownloadPath()Ljava/lang/String;\nmove-result-object v$register\ninvoke-virtual {v$builder, v$register}, $APPEND"
    }
    fun sites(method: Method): List<Site> {
        if (method.accessFlags != 9 || method.returnType != "Landroid/net/Uri;" ||
            method.parameterTypes.map(CharSequence::toString) != listOf("Landroid/content/Context;", "Ljava/lang/String;")) throw PatchException("Changed download Uri boundary")
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("Missing download Uri body")
        val strings = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        if (!strings.containsAll(listOf("/", "/Camera", "/Camera/", "video/mp4"))) throw PatchException("Changed download Uri role")
        val refs = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.toString() }
        if (!refs.containsAll(listOf("Ljava/io/File;->exists()Z", "Ljava/io/File;->mkdirs()Z"))) throw PatchException("Missing native gallery directory path")
        val sites = insns.indices.mapNotNull { site(method, it) }
        if (sites.size != 2) throw PatchException("Expected exactly two native directory builders")
        return sites
    }
    private fun site(method: Method, index: Int): Site? {
        val insns = method.implementation?.instructions?.toList() ?: return null
        val i = insns.getOrNull(index) ?: return null
        val field = ((i as? ReferenceInstruction)?.reference as? FieldReference) ?: return null
        if (field.definingClass != "Landroid/os/Environment;" || field.name != "DIRECTORY_DCIM" || field.type != "Ljava/lang/String;") return null
        if (i.opcode != Opcode.SGET_OBJECT) throw PatchException("Changed directory read")
        val r = (i as OneRegisterInstruction).registerA
        val first = insns.getOrNull(index + 1) ?: throw PatchException("Missing directory append")
        val camera = insns.getOrNull(index + 2) ?: throw PatchException("Missing Camera suffix")
        val second = insns.getOrNull(index + 3) ?: throw PatchException("Missing suffix append")
        val args = first.argumentRegisters()
        if (first.opcode != Opcode.INVOKE_VIRTUAL || (first as? ReferenceInstruction)?.reference?.toString() != APPEND ||
            args.size != 2 || args[1] != r || args[0] == r ||
            camera.opcode !in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) || (camera as OneRegisterInstruction).registerA != r ||
            ((camera as? ReferenceInstruction)?.reference as? StringReference)?.string !in setOf("/Camera", "/Camera/") ||
            second.opcode != Opcode.INVOKE_VIRTUAL || (second as? ReferenceInstruction)?.reference?.toString() != APPEND || second.argumentRegisters() != args)
            throw PatchException("Changed native directory/suffix builder")
        // Removing Camera's temporary must not change any later register read,
        // including a read from a protected exception handler.
        ScratchContracts.locals(method, index + 4, (0 until method.implementation!!.registerCount).filter { it != r }.toSet(), 1)
        return Site(index, r, args[0])
    }
    fun mode(method: Method, owner: ClassDef, accepted: String): String? = MODE.takeIf {
        accepted == ACCEPTED && owner.superclass == "Ljava/lang/Object;" && runCatching { sites(method) }.isSuccess
    }
    fun mutationAllowed(method: Method, index: Int?, code: String?): Boolean = runCatching {
        index?.let { site(method, it) }?.let { code?.filterNot(Char::isWhitespace) == ("replace-block 4\n" + it.code).filterNot(Char::isWhitespace) } == true
    }.getOrDefault(false)
}
