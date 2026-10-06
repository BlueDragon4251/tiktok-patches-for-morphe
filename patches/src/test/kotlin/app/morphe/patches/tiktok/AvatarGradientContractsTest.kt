package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.AvatarGradientContracts
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class AvatarGradientContractsTest {
    private val result = "LX/Gradient;"
    private fun loader(aliasedOutput: Boolean = false) = ImmutableMethod("LX/ConfigLoader;", "load",
        listOf(AvatarGradientContracts.CONFIG, "Landroid/content/Context;").map { ImmutableMethodParameter(it, emptySet(), null) },
        result, 25, emptySet(), emptySet(), MethodImplementationBuilder(10).apply {
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 8, ImmutableFieldReference(AvatarGradientContracts.CONFIG,
                "shaderParam", "Lcom/ss/android/ugc/aweme/story/setting/ui/color/ShaderParam;")))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 1, 2))
            addInstruction(BuilderInstruction22c(Opcode.NEW_ARRAY, 3, 1, ImmutableTypeReference("[F")))
            addInstruction(BuilderInstruction22c(Opcode.NEW_ARRAY, 5, 1, ImmutableTypeReference("[I")))
            val output = if (aliasedOutput) 3 else 0
            addInstruction(BuilderInstruction21c(Opcode.NEW_INSTANCE, output, ImmutableTypeReference(result)))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 3, output, 3, 5, 0, 0,
                ImmutableMethodReference(result, "<init>", listOf("[F", "[I"), "V")))
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, output))
        }.methodImplementation)
    private fun resultClass(swappedStores: Boolean = false): ImmutableClassDef {
        val constructor = ImmutableMethod(result, "<init>", listOf("[F", "[I").map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", 65537, emptySet(), emptySet(), MethodImplementationBuilder(3).apply {
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                    ImmutableMethodReference("Ljava/lang/Object;", "<init>", emptyList(), "V")))
                addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, if (swappedStores) 1 else 2, 0,
                    ImmutableFieldReference(result, "colors", "[I")))
                addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, if (swappedStores) 2 else 1, 0,
                    ImmutableFieldReference(result, "positions", "[F")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
        return ImmutableClassDef(result, 17, "Ljava/lang/Object;", emptyList(), null, emptySet(),
            listOf(ImmutableField(result, "colors", "[I", 17, null, emptySet(), emptySet()),
                ImmutableField(result, "positions", "[F", 17, null, emptySet(), emptySet())), listOf(constructor))
    }
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(m.definingClass, 17, "Ljava/lang/Object;",
        emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun theGuardUsesActualConstructorArraysAndOnlyItsNativeAllocationSite() {
        val m = loader()
        assertTrue(AvatarGradientContracts.boundary(m, owner(m)) { resultClass() })
        val site = AvatarGradientContracts.site(m)
        assertEquals(4, site.index)
        assertTrue(site.code.contains("{v5, v3}"))
        assertTrue(AvatarGradientContracts.mutationAllowed(m, site.index, site.code))
        assertFalse(AvatarGradientContracts.mutationAllowed(m, site.index + 1, site.code))
        assertFalse(AvatarGradientContracts.mutationAllowed(m, site.index, site.code.replace("v5, v3", "v3, v5")))
    }
    @Test fun swappedFieldStoresAndAliasedAllocationRefuseMutation() {
        val m = loader()
        assertFalse(AvatarGradientContracts.boundary(m, owner(m)) { resultClass(true) })
        val aliased = loader(true)
        assertFalse(AvatarGradientContracts.boundary(aliased, owner(aliased)) { resultClass() })
        assertFalse(AvatarGradientContracts.boundary(m, owner(m)) { null })
    }
}
