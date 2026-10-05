package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

internal object DownloadSuccessContracts {
    const val MODE = "experimental-download-success-path-fields"
    private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
    private const val STRING = "Ljava/lang/String;"
    private const val OBJECT = "Ljava/lang/Object;"
    const val ACCEPTED = "LX/0Xo8;->invokeSuspend($OBJECT)$OBJECT"
    fun code(method: Method, resolve: (String) -> ClassDef?): String {
        if (method.name != "invokeSuspend" || method.accessFlags != 17 || method.returnType != OBJECT ||
            method.parameterTypes.map(CharSequence::toString) != listOf(OBJECT)) throw PatchException("Changed download coroutine boundary")
        val insns = method.implementation?.instructions?.toList() ?: throw PatchException("No download coroutine body")
        val strings = insns.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
        if (strings.count { it.endsWith(".startDownload\$globalListener\$1\$onSuccess\$1") } != 1 ||
            !strings.containsAll(listOf("filePath", "fileExist", "aweme_share_monitor"))) throw PatchException("Changed download success role")
        val receiver = method.parameterRegister(0) - 1
        if (receiver >= 16) throw PatchException("Download receiver needs a different register encoding")
        val aliases = ReceiverAliases.atEveryInstruction(method)
        fun preservedAlias(index: Int, register: Int) = register in aliases[index].orEmpty()
        val paths = insns.withIndex().mapNotNull { (index, i) ->
            val ref = (i as? ReferenceInstruction)?.reference as? MethodReference ?: return@mapNotNull null
            if (i.opcode !in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) || ref.name != "<init>" ||
                ref.parameterTypes.map(CharSequence::toString) != listOf(STRING) ||
                (ref.definingClass != "Ljava/io/File;" && resolve(ref.definingClass)?.superclass != "Ljava/io/File;")) return@mapNotNull null
            val read = insns.getOrNull(index - 1) as? TwoRegisterInstruction ?: return@mapNotNull null
            val field = (read as? ReferenceInstruction)?.reference as? FieldReference ?: return@mapNotNull null
            field.takeIf { read.opcode == Opcode.IGET_OBJECT && it.definingClass == method.definingClass && it.type == STRING &&
                i.argumentRegisters().size == 2 && read.registerA == i.argumentRegisters()[1] && preservedAlias(index - 1, read.registerB) }
        }.distinctBy { it.toString() }
        val path = paths.singleOrThrow("Unique actual File path field")
        val aweme = insns.withIndex().mapNotNull { (index, i) ->
            val field = (i as? ReferenceInstruction)?.reference as? FieldReference
            field?.takeIf { i.opcode == Opcode.IGET_OBJECT && it.definingClass == method.definingClass && it.type == AWEME &&
                preservedAlias(index, (i as TwoRegisterInstruction).registerB) }
        }.distinctBy { it.toString() }.singleOrThrow("Unique download Aweme field")
        val (pathTemp, awemeTemp) = ScratchContracts.locals(method, 0, emptySet(), 2)
        return """
            iget-object v$pathTemp, p0, $path
            iget-object v$awemeTemp, p0, $aweme
            invoke-static {v$pathTemp, v$awemeTemp}, Lapp/morphe/extension/tiktok/download/DownloadFilenameFormatter;->renameDownloadedMedia(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v$pathTemp
            iput-object v$pathTemp, p0, $path
            invoke-static {v$pathTemp, v$awemeTemp}, Lapp/morphe/extension/tiktok/download/OriginalPhotoModeDownloader;->onTikTokDownloadCompleted(Ljava/lang/String;Lcom/ss/android/ugc/aweme/feed/model/Aweme;)V
        """.trimIndent()
    }
    fun mode(method: Method, owner: ClassDef, accepted: String, resolve: (String) -> ClassDef?): String? {
        if (accepted != ACCEPTED) return null
        val visited = hashSetOf<String>()
        var type: String? = owner.superclass
        var continuation = false
        while (type != null && visited.add(type)) {
            if (type == "Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;") { continuation = true; break }
            val base = resolve(type) ?: break
            // TikTok relocates Kotlin's continuation implementation outside kotlin/.
            // Identify its complete public protocol, never a guessed obfuscated name.
            val methods = base.methods.toList()
            if (base.superclass == "Ljava/lang/Object;" && "Ljava/io/Serializable;" in base.interfaces &&
                methods.count { it.name == "invokeSuspend" && it.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) && it.returnType == OBJECT } == 1 &&
                methods.count { it.name == "resumeWith" && it.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) && it.returnType == "V" } == 1 &&
                methods.count { it.name == "getContext" && it.parameterTypes.isEmpty() && it.returnType == "Lkotlin/coroutines/CoroutineContext;" } == 1 &&
                methods.count { it.name == "getStackTraceElement" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/StackTraceElement;" } == 1 &&
                listOf("getCompletion", "getCallerFrame", "releaseIntercepted").all { name -> methods.count { it.name == name && it.parameterTypes.isEmpty() } == 1 }) {
                continuation = true
                break
            }
            type = base.superclass
        }
        return MODE.takeIf { continuation && "Lkotlin/jvm/functions/Function2;" in owner.interfaces &&
            runCatching { code(method, resolve) }.isSuccess }
    }
    fun mutationAllowed(method: Method, index: Int?, injected: String?, resolve: (String) -> ClassDef?): Boolean =
        index == 0 && runCatching { injected?.filterNot(Char::isWhitespace) == code(method, resolve).filterNot(Char::isWhitespace) }.getOrDefault(false)
}
