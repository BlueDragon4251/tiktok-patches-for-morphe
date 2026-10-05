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

class ExternalBrowserContractsTest {
    private val type = "Lcom/bytedance/hybrid/spark/page/SparkActivity;"
    private fun activity(clobberReceiver: Boolean = false): ImmutableMethod = ImmutableMethod(type, "onCreate",
        listOf(ImmutableMethodParameter("Landroid/os/Bundle;", emptySet(), null)), "V", 17, emptySet(), emptySet(),
        MethodImplementationBuilder(4).apply {
            addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("SparkContextContainerId")))
            if (clobberReceiver) addInstruction(BuilderInstruction11n(Opcode.CONST_4, 2, 0))
            addInstruction(BuilderInstruction3rc(Opcode.INVOKE_SUPER_RANGE, 2, 2,
                ImmutableMethodReference("LX/Activity;", "onCreate", listOf("Landroid/os/Bundle;"), "V")))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(type, 1, "LX/Activity;", emptyList(), null, emptySet(), emptyList(), listOf(m))
    private fun resolve(t: String) = if (t == "LX/Activity;") ImmutableClassDef(t, 1, "Landroid/app/Activity;", emptyList(), null, emptySet(), emptyList(), emptyList()) else null
    @Test fun theActivityGateRunsAfterItsRealSuperCallAndUsesProvenScratch() {
        val m = activity()
        val site = ExternalBrowserContracts.site(m, owner(m), ::resolve)
        assertEquals(2, site.index)
        assertEquals(0, site.register)
        assertTrue(ExternalBrowserContracts.mutationAllowed(m, owner(m), site.index, site.code, ::resolve))
        assertFalse(ExternalBrowserContracts.mutationAllowed(m, owner(m), 0, site.code, ::resolve))
        assertFalse(ExternalBrowserContracts.mutationAllowed(m, owner(m), site.index, site.code.replace("move-result v0", "move-result p0"), ::resolve))
        assertFalse(ExternalBrowserContracts.mutationAllowed(m, owner(m), site.index, site.code.replace("if-eqz", "if-nez"), ::resolve))
    }
    @Test fun anUnprovenActivityOrReusedReceiverRefusesTheGate() {
        val m = activity()
        assertThrows(PatchException::class.java) { ExternalBrowserContracts.site(m, owner(m)) { null } }
        val clobbered = activity(true)
        assertThrows(PatchException::class.java) { ExternalBrowserContracts.site(clobbered, owner(clobbered), ::resolve) }
    }
}
