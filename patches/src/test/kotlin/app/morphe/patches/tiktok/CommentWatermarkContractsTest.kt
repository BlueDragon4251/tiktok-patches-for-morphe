package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.CommentWatermarkContracts
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.*
import org.junit.Test

class CommentWatermarkContractsTest {
    private fun watermark(marker: String = "[tiktok_logo]", duplicate: Boolean = false,
                          range: Boolean = false): ImmutableMethod {
        val body = MethodImplementationBuilder(8).apply {
            addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 5, ImmutableStringReference(marker)))
            fun invoke(owner: String, name: String, params: List<String>, returns: String, args: List<Int>) {
                val padded = args + List(5 - args.size) { 0 }
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, args.size,
                    padded[0], padded[1], padded[2], padded[3], padded[4],
                    ImmutableMethodReference(owner, name, params, returns)))
            }
            invoke("Landroid/graphics/Bitmap;", "copy", listOf("Landroid/graphics/Bitmap\$Config;", "Z"),
                "Landroid/graphics/Bitmap;", listOf(7, 5, 2))
            invoke("Landroid/graphics/Canvas;", "<init>", listOf("Landroid/graphics/Bitmap;"), "V", listOf(0, 4))
            invoke("Landroid/graphics/Bitmap;", "createScaledBitmap", listOf("Landroid/graphics/Bitmap;", "I", "I", "Z"),
                "Landroid/graphics/Bitmap;", listOf(7, 2, 3, 2))
            invoke("Landroid/text/Layout;", "draw", listOf("Landroid/graphics/Canvas;"), "V", listOf(5, 0))
            invoke("Landroid/graphics/Canvas;", "save", emptyList(), "I", listOf(0))
            val draw = ImmutableMethodReference("Landroid/graphics/Canvas;", "drawBitmap",
                listOf("Landroid/graphics/Bitmap;", "F", "F", "Landroid/graphics/Paint;"), "V")
            fun draw() {
                if (range) addInstruction(BuilderInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 5, draw))
                else addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 5, 0, 4, 2, 3, 1, draw))
            }
            draw()
            if (duplicate) draw()
            invoke("Landroid/graphics/Canvas;", "restore", emptyList(), "V", listOf(0))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        return ImmutableMethod("LX/Candidate;", "LIZ",
            listOf(ImmutableMethodParameter("Landroid/graphics/Bitmap;", emptySet(), null)), "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, emptySet(), emptySet(), body.methodImplementation)
    }

    @Test fun replacementPreservesAllFiveArgumentsAndPermitsNoScratchOrRemoval() {
        val method = watermark()
        assertTrue(CommentWatermarkContracts.boundary(method))
        val code = CommentWatermarkContracts.replacement(method)
        assertEquals("invoke-static {v0, v4, v2, v3, v1}, ${CommentWatermarkContracts.WRAPPER}", code)
        val index = CommentWatermarkContracts.drawIndex(method)
        assertTrue(CommentWatermarkContracts.mutationAllowed(method, index, code, "replace"))
        assertFalse(CommentWatermarkContracts.mutationAllowed(method, index, code, "insert"))
        assertFalse(CommentWatermarkContracts.mutationAllowed(method, index, code, "remove"))
        assertFalse(CommentWatermarkContracts.mutationAllowed(method, index + 1, code, "replace"))
        assertFalse(CommentWatermarkContracts.mutationAllowed(method, index, code.replace("v2", "v5"), "replace"))
    }

    @Test fun rangeDrawKeepsTheSameFiveContiguousWords() {
        assertEquals("invoke-static/range {v0 .. v4}, ${CommentWatermarkContracts.WRAPPER}",
            CommentWatermarkContracts.replacement(watermark(range = true)))
    }

    @Test fun genericStorageAndMultipleDrawsCannotBecomeWatermarkHooks() {
        assertFalse(CommentWatermarkContracts.boundary(watermark(marker = "image/jpeg")))
        assertFalse(CommentWatermarkContracts.boundary(watermark(duplicate = true)))
    }
}
