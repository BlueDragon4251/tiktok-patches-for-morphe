package app.morphe.patches.tiktok
import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.*
import org.junit.Test
class SubstringMarkersTest {
    @Test fun compatibilityMatcherRequiresEveryOriginalSubstringAndDoesNotAcceptAMissingMarker() {
        val method = ImmutableMethod("LX/Native;", "run", emptyList(), "V", 17, emptySet(), emptySet(),
            MethodImplementationBuilder(1).apply {
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("prefix Feed0VVManager@generated suffix")))
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("popCaptchaV2 - riskInfo = details")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
        assertTrue(TikTokFingerprint.matchesSubstringMarkers(method, listOf("Feed0VVManager@", "popCaptchaV2 - riskInfo =")))
        assertFalse(TikTokFingerprint.matchesSubstringMarkers(method, listOf("Feed0VVManager@", "missing semantic marker")))
    }
}
