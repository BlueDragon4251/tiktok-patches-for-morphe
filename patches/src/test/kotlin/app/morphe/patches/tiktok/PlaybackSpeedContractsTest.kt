package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackSpeedContractsTest {
    private fun setter(wrongInput: Boolean = false, wrongManager: Boolean = false, duplicateApplication: Boolean = false): ImmutableMethod =
        ImmutableMethod(PlayerFrameContracts.PLAYER, "renamedSetter", listOf(ImmutableMethodParameter("F", emptySet(), null)),
            "V", 17, emptySet(), emptySet(), MethodImplementationBuilder(5).apply {
                for (marker in listOf("stage", "speed_begin", "action", "click", "begin_speed"))
                    addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(marker)))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 4, 0, 0, 0, 0,
                    ImmutableMethodReference("Ljava/lang/Float;", "valueOf", listOf("F"), "Ljava/lang/Float;")))
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, if (wrongManager) 1 else 3, 0, 0, 0, 0,
                    ImmutableMethodReference(PlayerFrameContracts.PLAYER, "getPlayerManager", emptyList(), "LX/Manager;")))
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
                repeat(if (duplicateApplication) 2 else 1) {
                    addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 2, 0, if (wrongInput) 1 else 4, 0, 0, 0,
                        ImmutableMethodReference("LX/Manager;", "renamedApply", listOf("F"), "V")))
                }
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(PlayerFrameContracts.PLAYER, 1,
        "Lcom/ss/android/ugc/aweme/feed/controller/BaseController;", emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun aRenamedSetterStillAppliesItsOriginalFloatToItsActualManager() {
        val m = setter()
        assertTrue(PlaybackSpeedContracts.setterBoundary(m, owner(m)))
    }
    @Test fun changedInputManagerOrAmbiguousApplicationRefusesTheSetter() {
        for (m in listOf(setter(wrongInput = true), setter(wrongManager = true), setter(duplicateApplication = true)))
            assertFalse(PlaybackSpeedContracts.setterBoundary(m, owner(m)))
    }
}
