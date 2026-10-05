package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.*
import org.junit.Test

class StickerSourceContractsTest {
    private val binderParams = listOf("LX/Preview;", "Z", "Ljava/lang/String;", "Ljava/util/Map;")
    private fun binder(owner: String = "LX/Binder;") = ImmutableMethod(owner, "LIZ",
        binderParams.map { ImmutableMethodParameter(it, emptySet(), null) }, "V", 17, emptySet(), emptySet(),
        MethodImplementationBuilder(5).apply { addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID)) }.methodImplementation)
    private fun source(clobberItem: Boolean = false): ImmutableMethod {
        val b = MethodImplementationBuilder(18)
        for ((name, type) in listOf("getSetId" to "Ljava/lang/Long;",
            "getStaticUrl" to "Lcom/ss/android/ugc/aweme/im/common/model/StickerUrlStruct;"))
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 2, 0, 0, 0, 0,
                ImmutableMethodReference("Lcom/ss/android/ugc/aweme/im/common/model/SetSticker;", name, emptyList(), type)))
        if (clobberItem) b.addInstruction(BuilderInstruction21s(Opcode.CONST_16, 10, 0))
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 5, 3, 4, 5, 6, 7,
            ImmutableMethodReference("LX/Binder;", "LIZ", binderParams, "V")))
        b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        val params = listOf("Ljava/lang/String;", "Lcom/ss/android/ugc/aweme/im/common/model/StickerItem;",
            "Landroid/view/View;", "Z", "Ljava/lang/String;", "Ljava/util/Map;",
            "Lkotlin/jvm/functions/Function0;", "Lkotlin/jvm/functions/Function0;", "Lkotlin/jvm/functions/Function0;")
        return ImmutableMethod("LX/Source;", "LJ", params.map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", 17, emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun associationUsesTheActualPreviewAndPreservedStickerParameter() {
        val m = source(); val site = StickerSourceContracts.sites(m, binder()).single()
        assertEquals(2, site.index)
        assertTrue(site.code.contains("v0, v4"))
        assertTrue(site.code.contains("v1, p2"))
        assertTrue(StickerSourceContracts.mutationAllowed(m, binder(), 2, site.code))
        assertFalse(StickerSourceContracts.mutationAllowed(m, binder(), 3, site.code))
        assertFalse(StickerSourceContracts.mutationAllowed(m, binder(), 2, site.code.replace("p2", "p3")))
        assertFalse(StickerSourceContracts.mutationAllowed(m, binder(), 2, site.code.replace("v0, v4", "v0, v5")))
    }
    @Test fun wrongBinderAndReusedStickerInputCannotAcquireAnAssociationSite() {
        assertFalse(StickerSourceContracts.boundary(source(), binder("LX/Other;")))
        assertFalse(StickerSourceContracts.boundary(source(true), binder()))
    }
}
