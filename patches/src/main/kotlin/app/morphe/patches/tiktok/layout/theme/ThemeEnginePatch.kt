package app.morphe.patches.tiktok.layout.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode

private const val THEME_ENGINE_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeEngine;"

/**
 * BlueIT TikTok Theme Engine.
 *
 * The patch option only chooses the initial preset for a fresh BlueIT settings data set. It does
 * not bake the selected theme into TikTok: users can always change the preset later from BlueIT
 * settings, and an existing runtime selection is never overwritten by a repatch/update.
 */
@Suppress("unused")
val themeEnginePatch = bytecodePatch(
    name = "Theme engine",
    description = "Adds runtime-selectable Material You, AMOLED, Liquid Glass, Graphite, Neon, Rose, Arctic, Aurora, Ember, and custom TikTok themes.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4673())

    val initialPreset by stringOption(
        key = "initialThemePreset",
        default = "default",
        values = mapOf(
            "TikTok default" to "default",
            "Material You" to "material_you",
            "Material You AMOLED" to "material_you_amoled",
            "OLED black" to "oled_black",
            "Liquid Glass" to "liquid_glass",
            "Frosted Graphite" to "frosted_graphite",
            "Midnight Neon" to "midnight_neon",
            "Rose Noir" to "rose_noir",
            "Arctic Blue" to "arctic_blue",
            "Aurora Violet" to "aurora_violet",
            "Sunset Ember" to "sunset_ember",
            "Custom" to "custom",
        ),
        title = "Initial theme preset",
        description = "Optional starting theme for a fresh install. This is only a default; the theme remains freely selectable in BlueIT settings afterwards.",
        required = false,
    )

    execute {
        val patchDefaultPreset = initialPreset ?: "default"

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableThemeEngine()V",
        )

        MainActivityOnCreateFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static/range {p0 .. p0}, Lapp/morphe/extension/shared/Utils;->setContext(Landroid/content/Context;)V
                        const-string v0, "$patchDefaultPreset"
                        invoke-static {v0}, $THEME_ENGINE_CLASS_DESCRIPTOR->initializePatchDefault(Ljava/lang/String;)V
                        invoke-static/range {p0 .. p0}, $THEME_ENGINE_CLASS_DESCRIPTOR->onMainActivityCreated(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
