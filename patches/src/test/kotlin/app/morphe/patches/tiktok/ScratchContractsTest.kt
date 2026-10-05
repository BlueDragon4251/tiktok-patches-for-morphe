package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.ScratchContracts
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.*
import org.junit.Test

class ScratchContractsTest {
    private fun method(b: MethodImplementationBuilder, params: List<String> = emptyList(), flags: Int = 9,
                       result: String = "I") = ImmutableMethod("LX/Scratch;", "LIZ",
        params.map { ImmutableMethodParameter(it, emptySet(), null) }, result, flags, emptySet(), emptySet(), b.methodImplementation)
    @Test fun aProtectedOverwriteDoesNotHideAHandlerReadOfTheOldValue() {
        val b = MethodImplementationBuilder(4)
        val start = b.addLabel("start")
        b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
            ImmutableMethodReference("Ltest/Call;", "run", emptyList(), "V")))
        val end = b.addLabel("end")
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        val handler = b.addLabel("handler")
        b.addInstruction(BuilderInstruction11x(Opcode.MOVE_EXCEPTION, 2))
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        b.addCatch("Ljava/lang/Throwable;", start, end, handler)
        assertThrows(PatchException::class.java) { ScratchContracts.locals(method(b), 0, setOf(1, 2, 3), 1) }
    }
    @Test fun everyBranchMustKillTheIncomingValueBeforeReadingIt() {
        for (killBranch in listOf(false, true)) {
            val b = MethodImplementationBuilder(4)
            b.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, b.getLabel("branch")))
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            b.addLabel("branch")
            if (killBranch) b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 2))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            if (killBranch) assertEquals(listOf(0), ScratchContracts.locals(method(b), 0, setOf(1, 2, 3), 1))
            else assertThrows(PatchException::class.java) { ScratchContracts.locals(method(b), 0, setOf(1, 2, 3), 1) }
        }
    }
    @Test fun switchOffsetsUseTheSwitchInstructionRatherThanThePayloadAddress() {
        val b = MethodImplementationBuilder(4)
        b.addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 2, b.getLabel("payload")))
        b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        b.addLabel("case")
        b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 2))
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        b.addLabel("payload")
        b.addInstruction(BuilderPackedSwitchPayload(1, listOf(b.getLabel("case"))))
        assertEquals(listOf(0), ScratchContracts.locals(method(b), 0, setOf(1, 2, 3), 1))
    }
    @Test fun wideReadsAndInPlaceArithmeticKeepTheirInputWordsLive() {
        val wide = MethodImplementationBuilder(4).apply { addInstruction(BuilderInstruction11x(Opcode.RETURN_WIDE, 0)) }
        assertThrows(PatchException::class.java) { ScratchContracts.locals(method(wide, result = "J"), 0, setOf(0, 2, 3), 1) }
        val arithmetic = MethodImplementationBuilder(4).apply {
            addInstruction(BuilderInstruction12x(Opcode.ADD_INT_2ADDR, 0, 1))
            addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        }
        assertThrows(PatchException::class.java) { ScratchContracts.locals(method(arithmetic), 0, setOf(1, 2, 3), 1) }
    }
    @Test fun neverAllocatesTheReceiverOrEitherWordOfAWideParameter() {
        val b = MethodImplementationBuilder(4).apply { addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID)) }
        val m = method(b, params = listOf("J"), flags = 1, result = "V")
        assertEquals(listOf(0), ScratchContracts.locals(m, 0, emptySet(), 1))
        assertThrows(PatchException::class.java) { ScratchContracts.locals(m, 0, setOf(0), 1) }
    }
}
