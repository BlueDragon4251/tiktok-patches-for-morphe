package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import org.junit.Assert.*
import org.junit.Test

class ReviewedMethodScopesTest {
    private val accepted = "LX/02z2;->LIZ(ILjava/lang/String;ZZ)Z"
    private fun getter(owner: String, registers: Int = 6): ImmutableMethod = ImmutableMethod(
        owner, "LIZ", listOf("I", "Ljava/lang/String;", "Z", "Z").map {
            ImmutableMethodParameter(it, emptySet(), null)
        }, "Z", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
        MethodImplementationBuilder(registers).apply {
            addInstruction(BuilderInstruction21c(Opcode.SGET_BOOLEAN, 0,
                ImmutableFieldReference(owner, "LIZ", "Z")))
            addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        }.methodImplementation)
    private fun owner(method: ImmutableMethod, fieldFlags: Int = 9,
                      superclass: String = "Ljava/lang/Object;") = ImmutableClassDef(
        method.definingClass, AccessFlags.PUBLIC.value, superclass, emptyList(), null, emptySet(),
        listOf(ImmutableField(method.definingClass, "LIZ", "Z", fieldFlags, null, emptySet(), emptySet())),
        listOf(method))
    private fun reviewed(): FixtureContracts.Reviewed {
        val baseline = getter("LX/02z2;")
        return FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030, "sha",
            emptyMap(), emptyMap(), mapOf(accepted to FixtureContracts.portableSignature(baseline)),
            mapOf(baseline.definingClass to FixtureContracts.portableClassSignature(owner(baseline))),
            scopedMethods = mapOf(accepted to FixtureContracts.portableReturnScopeSignature(owner(baseline), baseline)))
    }
    @Test fun explicitGetterRequiresTheEntireReviewedBodyAndReferencedScope() {
        val method = getter("LX/02yB;")
        assertEquals(listOf(accepted), ReviewedMethodScopes.candidates(owner(method), method, reviewed()))
        for ((changedMethod, changedOwner) in listOf(
            getter("LX/02yB;", 7).let { it to owner(it) },
            method to owner(method, fieldFlags = 10),
            method to owner(method, superclass = "Ljava/lang/Number;"))) {
            assertTrue(ReviewedMethodScopes.candidates(changedOwner, changedMethod, reviewed()).isEmpty())
        }
    }
    @Test fun anIdenticalUnreviewedStableOwnerDoesNotGainAnAlias() {
        val method = getter("Lother/Getter;")
        assertTrue(ReviewedMethodScopes.candidates(owner(method), method, reviewed()).isEmpty())
    }
    @Test fun storedScopesContainExactlyTheReviewedHooks() {
        assertEquals(FixtureContracts.methodScopedHooks(), FixtureContracts.load("46.7.3").scopedMethods.keys)
    }
}
