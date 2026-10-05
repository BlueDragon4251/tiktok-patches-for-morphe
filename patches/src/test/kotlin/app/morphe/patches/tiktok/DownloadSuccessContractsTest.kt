package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class DownloadSuccessContractsTest {
    private val type = "LX/Coroutine;"
    private fun method(ambiguous: Boolean = false): ImmutableMethod = ImmutableMethod(type, "invokeSuspend",
        listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)), "Ljava/lang/Object;", 17,
        emptySet(), emptySet(), MethodImplementationBuilder(6).apply {
            for (s in listOf("DownloadAction.startDownload\$globalListener\$1\$onSuccess\$1", "filePath", "fileExist", "aweme_share_monitor"))
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 2, ImmutableStringReference(s)))
            for (name in if (ambiguous) listOf("path", "otherPath") else listOf("path")) {
                addInstruction(BuilderInstruction21c(Opcode.NEW_INSTANCE, 1, ImmutableTypeReference("LX/FileSubclass;")))
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 4, ImmutableFieldReference(type, name, "Ljava/lang/String;")))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 2, 1, 0, 0, 0, 0,
                    ImmutableMethodReference("LX/FileSubclass;", "<init>", listOf("Ljava/lang/String;"), "V")))
            }
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 4,
                ImmutableFieldReference(type, "video", "Lcom/ss/android/ugc/aweme/feed/model/Aweme;")))
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(type, 1, "LX/Continuation;", listOf("Lkotlin/jvm/functions/Function2;"), null, emptySet(), emptyList(), listOf(m))
    private fun resolve(type: String): ImmutableClassDef? = when(type) {
        "LX/Continuation;" -> ImmutableClassDef(type, 1, "Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;", emptyList(), null, emptySet(), emptyList(), emptyList())
        "LX/FileSubclass;" -> ImmutableClassDef(type, 1, "Ljava/io/File;", emptyList(), null, emptySet(), emptyList(), emptyList())
        else -> null
    }
    @Test fun theFileConstructorSelectsThePathAndProvenScratchLocals() {
        val m = method()
        assertEquals(DownloadSuccessContracts.MODE, DownloadSuccessContracts.mode(m, owner(m), DownloadSuccessContracts.ACCEPTED, ::resolve))
        val code = DownloadSuccessContracts.code(m, ::resolve)
        assertTrue(code.contains("->path:Ljava/lang/String;"))
        assertTrue(code.contains("->video:Lcom/ss/android/ugc/aweme/feed/model/Aweme;"))
        assertTrue(DownloadSuccessContracts.mutationAllowed(m, 0, code, ::resolve))
        assertFalse(DownloadSuccessContracts.mutationAllowed(m, 1, code, ::resolve))
        assertFalse(DownloadSuccessContracts.mutationAllowed(m, 0, code.replace("->path:", "->otherPath:"), ::resolve))
        assertNull(DownloadSuccessContracts.mode(m, owner(m), DownloadSuccessContracts.ACCEPTED) { null })
    }
    @Test fun twoDifferentFilePathFieldsRemainAmbiguous() {
        val m = method(true)
        assertNull(DownloadSuccessContracts.mode(m, owner(m), DownloadSuccessContracts.ACCEPTED, ::resolve))
    }
    @Test fun relocatedContinuationRequiresItsCompleteProtocol() {
        val m = method()
        for (complete in listOf(false, true)) {
            val signatures = listOf("invokeSuspend" to "Ljava/lang/Object;", "resumeWith" to "V",
                "getContext" to "Lkotlin/coroutines/CoroutineContext;", "getStackTraceElement" to "Ljava/lang/StackTraceElement;",
                "getCompletion" to "LX/Completion;", "getCallerFrame" to "LX/Frame;", "releaseIntercepted" to "V")
            val methods = signatures.filter { complete || it.first != "resumeWith" }.map { (name, ret) ->
                ImmutableMethod("LX/Continuation;", name,
                    if (name in setOf("invokeSuspend", "resumeWith")) listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)) else emptyList(),
                    ret, 1025, emptySet(), emptySet(), null)
            }
            val base = ImmutableClassDef("LX/Continuation;", 1, "Ljava/lang/Object;", listOf("Ljava/io/Serializable;"), null, emptySet(), emptyList(), methods)
            val mode = DownloadSuccessContracts.mode(m, owner(m), DownloadSuccessContracts.ACCEPTED) { if (it == base.type) base else resolve(it) }
            if (complete) assertEquals(DownloadSuccessContracts.MODE, mode) else assertNull(mode)
        }
    }
}
