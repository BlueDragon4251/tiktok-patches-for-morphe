package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.ThemeRenderContracts
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class ThemeRenderContractsTest {
    private fun app(wrong: Boolean = false): ImmutableMethod = ImmutableMethod(ThemeRenderContracts.APP,
        "attachBaseContext", listOf(ImmutableMethodParameter("Landroid/content/Context;", emptySet(), null)),
        "V", 17, emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_SUPER, 2, 0, if (wrong) 0 else 1, 0, 0, 0,
                ImmutableMethodReference("Landroid/app/Application;", "attachBaseContext", listOf("Landroid/content/Context;"), "V")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }.methodImplementation)
    @Test fun startupRequiresOriginalApplicationAndActualContextParameter() {
        val method = app()
        val owner = ImmutableClassDef(ThemeRenderContracts.APP, 17, "Landroid/app/Application;", emptyList(), null,
            emptySet(), emptyList(), listOf(method))
        assertTrue(ThemeRenderContracts.appBoundary(method, owner))
        assertFalse(ThemeRenderContracts.appBoundary(app(true), owner))
        val site = ThemeRenderContracts.site(method)
        assertTrue(ThemeRenderContracts.mutationAllowed(method, 0, site.code))
        assertFalse(ThemeRenderContracts.mutationAllowed(method, 1, site.code))
        assertFalse(ThemeRenderContracts.mutationAllowed(method, 0, site.code.replace("p1 .. p1", "p0 .. p0")))
    }
    private fun caption(wrong: Boolean = false): Pair<ImmutableMethod, ImmutableClassDef> {
        val owner = "LX/NativeCaption;"
        val field = ImmutableFieldReference(owner, "layout", "Landroid/text/Layout;")
        val method = ImmutableMethod(owner, "onDraw", listOf(ImmutableMethodParameter("Landroid/graphics/Canvas;", emptySet(), null)), "V", 17,
            emptySet(), emptySet(), MethodImplementationBuilder(5).apply {
                fun call(opcode: Opcode, ref: ImmutableMethodReference, vararg regs: Int) = addInstruction(
                    BuilderInstruction35c(opcode, regs.size, regs.getOrElse(0){0}, regs.getOrElse(1){0}, regs.getOrElse(2){0}, 0, 0, ref))
                call(Opcode.INVOKE_SUPER, ImmutableMethodReference("Landroid/view/View;", "onDraw", listOf("Landroid/graphics/Canvas;"), "V"), 3, 4)
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/graphics/Canvas;", "save", emptyList(), "I"), 4)
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 2, 3, field))
                addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 2, getLabel("done")))
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/view/View;", "getPaddingLeft", emptyList(), "I"), 3)
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0)); addInstruction(BuilderInstruction12x(Opcode.INT_TO_FLOAT, 1, 0))
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/view/View;", "getPaddingTop", emptyList(), "I"), 3)
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0)); addInstruction(BuilderInstruction12x(Opcode.INT_TO_FLOAT, 0, 0))
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/graphics/Canvas;", "translate", listOf("F", "F"), "V"), 4, 1, 0)
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/text/Layout;", "draw", listOf("Landroid/graphics/Canvas;"), "V"), if(wrong) 1 else 2, 4)
                call(Opcode.INVOKE_VIRTUAL, ImmutableMethodReference("Landroid/graphics/Canvas;", "restore", emptyList(), "V"), 4)
                addLabel("done"); addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
        val getter = ImmutableMethod(owner, "getTextLayout", emptyList(), "Landroid/text/Layout;", 17,
            emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 1, field)); addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            }.methodImplementation)
        return method to ImmutableClassDef(owner, 17, "Landroid/view/View;", emptyList(), null, emptySet(), emptyList(), listOf(method, getter))
    }
    @Test fun captionHookRequiresNativeLayoutDrawAndCannotUseWrongRegisterOrTiming() {
        val (method, owner) = caption()
        assertTrue(ThemeRenderContracts.drawBoundary(method, owner))
        val (wrong, wrongOwner) = caption(true)
        assertFalse(ThemeRenderContracts.drawBoundary(wrong, wrongOwner))
        val site = ThemeRenderContracts.site(method)
        assertEquals(11, site.index)
        assertTrue(ThemeRenderContracts.mutationAllowed(method, 11, site.code))
        assertFalse(ThemeRenderContracts.mutationAllowed(method, 12, site.code))
        assertFalse(ThemeRenderContracts.mutationAllowed(method, 11, site.code.replace("v3, v2", "v2, v3")))
    }
}
