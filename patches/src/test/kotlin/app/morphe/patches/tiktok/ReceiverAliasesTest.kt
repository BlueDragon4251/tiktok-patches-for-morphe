package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.ReceiverAliases
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import org.junit.Assert.*
import org.junit.Test

class ReceiverAliasesTest {
    @Test fun anExplicitObjectInputCanBeTrackedInAStaticMethod() {
        val b = MethodImplementationBuilder(3).apply {
            addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 2))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val m = ImmutableMethod("LX/Inputs;", "body", listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)), "V", 9, emptySet(), emptySet(), b.methodImplementation)
        assertEquals(setOf(0, 2), ReceiverAliases.atEveryInstruction(m, 2).getValue(1))
    }
    private fun method(b: MethodImplementationBuilder) = ImmutableMethod("LX/Receiver;", "body", emptyList(), "V", 1,
        emptySet(), emptySet(), b.methodImplementation)
    @Test fun anAliasCanBeReusedAfterItsLastNativeFieldRead() {
        val b = MethodImplementationBuilder(3).apply {
            addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 2))
            addInstruction(BuilderInstruction10x(Opcode.NOP))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val states = ReceiverAliases.atEveryInstruction(method(b))
        assertTrue(0 in states.getValue(1))
        assertFalse(0 in states.getValue(3))
    }
    @Test fun aClobberOnEitherIncomingBranchRemovesTheAlias() {
        val b = MethodImplementationBuilder(3)
        b.addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 2))
        b.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 1, b.getLabel("join")))
        b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
        b.addLabel("join")
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        assertFalse(0 in ReceiverAliases.atEveryInstruction(method(b)).getValue(3))
    }
    @Test fun aProtectedMoveDoesNotInventAnAliasOnItsExceptionEdge() {
        val b = MethodImplementationBuilder(3)
        val start = b.addLabel("start")
        b.addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 2))
        val end = b.addLabel("end")
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        val handler = b.addLabel("handler")
        b.addInstruction(BuilderInstruction11x(Opcode.MOVE_EXCEPTION, 1))
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        b.addCatch("Ljava/lang/Throwable;", start, end, handler)
        val states = ReceiverAliases.atEveryInstruction(method(b))
        assertTrue(0 in states.getValue(1))
        assertFalse(0 in states.getValue(2))
        assertTrue(2 in states.getValue(2))
    }
}
