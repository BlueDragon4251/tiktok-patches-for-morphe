package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class DownloadPathContractsTest {
    private fun method(clobberBuilder: Boolean = false, liveSuffix: Boolean = false): ImmutableMethod {
        val b = MethodImplementationBuilder(4)
        for (s in listOf("/", "video/mp4")) b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(s)))
        for (name in listOf("exists", "mkdirs")) b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
            ImmutableMethodReference("Ljava/io/File;", name, emptyList(), "Z")))
        for (suffix in listOf("/Camera", "/Camera/")) {
            b.addInstruction(BuilderInstruction21c(Opcode.SGET_OBJECT, 0, ImmutableFieldReference("Landroid/os/Environment;", "DIRECTORY_DCIM", "Ljava/lang/String;")))
            val append = ImmutableMethodReference("Ljava/lang/StringBuilder;", "append", listOf("Ljava/lang/String;"), "Ljava/lang/StringBuilder;")
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 1, 0, 0, 0, 0, append))
            b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(suffix)))
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, if (clobberBuilder) 0 else 1, 0, 0, 0, 0, append))
            if (liveSuffix) b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
        }
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
        return ImmutableMethod("LX/Uri;", "gallery", listOf("Landroid/content/Context;", "Ljava/lang/String;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "Landroid/net/Uri;", 9, emptySet(), emptySet(), b.methodImplementation)
    }
    @Test fun twoBuildersCanBeReplacedIndependentlyWithoutRequiringTheRemovedSuffix() {
        val m = MutableMethod(method())
        val owner = ImmutableClassDef(m.definingClass, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(m))
        assertEquals(DownloadPathContracts.MODE, DownloadPathContracts.mode(m, owner, DownloadPathContracts.ACCEPTED))
        val sites = DownloadPathContracts.sites(m)
        for (site in sites.asReversed()) {
            assertTrue(DownloadPathContracts.mutationAllowed(m, site.index, "replace-block 4\n" + site.code))
            assertFalse(DownloadPathContracts.mutationAllowed(m, site.index, "replace-block 3\n" + site.code))
            assertFalse(DownloadPathContracts.mutationAllowed(m, site.index, "replace-block 4\n" + site.code.replace("v0", "v2")))
            m.replaceNativeBlock(site.index, 4, site.code)
        }
        assertEquals(2, m.implementation!!.instructions.count { (it as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference?.toString()?.contains("getDownloadPath()") == true })
    }
    @Test fun differentBuildersOrALiveCameraTemporaryRefuseReplacement() {
        assertThrows(PatchException::class.java) { DownloadPathContracts.sites(method(clobberBuilder = true)) }
        assertThrows(PatchException::class.java) { DownloadPathContracts.sites(method(liveSuffix = true)) }
    }
    @Test fun blockReplacementPreservesEntryLabelsAndRejectsInteriorEntries() {
        for (interior in listOf(false, true)) {
            val b = MethodImplementationBuilder(2)
            b.addInstruction(BuilderInstruction10t(Opcode.GOTO, b.getLabel("entry")))
            if (!interior) b.addLabel("entry")
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            if (interior) b.addLabel("entry")
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 1, 0))
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            val m = MutableMethod(ImmutableMethod("LX/Block;", "body", emptyList(), "V", 9, emptySet(), emptySet(), b.methodImplementation))
            if (interior) assertThrows(PatchException::class.java) { m.replaceNativeBlock(1, 2, "const/4 v0, 0x1") }
            else {
                val label = m.implementation!!.instructions[1].location.labels.single()
                m.replaceNativeBlock(1, 2, "const/4 v0, 0x1")
                assertEquals(1, label.location.index)
                assertEquals(Opcode.NOP, m.implementation!!.instructions[label.location.index].opcode)
            }
        }
    }
}
