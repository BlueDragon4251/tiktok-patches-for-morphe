package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class PlayerFrameContractsTest {
    private fun frame(wrongReceiver: Boolean = false, wrongInput: Boolean = false): ImmutableMethod = ImmutableMethod(PlayerFrameContracts.PLAYER,
        "onRenderFirstFrame", listOf(ImmutableMethodParameter("LX/Frame;", emptySet(), null)), "V", 17, emptySet(), emptySet(),
        MethodImplementationBuilder(5).apply {
            addInstruction(BuilderInstruction21c(Opcode.NEW_INSTANCE, 1, ImmutableTypeReference("LX/FrameTask;")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 3, 1, if (wrongReceiver) 0 else 3, if (wrongInput) 0 else 4, 0, 0,
                ImmutableMethodReference("LX/FrameTask;", "<init>", listOf(PlayerFrameContracts.PLAYER, "LX/Frame;"), "V")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, 0, 1, 0, 0, 0,
                ImmutableMethodReference("LX/Bridge;", "schedule", listOf("Landroid/view/Choreographer;", "Ljava/lang/Runnable;"), "V")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
                ImmutableMethodReference("LX/FrameTask;", "run", emptyList(), "V")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(PlayerFrameContracts.PLAYER, 1,
        "Lcom/ss/android/ugc/aweme/feed/controller/BaseController;", listOf("Lcom/ss/android/ugc/aweme/player/sdk/api/OnUIPlayListener;"),
        null, emptySet(), emptyList(), listOf(m))
    private fun resolve(t: String) = if (t == "LX/FrameTask;") ImmutableClassDef(t, 1, "Ljava/lang/Object;",
        listOf("Ljava/lang/Runnable;"), null, emptySet(), emptyList(), emptyList()) else null
    @Test fun firstFrameUsesTheActualReceiverAndParameterInItsRunnable() {
        val m = frame()
        assertTrue(PlayerFrameContracts.boundary(m, owner(m), ::resolve))
        assertEquals(PlayerFrameContracts.MODE, PlayerFrameContracts.mode(m, owner(m), PlayerFrameContracts.ACCEPTED, ::resolve))
        assertNull(PlayerFrameContracts.mode(m, owner(m), "LX/Unreviewed;->call()V", ::resolve))
    }
    @Test fun untypedRunnableOrChangedReceiverAndInputAreRejected() {
        val m = frame()
        assertFalse(PlayerFrameContracts.boundary(m, owner(m)) { null })
        for (changed in listOf(frame(wrongReceiver = true), frame(wrongInput = true)))
            assertFalse(PlayerFrameContracts.boundary(changed, owner(changed), ::resolve))
    }
}
