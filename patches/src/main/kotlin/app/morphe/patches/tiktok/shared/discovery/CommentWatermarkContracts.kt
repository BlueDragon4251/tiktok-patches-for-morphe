package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/** Replace only the watermark draw, preserving every native argument and register. */
internal object CommentWatermarkContracts {
    const val ACCEPTED = "LX/0Xtt;->LIZ(Landroid/graphics/Bitmap;)V"
    const val MODE = "experimental-comment-watermark-draw"
    const val DRAW = "Landroid/graphics/Canvas;->drawBitmap(Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V"
    const val WRAPPER = "Lapp/morphe/extension/tiktok/download/CommentWatermarkRenderer;->draw(Landroid/graphics/Canvas;Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V"

    fun boundary(method: Method): Boolean {
        val body = method.implementation ?: return false
        val insns = body.instructions.toList()
        val refs = insns.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
        return method.definingClass.startsWith("LX/") && method.returnType == "V" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("Landroid/graphics/Bitmap;") &&
            method.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) &&
            insns.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "[tiktok_logo]" } &&
            refs.containsAll(listOf(
                "Landroid/graphics/Bitmap;->copy(Landroid/graphics/Bitmap\$Config;Z)Landroid/graphics/Bitmap;",
                "Landroid/graphics/Canvas;-><init>(Landroid/graphics/Bitmap;)V",
                "Landroid/graphics/Bitmap;->createScaledBitmap(Landroid/graphics/Bitmap;IIZ)Landroid/graphics/Bitmap;",
                "Landroid/text/Layout;->draw(Landroid/graphics/Canvas;)V",
                "Landroid/graphics/Canvas;->save()I", "Landroid/graphics/Canvas;->restore()V")) &&
            insns.count { it.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                (it as? ReferenceInstruction)?.reference?.toString() == DRAW } == 1
    }

    fun drawIndex(method: Method): Int = method.uniqueInstructionIndex("Comment watermark bitmap draw") {
        it.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
            (it as? ReferenceInstruction)?.reference?.toString() == DRAW
    }

    fun replacement(method: Method): String {
        val insn = method.implementation!!.instructions.toList()[drawIndex(method)]
        val registers = insn.argumentRegisters()
        if (registers.size != 5) throw PatchException("Comment watermark needs five original argument words")
        return if (insn is RegisterRangeInstruction)
            "invoke-static/range {v${registers.first()} .. v${registers.last()}}, $WRAPPER"
        else "invoke-static {${registers.joinToString(", ") { "v$it" }}}, $WRAPPER"
    }

    fun mutationAllowed(method: Method, index: Int?, code: String?, operation: String): Boolean =
        operation == "replace" && boundary(method) && index == drawIndex(method) && code?.trim() == replacement(method)
}
