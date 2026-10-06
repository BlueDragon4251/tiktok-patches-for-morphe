package app.morphe.patches.tiktok
import app.morphe.patches.tiktok.shared.discovery.FeedDescriptionContracts
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test
class FeedDescriptionContractsTest {
    private fun getter(wrongReceiver: Boolean = false) = ImmutableMethod(FeedDescriptionContracts.OWNER, "getDescription",
        emptyList(), FeedDescriptionContracts.TEXT, 17, emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, if (wrongReceiver) 0 else 1,
                ImmutableFieldReference(FeedDescriptionContracts.OWNER, "lazyDescription", "LX/Lazy;")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference("LX/Lazy;", "getValue", emptyList(), "Ljava/lang/Object;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction21c(Opcode.CHECK_CAST, 0, ImmutableTypeReference(FeedDescriptionContracts.TEXT)))
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(m.definingClass, 17, "Ljava/lang/Object;", emptyList(),
        null, emptySet(), emptyList(), listOf(m))
    private val text = ImmutableClassDef(FeedDescriptionContracts.TEXT, 1, "Landroid/widget/TextView;", emptyList(),
        null, emptySet(), emptyList(), emptyList())
    @Test fun onlyNativeLazyTextReturnCanBeStyled() {
        val method = getter()
        assertTrue(FeedDescriptionContracts.boundary(method, owner(method)) { text })
        val site = FeedDescriptionContracts.site(method)
        assertEquals(4, site.index)
        assertTrue(FeedDescriptionContracts.mutationAllowed(method, site.index, site.code))
        assertFalse(FeedDescriptionContracts.mutationAllowed(method, 3, site.code))
        assertFalse(FeedDescriptionContracts.mutationAllowed(method, 4, site.code.replace("v0 .. v0", "v1 .. v1")))
    }
    @Test fun wrongReceiverAndUnknownTextHierarchyRefuseTheHook() {
        val wrong = getter(true)
        assertFalse(FeedDescriptionContracts.boundary(wrong, owner(wrong)) { text })
        val method = getter()
        assertFalse(FeedDescriptionContracts.boundary(method, owner(method)) { null })
    }
}
