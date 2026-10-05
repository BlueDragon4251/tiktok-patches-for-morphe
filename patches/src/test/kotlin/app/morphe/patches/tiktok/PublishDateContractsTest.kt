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

class PublishDateContractsTest {
    private val type = "Lcom/ss/android/ugc/aweme/feed/assem/videoauthorinfo/VideoAuthorInfoVM;"
    private fun method(count: Int = 5, duplicateMarker: Boolean = false): ImmutableMethod {
        val b = MethodImplementationBuilder(7)
        b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference("v3")))
        if (duplicateMarker) b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference("v3")))
        repeat(count) {
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 1, 0, 0, 0, 0,
                ImmutableMethodReference("LX/Gate;", "LIZ", listOf("Ljava/lang/String;"), "Z")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
        }
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 2, 0, 0, 0, 0,
            ImmutableMethodReference("Lcom/ss/android/ugc/aweme/feed/model/Aweme;", "getCreateTime", emptyList(), "J")))
        b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_WIDE, 0))
        b.addInstruction(BuilderInstruction23x(Opcode.CMP_LONG, 0, 0, 2))
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 5))
        return ImmutableMethod(type, "paramSync2StateAccept", listOf("LX/State;",
            "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "LX/State;", 17, emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun visibilityResultsKeepTheirActualRegistersAndExactInsertionSites() {
        val m = method()
        val owner = ImmutableClassDef(type, 17, "Lcom/ss/android/ugc/aweme/feed/assem/base/FeedBaseViewModel;",
            emptyList(), null, emptySet(), emptyList(), listOf(m))
        assertEquals(PublishDateContracts.MODE, PublishDateContracts.mode(m, owner, PublishDateContracts.ACCEPTED))
        val sites = PublishDateContracts.gates(m)
        assertEquals(5, sites.size)
        for (site in sites) {
            assertTrue(PublishDateContracts.mutationAllowed(m, site.index, site.code))
            assertFalse(PublishDateContracts.mutationAllowed(m, site.index - 1, site.code))
            assertFalse(PublishDateContracts.mutationAllowed(m, site.index, site.code.replace("v0", "v1")))
        }
    }
    @Test fun missingExtraAndAmbiguousGateRegionsCannotBeEdited() {
        for (m in listOf(method(4), method(6), method(duplicateMarker = true)))
            assertThrows(PatchException::class.java) { PublishDateContracts.gates(m) }
    }
}
