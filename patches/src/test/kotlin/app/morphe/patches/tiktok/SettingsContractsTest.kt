package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.SettingsContracts
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.*
import org.junit.Test

class SettingsContractsTest {
    private fun create(protected: Boolean = false, wrongReceiver: Boolean = false, range: Boolean = false,
                       duplicate: Boolean = false, readScratch: Boolean = false): ImmutableMethod {
        val body = MethodImplementationBuilder(20).apply {
            // High p0/p1 are copied to v11/v0, as in the real candidate.
            addInstruction(BuilderInstruction22x(Opcode.MOVE_OBJECT_FROM16, 11, if (wrongReceiver) 17 else 18))
            addInstruction(BuilderInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, 19))
            val ref = ImmutableMethodReference(SettingsContracts.BASE, "onCreate", listOf("Landroid/os/Bundle;"), "V")
            if (range) addInstruction(BuilderInstruction3rc(Opcode.INVOKE_SUPER_RANGE, 18, 2, ref))
            else addInstruction(BuilderInstruction35c(Opcode.INVOKE_SUPER, 2, 11, 0, 0, 0, 0, ref))
            addLabel("start")
            if (readScratch) addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference("Landroid/util/Log;", "value", listOf("Ljava/lang/Object;"), "V")))
            addInstruction(BuilderInstruction31i(Opcode.CONST, 0, 2131886544))
            addLabel("end")
            if (duplicate) addInstruction(BuilderInstruction35c(Opcode.INVOKE_SUPER, 2, 11, 0, 0, 0, 0, ref))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            addLabel("handler")
            addInstruction(BuilderInstruction11x(Opcode.MOVE_EXCEPTION, 1))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            if (protected) addCatch("Ljava/lang/Exception;", getLabel("start"), getLabel("end"), getLabel("handler"))
        }
        return ImmutableMethod(SettingsContracts.ACTIVITY, "onCreate",
            listOf(ImmutableMethodParameter("Landroid/os/Bundle;", emptySet(), null)), "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, emptySet(), emptySet(), body.methodImplementation)
    }

    @Test fun highReceiverAndOverwriteAreDerivedFromTheOriginalMethod() {
        val method = create()
        val site = SettingsContracts.activitySite(method, true)
        assertEquals(3, site.index)
        assertEquals(18, site.receiver)
        assertEquals(0, site.scratch)
        val code = SettingsContracts.activityCode(site, true)
        assertTrue(code.contains("invoke-static/range {v18 .. v18}"))
        assertTrue(SettingsContracts.mutationAllowed(SettingsContracts.CREATE, method, site.index, code))
        assertFalse(SettingsContracts.mutationAllowed(SettingsContracts.CREATE, method, 0, code))
        assertFalse(SettingsContracts.mutationAllowed(SettingsContracts.CREATE, method, site.index,
            code.replace("move-result v0", "move-result v16")))
    }

    @Test fun rangeSuperCallIsSupported() {
        assertEquals(18, SettingsContracts.activitySite(create(range = true), true).receiver)
    }

    @Test fun exceptionRangeRefusesInjectionEvenWithAWriteOnlyNextInstruction() {
        assertNull(SettingsContracts.mode(create(protected = true), "${SettingsContracts.ACTIVITY}->onCreate(Landroid/os/Bundle;)V"))
    }

    @Test fun wrongReceiverDuplicateSuperAndScratchReadAreRejected() {
        for (method in listOf(create(wrongReceiver = true), create(duplicate = true), create(readScratch = true))) {
            assertTrue(runCatching { SettingsContracts.activitySite(method, true) }.isFailure)
        }
    }
}
