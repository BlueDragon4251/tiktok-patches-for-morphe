package app.morphe.patches.tiktok

import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FingerprintCardinalityTest {
    // Synthetic context; reflection is confined to these internal Morphe test constructors.
    private fun context(vararg names: String): BytecodePatchContext {
        val classes = names.map { name ->
            val b = MethodImplementationBuilder(1)
            b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("stable-hook")))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            val m = ImmutableMethod(name, "renamed", emptyList(), "Ljava/lang/String;", AccessFlags.STATIC.value, emptySet(), emptySet(), b.methodImplementation)
            ImmutableClassDef(name, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(m))
        }.toSet()
        val metadata = PackageMetadata::class.java.getDeclaredConstructor(String::class.java, String::class.java, String::class.java).newInstance("test.fixture", "1", "1")
        val ctx = BytecodePatchContext::class.java.getDeclaredConstructor(PatcherConfig::class.java, PackageMetadata::class.java).newInstance(PatcherConfig(File("unused-synthetic.apk")), metadata)
        val patchClasses = Class.forName("app.morphe.patcher.util.PatchClasses").getDeclaredConstructor(Set::class.java).newInstance(classes)
        BytecodePatchContext::class.java.getDeclaredField("patchClasses").apply { isAccessible = true }.set(ctx, patchClasses)
        return ctx
    }
    @Test fun missingAndAmbiguousFingerprintsFailIncludingOptionalAmbiguity() {
        val fp = TikTokFingerprint(returnType = "Ljava/lang/String;", exactStrings = listOf("stable-hook"))
        with(context()) { assertNull(fp.uniqueMatchOrNull()); assertThrows(PatchException::class.java) { fp.uniqueMatch() } }
        with(context("LX/A;", "LX/B;")) {
            assertThrows(PatchException::class.java) { fp.uniqueMatch() }
            assertThrows(PatchException::class.java) { fp.uniqueMatchOrNull() }
        }
    }
    @Test fun resolvesMovedOwnerWithoutReusingThePreviousApkCache() {
        val fp = TikTokFingerprint(returnType = "Ljava/lang/String;", exactStrings = listOf("stable-hook"))
        with(context("LX/Old;")) { assertEquals("LX/Old;", fp.uniqueOriginalMethod.definingClass) }
        with(context("LX/New;")) { assertEquals("LX/New;", fp.uniqueOriginalMethod.definingClass) }
        with(context()) { assertThrows(PatchException::class.java) { fp.uniqueMatch() } }
    }
}
