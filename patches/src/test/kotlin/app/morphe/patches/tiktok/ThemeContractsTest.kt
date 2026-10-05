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

class ThemeContractsTest {
    private fun styled(resource: Int = 2131100553, changedDefault: Boolean = false): ImmutableMethod = ImmutableMethod("LX/Tux;", "styled",
        listOf("I", "Landroid/content/Context;", "[I").map { ImmutableMethodParameter(it, emptySet(), null) }, "Ljava/lang/Integer;", 25,
        emptySet(), emptySet(), MethodImplementationBuilder(7).apply {
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 3, 0))
            addInstruction(BuilderInstruction31i(Opcode.CONST, 1, resource))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 5, 5, 3, 6, 1, 0,
                ImmutableMethodReference("Landroid/content/Context;", "obtainStyledAttributes", listOf("Landroid/util/AttributeSet;", "[I", "I", "I"), "Landroid/content/res/TypedArray;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 2))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 1, if (changedDefault) 2 else 1))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 2, 4, 1, 0, 0,
                ImmutableMethodReference("Landroid/content/res/TypedArray;", "getColor", listOf("I", "I"), "I")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 2, 0, 0, 0, 0,
                ImmutableMethodReference("Landroid/content/res/TypedArray;", "recycle", emptyList(), "V")))
            addInstruction(BuilderInstruction22t(Opcode.IF_EQ, 0, 1, getLabel("return")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference("Ljava/lang/Integer;", "valueOf", listOf("I"), "Ljava/lang/Integer;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 3))
            addLabel("return")
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 3))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(m.definingClass, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(m))
    private fun reviewed() = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030, "sha", emptyMap(), emptyMap(),
        mapOf(ThemeContracts.STYLED to FixtureContracts.portableSignature(styled())), emptyMap())
    @Test fun onlyTheReviewedAndroidStyleAttributeMayRelocate() {
        val m = styled(2131100558)
        assertEquals(ThemeContracts.STYLED_MODE, ThemeContracts.mode(m, owner(m), ThemeContracts.STYLED, reviewed()) { null })
        for (changed in listOf(styled(42), styled(2131100559), styled(changedDefault = true)))
            assertNull(ThemeContracts.mode(changed, owner(changed), ThemeContracts.STYLED, reviewed()) { null })
    }
    @Test fun theStyledGatePreservesInputsAndRequiresAReviewedPresetAndScratch() {
        val m = styled()
        val site = ThemeContracts.tuxEntrySite(m, "styled", "oled_black")
        assertTrue(site.code.contains("{p0, p1, p2, v0}"))
        assertTrue(ThemeContracts.mutationAllowed(m, 0, site.code))
        assertFalse(ThemeContracts.mutationAllowed(m, 0, site.code.replace("v0", "p0")))
        assertFalse(ThemeContracts.mutationAllowed(m, 1, site.code))
        assertThrows(PatchException::class.java) { ThemeContracts.tuxEntrySite(m, "styled", "unreviewed") }
    }
    private val composer = "LX/Composer;"
    private val palette = "LX/Palette;"
    private fun provider(group: Boolean, wrongReceiver: Boolean = false): ImmutableMethod = ImmutableMethod("LX/Provider;", "palette",
        listOf(ImmutableMethodParameter(composer, emptySet(), null)), palette, 25, emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
            if (group) {
                addInstruction(BuilderInstruction31i(Opcode.CONST, 0, 1840675359))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 2, 1, 0, 0, 0, 0, ImmutableMethodReference(composer, "begin", listOf("I"), "V")))
            }
            addInstruction(BuilderInstruction21c(Opcode.SGET_OBJECT, 0, ImmutableFieldReference("LX/Provider;", "local", "LX/Local;")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 2, if (wrongReceiver) 0 else 1, 0, 0, 0, 0,
                ImmutableMethodReference(composer, "consume", listOf("LX/BaseLocal;"), "Ljava/lang/Object;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction21c(Opcode.CHECK_CAST, 0, ImmutableTypeReference(palette)))
            if (group) addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 1, 1, 0, 0, 0, 0, ImmutableMethodReference(composer, "end", emptyList(), "V")))
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
        }.methodImplementation)
    private fun resolve(t: String): ImmutableClassDef? = when (t) {
        composer -> ImmutableClassDef(t, 1537, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), emptyList())
        "LX/Local;" -> ImmutableClassDef(t, 1, "LX/BaseLocal;", emptyList(), null, emptySet(), emptyList(), emptyList())
        palette -> ImmutableClassDef(t, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), (0 until 220).map {
            ImmutableField(t, "color$it", "J", 1, null, emptySet(), emptySet()) }, emptyList())
        else -> null
    }
    @Test fun bothNativeCompositionLocalShapesReturnTheActualTypedPalette() {
        for (group in listOf(false, true)) {
            val m = provider(group)
            assertTrue(ThemeContracts.paletteProviderBoundary(m, owner(m), ::resolve))
            val site = ThemeContracts.paletteSite(m)
            assertTrue(ThemeContracts.paletteMutationAllowed(m, site.index, site.code))
            assertFalse(ThemeContracts.paletteMutationAllowed(m, site.index, site.code.replace("v0", "p0")))
        }
    }
    @Test fun aWrongComposerOrUnprovenColorTableRefusesTheProvider() {
        val m = provider(true)
        assertFalse(ThemeContracts.paletteProviderBoundary(m, owner(m)) { null })
        val changed = provider(true, wrongReceiver = true)
        assertFalse(ThemeContracts.paletteProviderBoundary(changed, owner(changed), ::resolve))
    }
}
