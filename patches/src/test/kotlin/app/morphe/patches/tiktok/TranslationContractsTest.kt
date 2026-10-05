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

class TranslationContractsTest {
    private val response = "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;"
    private fun loaded(wrongAlias: Boolean = false, wrongConsumption: Boolean = false): ImmutableMethod = ImmutableMethod("LX/Loaded;", "response",
        listOf(response, "Z", "Ljava/lang/String;", "Ljava/lang/String;", "LX/Callback;", "Ljava/lang/String;", "Ljava/lang/String;", "LX/Continuation;", "I", "I")
            .map { ImmutableMethodParameter(it, emptySet(), null) }, "V", 17, emptySet(), emptySet(), MethodImplementationBuilder(16).apply {
            addInstruction(BuilderInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, if (wrongAlias) 7 else 6))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 3, 0))
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 2, 0, ImmutableFieldReference(response, "lazySplitItemsParseTask", "LX/Task;")))
            addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, 3, if (wrongConsumption) 1 else 0, ImmutableFieldReference(response, "lazySplitItemsParseTask", "LX/Task;")))
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 1, 0, ImmutableFieldReference(response, "items", "Ljava/util/List;")))
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 1, 0, ImmutableFieldReference(response, "jsonData", "Ljava/lang/String;")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod, superclass: String = "Ljava/lang/Object;") = ImmutableClassDef(m.definingClass, 1,
        superclass, emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun responseHookUsesTheActualFirstParameterAliasBeforeSplitConsumption() {
        val m = loaded()
        val site = TranslationContracts.loadedSite(m, owner(m))
        assertEquals(2, site.index)
        assertTrue(site.code.contains("{v0 .. v0}"))
        assertTrue(TranslationContracts.mutationAllowed(TranslationContracts.LOADED_MODE, m, owner(m), site.index, site.code) { null })
        assertFalse(TranslationContracts.mutationAllowed(TranslationContracts.LOADED_MODE, m, owner(m), site.index + 1, site.code) { null })
        assertFalse(TranslationContracts.mutationAllowed(TranslationContracts.LOADED_MODE, m, owner(m), site.index, site.code.replace("v0", "p0")) { null })
    }
    @Test fun anUnrelatedResponseOrChangedConsumptionIsRejected() {
        for (m in listOf(loaded(wrongAlias = true), loaded(wrongConsumption = true)))
            assertThrows(PatchException::class.java) { TranslationContracts.loadedSite(m, owner(m)) }
    }
    private val cell = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;"
    private val powerCell = "Lcom/bytedance/ies/powerlist/PowerCell;"
    private fun bind(wrongReceiver: Boolean = false): ImmutableMethod = ImmutableMethod(cell, "bind",
        listOf(ImmutableMethodParameter("LX/Item;", emptySet(), null)), "V", 1, emptySet(), emptySet(), MethodImplementationBuilder(4).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_SUPER, 2, if (wrongReceiver) 0 else 2, 3, 0, 0, 0,
                ImmutableMethodReference(powerCell, "onBindItemView", listOf("LX/ParentItem;"), "V")))
            addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("comment_panel")))
            for (type in listOf("Lcom/ss/android/ugc/aweme/comment/model/Comment;", "LX/Context;", "LX/Callback;"))
                addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, 0, 1, ImmutableFieldReference("LX/Observer;", type.substringAfterLast('/'), type)))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    private fun resolve(t: String) = when (t) {
        powerCell -> ImmutableClassDef(t, 1, "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", emptyList(), null, emptySet(), emptyList(), emptyList())
        "LX/Observer;" -> ImmutableClassDef(t, 1, "Ljava/lang/Object;", listOf("Landroidx/lifecycle/Observer;"), null, emptySet(), emptyList(), emptyList())
        else -> null
    }
    @Test fun registerCellRunsOnlyAfterTheObserverIsInitializedWithProvenScratch() {
        val m = bind()
        val site = TranslationContracts.bindSite(m, owner(m, powerCell), ::resolve)
        assertEquals(5, site.index)
        assertTrue(site.code.contains("move-object/from16 v0, v2"))
        assertTrue(site.code.contains("{v0, v1}"))
        assertFalse(TranslationContracts.mutationAllowed(TranslationContracts.BIND_MODE, m, owner(m, powerCell), site.index, site.code.replace("v0", "v3"), ::resolve))
    }
    @Test fun anUnprovenViewHolderObserverOrSuperReceiverRefusesBinding() {
        val m = bind()
        assertThrows(PatchException::class.java) { TranslationContracts.bindSite(m, owner(m, powerCell)) { null } }
        val changed = bind(wrongReceiver = true)
        assertThrows(PatchException::class.java) { TranslationContracts.bindSite(changed, owner(changed, powerCell), ::resolve) }
    }
}
