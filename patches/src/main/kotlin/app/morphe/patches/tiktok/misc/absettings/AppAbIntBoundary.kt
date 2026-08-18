/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.absettings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Kept temporarily for Feature Gate Lab's 46.2.3 boundary table. The shared
// integer hook below no longer relies on these obfuscated identifiers.
internal const val APP_AB_DESCRIPTOR = "LX/0BYX;"
internal const val APP_AB_INT_METHOD = "LJFF"
internal const val APP_AB_INT_KEY_REGISTER = "p3"
internal val APP_AB_INT_PARAMETERS = listOf("I", "I", "Ljava/lang/String;", "Z")

/**
 * TikTok obfuscates the App-AB implementation class and method names between
 * releases. The typed getter family is much more stable than those names, so
 * identify the class by the complete group of primitive/string getter
 * signatures instead of a descriptor such as LX/0BYX;.
 */
private fun ClassDef.hasAppAbTypedGetterFamily(): Boolean {
    fun has(returnType: String, parameters: List<String>) = methods.any { method ->
        method.returnType == returnType && method.parameterTypes == parameters
    }

    return has("Z", listOf("I", "Ljava/lang/String;", "Z", "Z")) &&
        has("D", listOf("D", "I", "Ljava/lang/String;", "Z")) &&
        has("F", listOf("I", "Ljava/lang/String;", "Z", "F")) &&
        has("I", APP_AB_INT_PARAMETERS) &&
        has("J", listOf("I", "J", "Ljava/lang/String;", "Z")) &&
        has("Ljava/lang/String;", listOf("I", "Ljava/lang/String;", "Ljava/lang/String;", "Z")) &&
        has("Ljava/lang/Object;", listOf("Ljava/lang/String;", "Z"))
}

internal object AppAbIntBoundaryFingerprint : Fingerprint(
    returnType = "I",
    parameters = APP_AB_INT_PARAMETERS,
    custom = { _, classDef -> classDef.hasAppAbTypedGetterFamily() },
)

internal fun BytecodePatchContext.hookAppAbIntBoundary(
    extensionDescriptor: String,
    extensionMethod: String,
) {
    AppAbIntBoundaryFingerprint.method.apply {
        implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN }
            .map { it.index }
            .asReversed()
            .forEach { returnIndex ->
                val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {$APP_AB_INT_KEY_REGISTER, v$register}, $extensionDescriptor->$extensionMethod(Ljava/lang/String;I)I
                        move-result v$register
                    """,
                )
            }
    }
}
