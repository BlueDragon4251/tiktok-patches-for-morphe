package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.*
import org.junit.Test

class TakoContractsTest {
    private val type = "Lcom/ss/android/ugc/aweme/feed/assem/tikbot/TakoAssem;"
    private val base = "Lcom/ss/android/ugc/feed/platform/cell/BaseCellSlotComponent;"
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(type, 17, base, emptyList(), null, emptySet(), emptyList(), listOf(m))
    private fun visibility(readOldScratch: Boolean = false): ImmutableMethod {
        val b = MethodImplementationBuilder(3)
        if (readOldScratch) b.addInstruction(BuilderInstruction12x(Opcode.MOVE, 1, 0))
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
            ImmutableMethodReference("Lcom/bytedance/assem/arch/reused/ReusedUIAssem;", "LJJJ", emptyList(), "Landroid/view/View;")))
        b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
            ImmutableMethodReference("Landroid/view/View;", "getVisibility", emptyList(), "I")))
        b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        return ImmutableMethod(type, "renamed", listOf(ImmutableMethodParameter("Z", emptySet(), null)), "V", 17,
            emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun visibilityGateNeedsADeadScratchAndAllowsOnlyTheReviewedFlagChange() {
        val m = visibility(); val accepted = "$type->Ar(Z)V"
        assertEquals(TakoContracts.VISIBILITY, TakoContracts.mode(m, owner(m), accepted))
        assertNull(TakoContracts.mode(visibility(true), owner(visibility(true)), accepted))
        assertTrue(TakoContracts.mutationAllowed(TakoContracts.VISIBILITY, 0, TakoContracts.visibilityCode()))
        assertFalse(TakoContracts.mutationAllowed(TakoContracts.VISIBILITY, 1, TakoContracts.visibilityCode()))
        assertFalse(TakoContracts.mutationAllowed(TakoContracts.VISIBILITY, 0, TakoContracts.visibilityCode().replace("p1", "p0")))
    }
    @Test fun viewCreatedSiteFollowsTheActualSuperReceiverAndViewArguments() {
        fun method(wrongView: Boolean) = ImmutableMethod(type, "onViewCreated",
            listOf(ImmutableMethodParameter("Landroid/view/View;", emptySet(), null)), "V", 17,
            emptySet(), emptySet(), MethodImplementationBuilder(4).apply {
                addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 2))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_SUPER, 2, 0, if(wrongView) 1 else 3, 0, 0, 0,
                    ImmutableMethodReference(base, "onViewCreated", listOf("Landroid/view/View;"), "V")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
        val m = method(false)
        assertEquals(TakoContracts.BIND, TakoContracts.mode(m, owner(m), m.toString()))
        assertNull(TakoContracts.mode(method(true), owner(method(true)), m.toString()))
        assertTrue(TakoContracts.mutationAllowed(TakoContracts.BIND, 2,
            "invoke-static/range {p1 .. p1}, Lapp/morphe/extension/tiktok/feedfilter/TakoAiFilter;->hideBoundFeedButtonView(Landroid/view/View;)V"))
    }
}
