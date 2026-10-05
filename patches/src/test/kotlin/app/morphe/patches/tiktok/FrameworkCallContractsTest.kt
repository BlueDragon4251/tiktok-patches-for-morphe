package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.*
import org.junit.Test

class FrameworkCallContractsTest {
    private fun method(owner: String = "Landroid/telephony/TelephonyManager;",
                       name: String = "getSimCountryIso", parameters: List<String> = emptyList(),
                       returns: String = "Ljava/lang/String;", range: Boolean = false,
                       resultOpcode: Opcode = Opcode.MOVE_RESULT_OBJECT): ImmutableMethod {
        val b = MethodImplementationBuilder(if (range) 25 else 3)
        val ref = ImmutableMethodReference(owner, name, parameters, returns)
        val words = 1 + parameters.size
        b.addInstruction(if (range) BuilderInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 20, words, ref)
            else BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, words, 1, 2, 0, 0, 0, ref))
        if (returns != "V") b.addInstruction(BuilderInstruction11x(resultOpcode, if (range) 19 else 0))
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        return ImmutableMethod("LX/Caller;", "LIZ", emptyList(), "V", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun supportsRangeAndKeepsTheActualStringResultRegister() {
        for (range in listOf(false, true)) {
            val m = method(range = range)
            val site = FrameworkCallContracts.simSites(m).single()
            assertEquals(if (range) 19 else 0, site.register)
            assertTrue(FrameworkCallContracts.mutationAllowed(m, 2, site.code, "insert"))
            assertFalse(FrameworkCallContracts.mutationAllowed(m, 1, site.code, "insert"))
            assertFalse(FrameworkCallContracts.mutationAllowed(m, 2, site.code.replace("getCountryIso", "getOperator"), "insert"))
            assertFalse(FrameworkCallContracts.mutationAllowed(m, 2, "nop", "replace"))
        }
    }
    @Test fun rejectsLookalikeOwnersOverloadsAndWrongResultTypes() {
        for (m in listOf(method(owner = "Lapp/TelephonyManager;"), method(parameters = listOf("I")),
            method(returns = "I", resultOpcode = Opcode.MOVE_RESULT),
            method(resultOpcode = Opcode.MOVE_RESULT_WIDE), method(name = "getDeviceId"))) {
            assertFalse(FrameworkCallContracts.boundary(m))
        }
    }
    @Test fun onlyExactVoidCaptureCallsCanBeReplacedWithNop() {
        val m = method(owner = "Landroid/app/Activity;", name = "unregisterScreenCaptureCallback",
            parameters = listOf("Landroid/app/Activity${'$'}ScreenCaptureCallback;"), returns = "V")
        assertEquals(listOf(0), FrameworkCallContracts.captureSites(m))
        assertTrue(FrameworkCallContracts.mutationAllowed(m, 0, "nop", "replace"))
        assertFalse(FrameworkCallContracts.mutationAllowed(m, 1, "nop", "replace"))
        assertFalse(FrameworkCallContracts.mutationAllowed(m, 0, "return-void", "replace"))
        assertFalse(FrameworkCallContracts.mutationAllowed(m, 0, "nop", "insert"))
        assertTrue(FrameworkCallContracts.captureSites(method(owner = "Lother/Activity;",
            name = "unregisterScreenCaptureCallback", parameters = listOf("Ljava/lang/Object;"), returns = "V")).isEmpty())
    }
}
