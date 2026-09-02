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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeEngineBootstrap;"
private const val THEME_COLOR_RESOLVER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeColorResolver;"
private const val THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeComposeColorResolver;"
private const val THEME_VIEW_HOOKS_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeViewHooks;"
private const val MAIN_PAGE_ASSEM =
    "Lcom/bytedance/tiktok/homepage/mainpagefragment/assem/MainPageBusinessAssem;"
private const val SETTINGS_COMPOSE_FRAGMENT =
    "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/SettingsComposeRvmpFragment;"
private const val PROFILE_SIDEBAR_FRAGMENT =
    "Lcom/ss/android/ugc/aweme/sidebar/profile/ProfileSidebarPageFragment;"
private const val PROFILE_SIDEBAR_CONTAINER =
    "Lcom/ss/android/ugc/aweme/sidebar/profile/ProfileSidebarContainerAssem;"
private const val SIDEBAR_ROOT_ABILITY =
    "Lcom/ss/android/ugc/aweme/sidebar/components/ISideBarRootAbility;"
private const val SETTINGS_COMPOSE_RENDERER = "LX/0VGt;"
private const val COMPOSE_PALETTE_PROVIDER = "LX/0VTU;"
private const val COMPOSE_PALETTE = "LX/05Pc;"

/** TikTok 46.7.3 TUX direct theme-attribute color resolver. */
private object TuxDirectColorResolverFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("LX/0547;") &&
            method.name == "LIZ" &&
            method.parameterTypes == listOf("I", "Landroid/content/Context;") &&
            method.returnType == "Ljava/lang/Integer;"
    },
)

/** Complete generic TUX/Compose theme-attribute resolver. */
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

/** TikTok 46.7.3 TUX semantic color-resource resolver. */
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

/** Main TikTok bottom-tab background writer. */
private object MainBottomNavigationBackgroundFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(MAIN_PAGE_ASSEM) &&
            method.name == "sh" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    },
)

/** Main bottom-tab visibility animator; it can repaint/animate the visible bar after sh(). */
private object MainBottomNavigationVisibilityFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(MAIN_PAGE_ASSEM) &&
            method.name == "showBottomTab" &&
            method.parameterTypes == listOf("Z") &&
            method.returnType == "V"
    },
)

/** Actual profile three-line/sidebar page root in TikTok 46.7.3. */
private object ProfileSidebarCreateViewFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(PROFILE_SIDEBAR_FRAGMENT) &&
            method.name == "onCreateView" &&
            method.parameterTypes == listOf(
                "Landroid/view/LayoutInflater;",
                "Landroid/view/ViewGroup;",
                "Landroid/os/Bundle;",
            ) &&
            method.returnType == "Landroid/view/View;"
    },
)

/** Actual profile sidebar becomes visible; keep the sidebar root themed. */
private object ProfileSidebarNodeShowFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(PROFILE_SIDEBAR_FRAGMENT) &&
            method.name == "onNodeShow" &&
            method.parameterTypes == listOf("Landroid/os/Bundle;") &&
            method.returnType == "V"
    },
)

/**
 * Profile-specific container that calls ISideBarRootAbility.FX2(true) when opening. Exact 46.7.3
 * discovery shows this is the root push/resize notification; the matching FX2(false) close path is
 * in onNodeHide and deliberately remains untouched.
 */
private object ProfileSidebarContainerNodeShowFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(PROFILE_SIDEBAR_CONTAINER) &&
            method.name == "onNodeShow" &&
            method.parameterTypes == listOf("Landroid/os/Bundle;") &&
            method.returnType == "V"
    },
)

/** Exact normal TikTok Settings & privacy Compose root. */
private object SettingsComposeCreateViewFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(SETTINGS_COMPOSE_FRAGMENT) &&
            method.name == "onCreateView" &&
            method.parameterTypes == listOf(
                "Landroid/view/LayoutInflater;",
                "Landroid/view/ViewGroup;",
                "Landroid/os/Bundle;",
            ) &&
            method.returnType == "Landroid/view/View;"
    },
)

/** Reapply after SettingsComposeRvmpFragment has completed its own view initialization. */
private object SettingsComposeViewCreatedFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(SETTINGS_COMPOSE_FRAGMENT) &&
            method.name == "onViewCreated" &&
            method.parameterTypes == listOf("Landroid/view/View;", "Landroid/os/Bundle;") &&
            method.returnType == "V"
    },
)

/** Native Compose palette provider used by Settings and other 46.7.3 Compose surfaces. */
private object ComposePaletteProviderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(COMPOSE_PALETTE_PROVIDER) &&
            method.name == "LIZIZ" &&
            method.parameterTypes == listOf("LX/008m;") &&
            method.returnType == COMPOSE_PALETTE
    },
)

/** Inner group/list renderer used by SettingsComposeRvmpFragment. */
private object SettingsComposeGroupRendererFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(SETTINGS_COMPOSE_RENDERER) &&
            method.name == "LIZ" &&
            method.parameterTypes == listOf(
                "LX/0VSj;",
                "Ljava/util/List;",
                "LX/008m;",
                "I",
            ) &&
            method.returnType == "V"
    },
)

/** Outer Settings & privacy Compose renderer. */
private object SettingsComposeScreenRendererFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(SETTINGS_COMPOSE_RENDERER) &&
            method.name == "LIZIZ" &&
            method.parameterTypes == listOf(
                "LX/0VSj;",
                "Ljava/util/List;",
                "LX/008m;",
                "I",
            ) &&
            method.returnType == "V"
    },
)

/** BlueIT TikTok Theme Engine. */
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

        MainBottomNavigationBackgroundFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        iget-object v0, p0, $MAIN_PAGE_ASSEM->LLJILJILJ:Landroid/view/View;
                        invoke-static {v0}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleBottomNavigation(Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }

        MainBottomNavigationVisibilityFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-virtual {p0}, $MAIN_PAGE_ASSEM->Vq()Landroid/view/View;
                        move-result-object v0
                        invoke-static {v0}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleBottomNavigation(Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }

        // Exact 46.7.3 discovery: ProfileSidebarPageFragment.onCreateView has 7 registers / 4 ins
        // and returns the created FrameLayout in v2 on both normal and caught paths.
        ProfileSidebarCreateViewFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstruction(
                    returnIndex,
                    "invoke-static {v2}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleProfileSidebar(Landroid/view/View;)V",
                )
            }
        }

        ProfileSidebarNodeShowFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getView()Landroid/view/View;
                        move-result-object v0
                        invoke-static {v0}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleProfileSidebar(Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }

        // Exact 46.7.3 path at code offset 0x00bd:
        // ISideBarRootAbility.FX2(true) is invoked only on profile-sidebar open. Skip that single
        // root push/resize notification, while leaving the onNodeHide FX2(false) cleanup untouched.
        ProfileSidebarContainerNodeShowFingerprint.method.apply {
            val pushIndex = implementation!!.instructions.withIndex().first { (_, instruction) ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == SIDEBAR_ROOT_ABILITY &&
                    reference.name == "FX2" &&
                    reference.returnType == "V"
            }.index

            addInstructionsWithLabels(
                pushIndex,
                "goto :blueit_profile_sidebar_overlay",
                ExternalLabel("blueit_profile_sidebar_overlay", getInstruction(pushIndex + 1)),
            )
        }

        // Exact discovery: onCreateView has 10 registers / 4 ins and returns its ComposeView in v5
        // on both normal and caught paths. The root style is kept for window/backdrop treatment;
        // actual Compose colors are mapped separately below.
        SettingsComposeCreateViewFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstruction(
                    returnIndex,
                    "invoke-static {v5}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleSettingsCompose(Landroid/view/View;)V",
                )
            }
        }

        SettingsComposeViewCreatedFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstruction(
                    returnIndex,
                    "invoke-static/range {p1 .. p1}, $THEME_VIEW_HOOKS_CLASS_DESCRIPTOR->styleSettingsCompose(Landroid/view/View;)V",
                )
            }
        }

        // LX/0VTU.LIZIZ is the native Compose palette provider. The returned LX/05Pc object contains
        // the packed color longs used by Settings page/card/row composables. Remap the palette once
        // per object/preset; the extension snapshots native values and can restore TikTok default.
        ComposePaletteProviderFingerprint.method.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {v0}, $THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR->mapPalette(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v0
                        check-cast v0, $COMPOSE_PALETTE
                    """.trimIndent(),
                )
            }
        }

        // Narrow renderer-level fallback for any palette value that bypasses the provider snapshot.
        listOf(
            SettingsComposeGroupRendererFingerprint.method,
            SettingsComposeScreenRendererFingerprint.method,
        ).forEach { method ->
            val paletteReads = method.implementation!!.instructions.withIndex().mapNotNull { (index, instruction) ->
                if (instruction.opcode != Opcode.IGET_WIDE) return@mapNotNull null
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapNotNull null
                if (field.definingClass != COMPOSE_PALETTE || field.type != "J") return@mapNotNull null
                val destination = (instruction as TwoRegisterInstruction).registerA
                index to destination
            }

            paletteReads.asReversed().forEach { (index, destination) ->
                method.addInstructions(
                    index + 1,
                    """
                        invoke-static/range {v$destination .. v${destination + 1}}, $THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR->mapColor(J)J
                        move-result-wide v$destination
                    """.trimIndent(),
                )
            }
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
