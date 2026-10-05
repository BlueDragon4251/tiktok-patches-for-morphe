package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class CommentCopyContractsTest {
    private val params = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/content/Context;", "Lcom/bytedance/bpea/basics/Cert;")
    private val helperRef = ImmutableMethodReference("LX/Clipboard;", "copy", params, "V")
    private fun helper(wrongText: Boolean = false, wrongCert: Boolean = false) = ImmutableMethod(helperRef.definingClass, helperRef.name,
        params.map { ImmutableMethodParameter(it, emptySet(), null) }, "V", 9, emptySet(), emptySet(), MethodImplementationBuilder(6).apply {
            addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("clipboard")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, 4, 0, 0, 0, 0,
                ImmutableMethodReference("LX/Service;", "get", listOf(params[2], params[0]), "Ljava/lang/Object;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1))
            addInstruction(BuilderInstruction21c(Opcode.CHECK_CAST, 1, ImmutableTypeReference("Landroid/content/ClipboardManager;")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, 2, if (wrongText) 2 else 3, 0, 0, 0,
                ImmutableMethodReference("Landroid/content/ClipData;", "newPlainText", listOf("Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;"), "Landroid/content/ClipData;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 3, 1, 0, if (wrongCert) 3 else 5, 0, 0,
                ImmutableMethodReference("LX/Sink;", "set", listOf("Landroid/content/ClipboardManager;", "Landroid/content/ClipData;", params[3]), "V")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    private fun helperOwner(m: ImmutableMethod) = ImmutableClassDef(m.definingClass, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun theReadOnlyHelperForwardsItsActualLabelTextAndCertificate() {
        val m = helper()
        assertTrue(CommentCopyContracts.helperBoundary(m, helperOwner(m)))
        for (changed in listOf(helper(wrongText = true), helper(wrongCert = true)))
            assertFalse(CommentCopyContracts.helperBoundary(changed, helperOwner(changed)))
    }
    private fun caller(branchBypass: Boolean = false, overwriteCopied: Boolean = false): ImmutableMethod = ImmutableMethod("LX/Copy;", "copy",
        listOf(ImmutableMethodParameter(CommentCopyContracts.COMMENT, emptySet(), null)), "V", 17, emptySet(), emptySet(),
        MethodImplementationBuilder(8).apply {
            if (branchBypass) addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("result")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 7, 0, 0, 0, 0,
                ImmutableMethodReference(CommentCopyContracts.COMMENT, "getUser", emptyList(), "Lcom/ss/android/ugc/aweme/profile/model/User;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 7, 0, 0, 0, 0,
                ImmutableMethodReference(CommentCopyContracts.COMMENT, "getText", emptyList(), params[0])))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 5, 0, 0, 0, 0,
                ImmutableMethodReference("Ljava/lang/StringBuilder;", "append", listOf(params[0]), "Ljava/lang/StringBuilder;")))
            addLabel("result")
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 5, 0, 0, 0, 0,
                ImmutableMethodReference("LX/Builder;", "text", listOf("Ljava/lang/StringBuilder;"), params[0])))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 2))
            if (overwriteCopied) addInstruction(BuilderInstruction11n(Opcode.CONST_4, 2, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 4, 1, 2, 3, 4, 0, helperRef))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    @Test fun sanitizeRunsAfterTheTypedBuilderAndBeforeCommentTextCanBeReused() {
        val site = CommentCopyContracts.sites(caller(), helperRef).single()
        assertEquals(7, site.index)
        assertEquals(2, site.copied)
        assertEquals(0, site.text)
        assertTrue(site.code.contains("{v2, v0}"))
    }
    @Test fun aBranchBypassOrOverwrittenClipboardValueRefusesInjection() {
        for (changed in listOf(caller(branchBypass = true), caller(overwriteCopied = true)))
            assertThrows(PatchException::class.java) { CommentCopyContracts.sites(changed, helperRef) }
    }
}
