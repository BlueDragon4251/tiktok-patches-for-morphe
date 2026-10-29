package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class ThemeSurfaceContractsTest {
    private val view = ThemeSurfaceContracts.VIEW
    private fun method(owner: String, name: String, params: List<String>, result: String, body: MethodImplementationBuilder) =
        ImmutableMethod(owner, name, params.map { ImmutableMethodParameter(it, emptySet(), null) }, result, 17,
            emptySet(), emptySet(), body.methodImplementation)
    private fun owner(m: ImmutableMethod, parent: String) = ImmutableClassDef(m.definingClass, 1, parent, emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun coexistingOwnersKeepTheReviewedBaselineInsteadOfCreatingTwoCandidates() {
        fun native(type: String) = ImmutableClassDef(type, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), emptyList())
        val owners = listOf(ThemeSurfaceContracts.OLD_INBOX, ThemeSurfaceContracts.INBOX, ThemeSurfaceContracts.OLD_CHAT, ThemeSurfaceContracts.CHAT).associateWith(::native)
        assertEquals(ThemeSurfaceContracts.OLD_INBOX, ThemeSurfaceContracts.inboxOwner(owners::get))
        assertEquals(ThemeSurfaceContracts.OLD_CHAT, ThemeSurfaceContracts.chatOwner(owners::get))
        val current = owners.filterKeys { it in setOf(ThemeSurfaceContracts.INBOX, ThemeSurfaceContracts.CHAT) }
        assertEquals(ThemeSurfaceContracts.INBOX, ThemeSurfaceContracts.inboxOwner(current::get))
        assertEquals(ThemeSurfaceContracts.CHAT, ThemeSurfaceContracts.chatOwner(current::get))
    }
    @Test fun aRootRequiresTheReviewedSurfaceAndActualFragmentHierarchy() {
        fun root(type: String) = method(type, "onCreateView", listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"), view,
            MethodImplementationBuilder(5).apply { addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0)); addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0)) })
        val m = root(ThemeSurfaceContracts.SETTINGS)
        assertTrue(ThemeSurfaceContracts.rootBoundary(m, owner(m, "Landroidx/fragment/app/Fragment;")) { if (it == m.definingClass) owner(m, "Landroidx/fragment/app/Fragment;") else null })
        assertFalse(ThemeSurfaceContracts.rootBoundary(m, owner(m, "Ljava/lang/Object;")) { null })
        val unknown = root("Lother/Fragment;")
        assertFalse(ThemeSurfaceContracts.rootBoundary(unknown, owner(unknown, "Landroidx/fragment/app/Fragment;")) { null })
        val site = ThemeSurfaceContracts.rootSites(m).single()
        assertTrue(ThemeSurfaceContracts.mutationAllowed(ThemeSurfaceContracts.ROOT_MODE, m, owner(m, "Landroidx/fragment/app/Fragment;"), site.index, site.code) { null })
        assertFalse(ThemeSurfaceContracts.mutationAllowed(ThemeSurfaceContracts.ROOT_MODE, m, owner(m, "Landroidx/fragment/app/Fragment;"), site.index, site.code.replace("v0", "v1")) { null })
    }
    @Test fun settingsHooksUseOnlyAnAliasThatSurvivesEveryReturnPath() {
        fun callback(overwrite: Boolean) = method(ThemeSurfaceContracts.SETTINGS, "onViewCreated", listOf(view, "Landroid/os/Bundle;"), "V",
            MethodImplementationBuilder(5).apply {
                addInstruction(BuilderInstruction12x(Opcode.MOVE_OBJECT, 0, 3))
                addInstruction(BuilderInstruction11n(Opcode.CONST_4, 3, 0))
                if (overwrite) addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            })
        assertTrue(ThemeSurfaceContracts.settingsViewSites(callback(false)).single().code.contains("{v0 .. v0}"))
        assertThrows(PatchException::class.java) { ThemeSurfaceContracts.settingsViewSites(callback(true)) }
    }
    @Test fun anAfterWriterHookDoesNotCaptureAnEarlyReturnTarget() {
        val builder = MethodImplementationBuilder(3).apply {
            addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 1, getLabel("end")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 1, 2, 0, 0, 0, ImmutableMethodReference(view, "setBackgroundColor", listOf("I"), "V")))
            addLabel("end")
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val m = MutableMethod(method("LX/Writer;", "write", emptyList(), "V", builder))
        m.insertAfterNative(1, "invoke-static {v1}, Lapp/morphe/extension/tiktok/theme/ThemeNativeTargets;->navigationDivider($view)V")
        val insns = m.implementation!!.instructions
        val target = (insns[0] as OffsetInstruction).codeOffset
        var address = 0
        val reached = insns.single { i -> val here = address; address += i.codeUnits; here == target }
        assertEquals(Opcode.RETURN_VOID, reached.opcode)
        assertEquals(Opcode.INVOKE_STATIC, insns[2].opcode)
    }
    @Test fun anAfterProducerHookCannotSplitAnObjectResultFromItsInvocation() {
        val m = MutableMethod(method("LX/Writer;", "read", emptyList(), "V", MethodImplementationBuilder(2).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0, ImmutableMethodReference(view, "getContext", emptyList(), "Landroid/content/Context;")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }))
        assertThrows(PatchException::class.java) { m.insertAfterNative(0, "nop") }
        assertEquals(3, m.implementation!!.instructions.size)
    }
    @Test fun nativeHeaderRequiresTheExactWriterAndRejectsWrongReceiverAndTiming() {
        fun writer(wrong: Boolean) = method(ThemeSurfaceContracts.HEADER, "setNavBackground", listOf("I"), "V",
            MethodImplementationBuilder(3).apply {
                addInstruction(BuilderInstruction22c(Opcode.IPUT, 2, 1, ImmutableFieldReference(ThemeSurfaceContracts.HEADER, "color", "I")))
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 1, ImmutableFieldReference(ThemeSurfaceContracts.HEADER, "background", "LX/HeaderBackground;")))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, if (wrong) 1 else 0, 2, 0, 0, 0,
                    ImmutableMethodReference(view, "setBackgroundColor", listOf("I"), "V")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            })
        val m = writer(false); val cls = owner(m, "Landroid/widget/FrameLayout;")
        assertTrue(ThemeSurfaceContracts.headerBoundary(m, cls))
        assertFalse(ThemeSurfaceContracts.headerBoundary(writer(true), cls))
        val site = ThemeSurfaceContracts.headerSite(m)
        assertTrue(ThemeSurfaceContracts.mutationAllowed(ThemeSurfaceContracts.HEADER_MODE, m, cls, site.index, site.code) { null })
        assertFalse(ThemeSurfaceContracts.mutationAllowed(ThemeSurfaceContracts.HEADER_MODE, m, cls, site.index + 1, site.code) { null })
        assertFalse(ThemeSurfaceContracts.mutationAllowed(ThemeSurfaceContracts.HEADER_MODE, m, cls, site.index, site.code.replace("v1", "v2")) { null })
    }
    @Test fun systemNotificationRootRolesRemainDistinctFromPrivateChatRoots() {
        for (type in listOf(ThemeSurfaceContracts.SYSTEM_CONTAINER, ThemeSurfaceContracts.SYSTEM_PAGE)) {
            val m = method(type, "onCreateView", listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"), view,
                MethodImplementationBuilder(5).apply { addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0)); addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0)) })
            val cls = owner(m, "Landroidx/fragment/app/Fragment;")
            assertTrue(ThemeSurfaceContracts.rootBoundary(m, cls) { if (it == type) cls else null })
            assertTrue(ThemeSurfaceContracts.rootSites(m).single().code.contains("->systemNotifications("))
            assertFalse(ThemeSurfaceContracts.rootSites(m).single().code.contains("->chat("))
        }
    }

}
