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

class PromotionContractsTest {
    private fun method(duplicate: Boolean = false, wrongRegister: Boolean = false): ImmutableMethod {
        val b = MethodImplementationBuilder(8)
        b.addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 1, 3, ImmutableFieldReference("Lcom/bytedance/touchpoint/api/model/TouchPoint;", "data", "Ljava/lang/String;")))
        b.addInstruction(BuilderInstruction21c(Opcode.CONST_CLASS, 0, ImmutableTypeReference("Lcom/bytedance/touchpoint/data/parser/notify/PendantViewModel;")))
        repeat(if (duplicate) 2 else 1) {
            b.addInstruction(BuilderInstruction21c(Opcode.CONST_CLASS, 0, ImmutableTypeReference("Lcom/bytedance/touchpoint/api/model/NormalPendant;")))
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 2, 1, 0, 0, 0, 0,
                ImmutableMethodReference("LX/Parser;", "parse", listOf("Ljava/lang/String;", "Ljava/lang/Class;"), "Ljava/lang/Object;")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 4))
            b.addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 1, if (wrongRegister) 2 else 4))
            b.addInstruction(BuilderInstruction21c(Opcode.CHECK_CAST, 1, ImmutableTypeReference("Lcom/bytedance/touchpoint/api/model/NormalPendant;")))
        }
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        return ImmutableMethod("LX/Pendant;", "parse", listOf("Ljava/util/HashMap;", "Ljava/util/List;", "Z").map { ImmutableMethodParameter(it, emptySet(), null) }, "V", 17, emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun onlyTheActualTypedModelResultCanBeFiltered() {
        val m = method()
        val site = PromotionContracts.site(m)
        assertEquals(5, site.index)
        assertEquals(4, site.register)
        assertTrue(PromotionContracts.mutationAllowed(m, site.index, site.code))
        assertFalse(PromotionContracts.mutationAllowed(m, site.index - 1, site.code))
        assertFalse(PromotionContracts.mutationAllowed(m, site.index, site.code.replace("v4", "v3")))
    }
    @Test fun ambiguousParsesAndAnUnrelatedCastRemainRejected() {
        assertThrows(PatchException::class.java) { PromotionContracts.site(method(duplicate = true)) }
        assertThrows(PatchException::class.java) { PromotionContracts.site(method(wrongRegister = true)) }
    }
}
