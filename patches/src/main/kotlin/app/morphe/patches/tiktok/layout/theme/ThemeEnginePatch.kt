package app.morphe.patches.tiktok.layout.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode

private const val THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeEngineBootstrap;"
private const val THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeColorResolver;"

/** TikTok 46.7.3 TUX direct theme-attribute color resolver. */
private object TuxDirectColorResolverFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("LX/0547;") &&
            method.name == "LIZ" &&
            method.parameterTypes == listOf("I", "Landroid/content/Context;") &&
            method.returnType == "Ljava/lang/Integer;"
    },
)

/**
 * TikTok 46.7.3 TUX generic theme-attribute resolver. Compose/TUX call this method directly with
 * different TypedValue converters, so hooking only the Integer wrapper misses many stock screens.
 */
private object TuxGenericAttributeResolverFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("LX/0547;") &&
            method.name == "LIZIZ" &&
            method.parameterTypes == listOf(
                "I",
                "Landroid/content/Context;",
                "Lkotlin/jvm/functions/Function1;",
            ) &&
            method.returnType == "Ljava/lang/Object;"
    },
)

/** TikTok 46.7.3 TUX semantic color-resource resolver used by Tux views and Compose hosts. */
private object TuxSemanticColorResolverFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("LX/0547;") &&
            method.name == "LIZJ" &&
            method.parameterTypes == listOf("I", "Landroid/content/Context;") &&
            method.returnType == "Ljava/lang/Integer;"
    },
)

/** TikTok 46.7.3 styled-attribute color resolver used by several TUX widgets. */
private object TuxStyledColorResolverFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("LX/0547;") &&
            method.name == "LIZLLL" &&
            method.parameterTypes == listOf("I", "Landroid/content/Context;", "[I") &&
            method.returnType == "Ljava/lang/Integer;"
    },
)

/**
 * BlueIT TikTok Theme Engine.
 *
 * Temporarily opt-in while Automatic Clear Display remains isolated from the recovery build.
 * TikTok 46.7.3 renders most stock UI through TUX/Compose, so the patch hooks TUX's complete
 * semantic color path in addition to the classic View-surface styler. The runtime choice remains
 * fully selectable from BlueIT; the patch option is only the initial preset.
 */
@Suppress("unused")
val themeEnginePatch = bytecodePatch(
    name = "Theme engine",
    description = "Experimental recovery opt-in: runtime-selectable BlueIT themes applied to TikTok TUX/Compose colors and classic surfaces.",
    default = false,
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

        // Exact 46.7.3 discovery proved all four methods have real local registers. Returning null
        // from ThemeColorResolver means "continue with TikTok's original resolver".
        TuxDirectColorResolverFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    const-string v0, "$patchDefaultPreset"
                    invoke-static {p0, p1, v0}, $THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR->resolve(ILandroid/content/Context;Ljava/lang/String;)Ljava/lang/Integer;
                    move-result-object v0
                    if-eqz v0, :blueit_tux_direct_original
                    return-object v0
                """.trimIndent(),
                ExternalLabel("blueit_tux_direct_original", getInstruction(0)),
            )
        }

        TuxGenericAttributeResolverFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    const-string v0, "$patchDefaultPreset"
                    invoke-static {p0, p1, p2, v0}, $THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR->resolveGeneric(ILandroid/content/Context;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
                    move-result-object v0
                    if-eqz v0, :blueit_tux_generic_original
                    return-object v0
                """.trimIndent(),
                ExternalLabel("blueit_tux_generic_original", getInstruction(0)),
            )
        }

        TuxSemanticColorResolverFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    const-string v0, "$patchDefaultPreset"
                    invoke-static {p0, p1, v0}, $THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR->resolve(ILandroid/content/Context;Ljava/lang/String;)Ljava/lang/Integer;
                    move-result-object v0
                    if-eqz v0, :blueit_tux_semantic_original
                    return-object v0
                """.trimIndent(),
                ExternalLabel("blueit_tux_semantic_original", getInstruction(0)),
            )
        }

        TuxStyledColorResolverFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    const-string v0, "$patchDefaultPreset"
                    invoke-static {p0, p1, p2, v0}, $THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR->resolveFromAttributeArray(ILandroid/content/Context;[ILjava/lang/String;)Ljava/lang/Integer;
                    move-result-object v0
                    if-eqz v0, :blueit_tux_styled_original
                    return-object v0
                """.trimIndent(),
                ExternalLabel("blueit_tux_styled_original", getInstruction(0)),
            )
        }

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
                        invoke-static {v0}, $THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR->setPatchDefaultPreset(Ljava/lang/String;)V
                        invoke-static/range {p0 .. p0}, $THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR->start(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
