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

class ClearDisplayContractsTest {
    private fun event(wrongOwner: Boolean = false, overwriteInput: Boolean = false, consumeBoolean: Boolean = true): ImmutableMethod =
        ImmutableMethod(ClearDisplayContracts.PANEL, "onClearModeEvent", listOf(ImmutableMethodParameter("LX/Event;", emptySet(), null)),
            "V", 17, emptySet(), emptySet(), MethodImplementationBuilder(5).apply {
                for (marker in listOf("onClearModeEvent type: ", " isClean: ", " enterMethod: "))
                    addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference(marker)))
                addInstruction(BuilderInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, 4))
                if (overwriteInput) addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
                addInstruction(BuilderInstruction22c(Opcode.IGET_BOOLEAN, 1, 0,
                    ImmutableFieldReference(if (wrongOwner) "LX/Other;" else "LX/Event;", "state", "Z")))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 2, 1, 0, 0, 0,
                    ImmutableMethodReference("Ljava/lang/StringBuilder;", "append", listOf(if (consumeBoolean) "Z" else "I"), "Ljava/lang/StringBuilder;")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(ClearDisplayContracts.PANEL, 1,
        "Lcom/ss/android/ugc/feed/platform/panel/BasePanelComponent;",
        listOf("Lcom/ss/android/ugc/feed/platform/panel/clearmode/IClearModePanelComponent;"), null, emptySet(), emptyList(), listOf(m))
    @Test fun theHookConsumesTheOriginalEventBooleanBeforeItsNativeAppend() {
        val m = event()
        val site = ClearDisplayContracts.eventSite(m, owner(m))
        assertEquals(5, site.index)
        assertTrue(site.code.contains("{v1 .. v1}"))
        assertEquals("X.Event", ClearDisplayContracts.eventClass(owner(m)))
        assertTrue(ClearDisplayContracts.mutationAllowed(ClearDisplayContracts.EVENT_MODE, m, owner(m), site.index, site.code, "insert"))
        assertFalse(ClearDisplayContracts.mutationAllowed(ClearDisplayContracts.EVENT_MODE, m, owner(m), site.index - 1, site.code, "insert"))
        assertFalse(ClearDisplayContracts.mutationAllowed(ClearDisplayContracts.EVENT_MODE, m, owner(m), site.index, site.code.replace("v1", "p0"), "insert"))
    }
    @Test fun anUnrelatedBooleanOrClobberedEventIsRejected() {
        for (m in listOf(event(wrongOwner = true), event(overwriteInput = true), event(consumeBoolean = false))) {
            assertThrows(PatchException::class.java) { ClearDisplayContracts.eventSite(m, owner(m)) }
            assertNull(ClearDisplayContracts.mode(m, owner(m), ClearDisplayContracts.EVENT))
        }
    }
    @Test fun identicalEventSignaturesDoNotAuthorizeTelemetrySuppression() {
        val m = event()
        assertNull(ClearDisplayContracts.mode(m, owner(m), ClearDisplayContracts.STATE))
        assertFalse(ClearDisplayContracts.mutationAllowed(ClearDisplayContracts.STATE_MODE, m, owner(m), 0, "return-void", "return-early"))
    }
}
