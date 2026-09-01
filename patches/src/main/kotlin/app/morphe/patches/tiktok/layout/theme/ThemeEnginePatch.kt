package app.morphe.patches.tiktok.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode

private const val THEME_ENGINE_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeEngine;"

/**
 * BlueIT TikTok Theme Engine.
 *
 * The runtime hook is deliberately anchored to the stable MainActivity.onCreate method instead of
 * broad view constructors. ThemeEngine itself applies only targeted/fail-open surface styling.
 */
@Suppress("unused")
val themeEnginePatch = bytecodePatch(
    name = "Theme engine",
    description = "Adds Material You, AMOLED, OLED black, Liquid Glass, and custom TikTok themes.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4673())

    execute {
        MainActivityOnCreateFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstruction(
                    returnIndex,
                    "invoke-static/range {p0 .. p0}, $THEME_ENGINE_CLASS_DESCRIPTOR->onMainActivityCreated(Landroid/app/Activity;)V",
                )
            }
        }
    }
}
