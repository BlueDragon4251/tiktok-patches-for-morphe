package app.morphe.patches.tiktok.layout.theme

import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import app.morphe.patches.tiktok.shared.discovery.HookEvidence
import app.morphe.patches.tiktok.shared.discovery.ThemeSurfaceContracts
import app.morphe.patches.tiktok.shared.discovery.ScratchContracts
import app.morphe.patches.tiktok.shared.discovery.ReceiverAliases
import app.morphe.patches.tiktok.shared.discovery.ThemeContracts
import app.morphe.patches.tiktok.shared.discovery.FeedDescriptionContracts
import app.morphe.patches.tiktok.shared.discovery.AvatarGradientContracts
import app.morphe.patches.tiktok.shared.discovery.calls
import app.morphe.patches.tiktok.shared.discovery.readsField
import com.android.tools.smali.dexlib2.iface.ClassDef
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addAfterInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.replaceInstruction
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeEngineBootstrap;"
private const val THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/theme/ThemeComposeColorResolver;"
private const val MAIN_PAGE_ASSEM =
    "Lcom/bytedance/tiktok/homepage/mainpagefragment/assem/MainPageBusinessAssem;"
private const val SETTINGS_COMPOSE_FRAGMENT =
    "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/SettingsComposeRvmpFragment;"
private const val PROFILE_SIDEBAR_FRAGMENT =
    "Lcom/ss/android/ugc/aweme/sidebar/profile/ProfileSidebarPageFragment;"
private var composePaletteType = ""

private class NativeRootFingerprint(owner: String) : Fingerprint(
    id = "app.morphe.patches.tiktok.layout.theme.NativeRootFingerprint:$owner:onCreateView",
    custom = { _, candidate -> candidate.type == if (owner == ThemeSurfaceContracts.OLD_CHAT) ThemeSurfaceContracts.chatOwner(HookEvidence::originalClass) else owner },
    name = "onCreateView", returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"),
)

private val nativeRoots = mapOf(
    "Lcom/ss/android/ugc/profile/business/profile/ui/v2/I18nMyProfileFragment;" to "profilePage",
    PROFILE_SIDEBAR_FRAGMENT to "sidebar",
    "Lcom/ss/android/ugc/aweme/sidebar/SidebarPageFragment;" to "sidebar",
    "Lcom/ss/android/ugc/profile/business/profile/menu/ProfilePageMenuFragment;" to "sidebar",
    "Lcom/ss/android/ugc/aweme/search/middle/AbstractSearchIntermediateFragmentNew;" to "search",
    "Lcom/ss/android/ugc/aweme/im/sdk/chat/ui/powerpage/BaseChatRoomFragment;" to "chat",
).map { (owner, hook) -> NativeRootFingerprint(owner) to hook }

private object HomePagerViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/main/assems/ui/HomepageViewPagerAssem;",
    name = "onViewCreated", parameters = listOf("Landroid/view/View;"), returnType = "V",
)

private var nativePagerDrawOwner = ""
private object NativePagerDrawFingerprint : Fingerprint(
    name = "dispatchDraw", parameters = listOf("Landroid/graphics/Canvas;"), returnType = "V",
    custom = { method, owner ->
        owner.type == nativePagerDrawOwner && owner.superclass == "Landroid/view/ViewGroup;" &&
            method.calls("Landroid/view/ViewGroup;", "dispatchDraw") && owner.methods.any {
                it.name == "computeScroll" && it.parameterTypes.isEmpty() && it.returnType == "V" &&
                    it.calls("Landroid/widget/Scroller;", "computeScrollOffset") &&
                    it.calls("Landroid/view/View;", "scrollTo")
            }
    },
)

/** Native master switch for the left-aligned header, merged avatar/info and avatar-at-right variants. */
private object ProfileLeftAlignFingerprint : Fingerprint(
    name = "<clinit>", returnType = "V", parameters = emptyList(),
    strings = listOf("profile_left_align"),
    custom = { method, owner ->
        method.implementation?.instructions?.any {
            ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "profile_left_align"
        } == true && owner.fields.count { it.type == "Z" } == 1 &&
            method.implementation?.instructions?.count { instruction ->
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                instruction.opcode == Opcode.SPUT_BOOLEAN && field?.definingClass == owner.type
            } == 1 && method.calls(parameters = listOf("I", "I", "Ljava/lang/String;", "Z"), returns = "I")
    },
)

/** Native inbox dispatcher after its actual row bind. */
private object InboxSessionBindFingerprint : Fingerprint(
    custom = { method, owner -> owner.type == ThemeSurfaceContracts.inboxOwner(HookEvidence::originalClass) &&
        ThemeSurfaceContracts.inboxBoundary(method, owner) },
)

/** Resolve the family by framework behavior, not an obfuscated owner/name. */
private fun isTuxFamily(owner: ClassDef): Boolean = owner.methods.any {
    it.parameterTypes == listOf("I", "Landroid/content/Context;", "[I") &&
        it.returnType == "Ljava/lang/Integer;" && it.calls("Landroid/content/res/TypedArray;", "getColor")
} && owner.methods.any {
    it.parameterTypes == listOf("Landroid/content/res/Resources$" + "Theme;", "I",
        "Landroid/util/TypedValue;", "Lkotlin/jvm/functions/Function1;") &&
        it.calls("Landroid/content/res/Resources$" + "Theme;", "resolveAttribute")
}

private object TuxDirectColorResolverFingerprint : Fingerprint(
    parameters = listOf("I", "Landroid/content/Context;"), returnType = "Ljava/lang/Integer;",
    custom = { method, owner -> isTuxFamily(owner) &&
        method.readsField("Landroid/util/TypedValue;", "data") &&
        method.calls("Landroid/content/res/Resources$" + "Theme;", "resolveAttribute") },
)
private object TuxGenericAttributeResolverFingerprint : Fingerprint(
    parameters = listOf("I", "Landroid/content/Context;", "Lkotlin/jvm/functions/Function1;"),
    returnType = "Ljava/lang/Object;",
    custom = { method, owner -> isTuxFamily(owner) && method.calls("Landroid/content/Context;", "getTheme") },
)
private object TuxSemanticColorResolverFingerprint : Fingerprint(
    parameters = listOf("I", "Landroid/content/Context;"), returnType = "Ljava/lang/Integer;",
    custom = { method, owner -> isTuxFamily(owner) && method.calls(owner = owner.type,
        parameters = listOf("I", "Landroid/content/Context;", "Lkotlin/jvm/functions/Function1;"),
        returns = "Ljava/lang/Object;") },
)
private object TuxStyledColorResolverFingerprint : Fingerprint(
    parameters = listOf("I", "Landroid/content/Context;", "[I"), returnType = "Ljava/lang/Integer;",
    custom = { method, owner -> isTuxFamily(owner) && method.calls("Landroid/content/res/TypedArray;", "getColor") },
)

private object FeedCaptionDrawFingerprint : Fingerprint(
    id = app.morphe.patches.tiktok.shared.discovery.ThemeRenderContracts.DRAW_HOOK,
    name = "onDraw", parameters = listOf("Landroid/graphics/Canvas;"), returnType = "V",
    custom = { method, owner -> app.morphe.patches.tiktok.shared.discovery.ThemeRenderContracts.drawBoundary(method, owner) },
)

private object FeedDescriptionGetterFingerprint : Fingerprint(
    definingClass = FeedDescriptionContracts.OWNER, parameters = emptyList(), returnType = FeedDescriptionContracts.TEXT,
    custom = { method, owner -> FeedDescriptionContracts.boundary(method, owner, HookEvidence::originalClass) },
)

private object AvatarGradientConfigFingerprint : Fingerprint(
    parameters = listOf(AvatarGradientContracts.CONFIG, "Landroid/content/Context;"),
    custom = { method, owner -> AvatarGradientContracts.boundary(method, owner, HookEvidence::originalClass) },
)

/** Native half-dp separator writer; the bar itself comes from showBottomTab(). */
private object MainBottomNavigationDividerFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith(MAIN_PAGE_ASSEM) &&
            method.calls("Landroid/view/View;", "setBackgroundColor") &&
            method.parameterTypes.isEmpty() &&
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

/** Resolve the provider from its returned color-table type and composition-local access. */
private object ComposePaletteProviderFingerprint : Fingerprint(
    custom = { method, owner ->
        method.returnType == composePaletteType && method.accessFlags == 9 &&
            method.implementation?.instructions?.count() == 5 && ThemeContracts.paletteProviderBoundary(method, owner, HookEvidence::originalClass)
    },
)

private var settingsGroupRenderer = ""
private var settingsScreenRenderer = ""
private object NativeBackgroundFieldFingerprint : Fingerprint(
    definingClass = THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR,
    name = "nativeBackgroundField", returnType = "Ljava/lang/String;", parameters = emptyList(),
)
private object SettingsComposeGroupRendererFingerprint : Fingerprint(
    custom = { method, _ -> method.toString() == settingsGroupRenderer },
)
private object SettingsComposeScreenRendererFingerprint : Fingerprint(
    custom = { method, _ -> method.toString() == settingsScreenRenderer },
)

/** BlueIT TikTok Theme Engine. */
@Suppress("unused")
val themeEnginePatch = bytecodePatch(
    name = "Theme engine",
    description = "Experimental recovery opt-in: runtime-selectable BlueIT themes applied to TikTok TUX/Compose colors and classic surfaces.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktokVerified())

    val classicProfileLayout by booleanOption(
        key = "classicProfileLayout",
        default = true,
        title = "Classic centered profile",
        description = "Use TikTok's native centered profile header instead of the left-aligned experiment. Applies independently of the selected color preset.",
        required = true,
    )

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
        val palettes = mutableListOf<ClassDef>()
        classDefForEach { owner ->
            val fields = owner.fields.toList()
            val colors = fields.count { it.type == "J" }
            val state = fields.filter { it.type != "J" }
            if (colors >= 200 && state.size <= 4 && state.all { it.type.startsWith("L") }) palettes += owner
        }
        if (palettes.size != 1) throw PatchException("Compose color table: expected one color-table palette, got ${palettes.map { it.type }}")
        composePaletteType = palettes.single().type
        val renderers = ThemeSurfaceContracts.renderers(HookEvidence.originalClass(SETTINGS_COMPOSE_FRAGMENT)!!, HookEvidence::originalClass)
        settingsGroupRenderer = renderers[0].toString()
        settingsScreenRenderer = renderers[1].toString()


        listOf(TuxDirectColorResolverFingerprint, TuxGenericAttributeResolverFingerprint,
            TuxSemanticColorResolverFingerprint, TuxStyledColorResolverFingerprint,
            InboxSessionBindFingerprint, MainBottomNavigationDividerFingerprint,
            ComposePaletteProviderFingerprint).forEach { fingerprint ->
            val match = fingerprint.observeUniqueSite()
            println("[BlueIT Hook Contract] ${fingerprint.javaClass.simpleName}: ${match.originalMethod}")
        }

        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableThemeEngine()V",
        )

        listOf(TuxDirectColorResolverFingerprint to "direct", TuxGenericAttributeResolverFingerprint to "generic",
            TuxSemanticColorResolverFingerprint to "semantic", TuxStyledColorResolverFingerprint to "styled").forEach { (fingerprint, role) ->
            val method = fingerprint.uniqueMethod
            val site = ThemeContracts.tuxEntrySite(method, role, patchDefaultPreset)
            method.addInstructions(site.index, site.code)
        }

        FeedCaptionDrawFingerprint.uniqueMethod.apply {
            val site = app.morphe.patches.tiktok.shared.discovery.ThemeRenderContracts.site(this)
            addInstruction(site.index, site.code)
        }
        FeedDescriptionGetterFingerprint.uniqueMethod.apply {
            val site = FeedDescriptionContracts.site(this)
            addInstruction(site.index, site.code)
        }

        AvatarGradientConfigFingerprint.uniqueMethod.apply {
            val site = AvatarGradientContracts.site(this)
            addInstructions(site.index, site.code)
        }

        InboxSessionBindFingerprint.uniqueMethod.apply {
            ThemeSurfaceContracts.inboxSites(this).asReversed().forEach { addInstructions(it.index, it.code) }
        }

        // Resolve actual native roots by lifecycle contracts, and use each return's real register.
        nativeRoots.forEach { (fingerprint, hook) ->
            val method = fingerprint.allMatches(1..1).single().method
            method.hookNativeReturns(hook)
        }

        // Resolve the real home pager from the native View argument's cast, then its draw owner.
        // computeScroll can advance the pager after pre-draw, so correct before it draws children.
        val pagerInit = HomePagerViewCreatedFingerprint.observeUniqueSite().originalMethod
        HookEvidence.requireReadOnlyDiscovery(pagerInit)
        val pagerInput = pagerInit.implementation!!.registerCount - 1
        val pagerType = pagerInit.implementation!!.instructions.mapNotNull { instruction ->
            if (instruction.opcode == Opcode.CHECK_CAST &&
                (instruction as OneRegisterInstruction).registerA == pagerInput) {
                ((instruction as ReferenceInstruction).reference as TypeReference).type
            } else null
        }.distinct().singleOrNull() ?: throw PatchException("Native home pager: expected one View argument cast")
        var pagerClass = classDefBy(pagerType)
        val pagerAncestors = mutableSetOf<String>()
        while (pagerClass.methods.none { it.name == "dispatchDraw" &&
                it.parameterTypes == listOf("Landroid/graphics/Canvas;") && it.returnType == "V" }) {
            val parent = pagerClass.superclass
            if (!pagerAncestors.add(pagerClass.type) || parent == null || parent.startsWith("Landroid/")) {
                throw PatchException("Native home pager: no app-owned dispatchDraw in $pagerType ancestry")
            }
            pagerClass = classDefBy(parent)
        }
        nativePagerDrawOwner = pagerClass.type
        val pagerDraw = NativePagerDrawFingerprint.allMatches(1..1).single().method
        pagerDraw.addInstruction(0,
            "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/tiktok/theme/ThemeNativeTargets;->beforePagerDraw(Landroid/view/ViewGroup;)V")
        println("[BlueIT Pager Draw Contract] $pagerType -> ${pagerClass.type}->dispatchDraw")

        if (classicProfileLayout == true) {
            val method = ProfileLeftAlignFingerprint.allMatches(1..1).single().method
            val (index, instruction) = method.implementation!!.instructions.withIndex().single {
                it.value.opcode == Opcode.SPUT_BOOLEAN
            }
            val field = (instruction as ReferenceInstruction).reference as FieldReference
            val register = (instruction as OneRegisterInstruction).registerA
            // Rewrite the native decision before its cached dependents initialize. Keep labels
            // on the zero assignment so both branches select the same complete classic layout.
            method.replaceInstruction(index, "const/16 v$register, 0x0")
            method.addInstruction(index + 1, "sput-boolean v$register, ${field.definingClass}->${field.name}:Z")
            println("[BlueIT Profile Layout Contract] profile_left_align disabled at ${field.definingClass}->${field.name}")
        }

        // Follow the native divider and showBottomTab getter fields, then hook only their writers.
        // Preserve its separately accepted unique identity; writer edits are validated at touch().
        MainBottomNavigationDividerFingerprint.uniqueMatch()
        val navigationClass = HookEvidence.originalClass(MAIN_PAGE_ASSEM)!!
        var writers = 0
        navigationClass.methods.forEach { original ->
            val sites = ThemeSurfaceContracts.navigationSites(original, navigationClass)
            if (sites.isNotEmpty()) {
                val method = mutableClassDefBy(navigationClass).findMutableMethodOf(original)
                sites.asReversed().forEach { site -> method.addAfterInstruction(site.index, site.code); writers++ }
            }
        }
        if (writers < 3) throw PatchException("Native navigation: missing initialization/background writer contracts ($writers)")

        SettingsComposeCreateViewFingerprint.uniqueMethod.apply {
            ThemeSurfaceContracts.rootSites(this).asReversed().forEach { addInstruction(it.index, it.code) }
        }
        SettingsComposeViewCreatedFingerprint.uniqueMethod.apply {
            ThemeSurfaceContracts.settingsViewSites(this).asReversed().forEach { addInstruction(it.index, it.code) }
        }

        // The Settings renderer supplies the native Compose palette provider. Its returned object contains
        // the packed color longs used by Settings page/card/row composables. Remap the palette once
        // per object/preset; the extension snapshots native values and can restore TikTok default.
        ComposePaletteProviderFingerprint.uniqueMethod.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }
                .toList()

            returnIndices.asReversed().forEach { returnIndex ->
                val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                addInstructions(
                    returnIndex,
                    """
                        invoke-static/range {v$register .. v$register}, $THEME_COMPOSE_COLOR_RESOLVER_CLASS_DESCRIPTOR->mapPalette(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$register
                        check-cast v$register, $returnType
                    """.trimIndent(),
                )
            }
        }

        // Determine TikTok's own light/dark palette from the actual page background, independently
        // of Android's system mode. The provider already maps every palette field; a second mapping
        // at renderer reads would turn Arctic Blue's dark background back into its light text color.
        SettingsComposeGroupRendererFingerprint.uniqueOriginalMethod
        val pageField = ThemeSurfaceContracts.backgroundField(
            SettingsComposeScreenRendererFingerprint.uniqueOriginalMethod, HookEvidence::originalClass)
        NativeBackgroundFieldFingerprint.uniqueMethod.apply {
            val index = implementation!!.instructions.indexOfFirst { it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO }
            if (index < 0) throw PatchException("Missing native background field configuration")
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(index, "const-string v$register, \"${pageField.name}\"")
        }

        MainActivityOnCreateFingerprint.uniqueMethod.apply {
            val returnIndices = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .toList()

            val aliases = ReceiverAliases.atEveryInstruction(this)
            returnIndices.asReversed().forEach { returnIndex ->
                val receiver = aliases[returnIndex]?.minOrNull() ?: throw PatchException("Theme Activity receiver overwritten")
                val scratch = ScratchContracts.locals(this, returnIndex, setOf(receiver), 1).single()
                addInstructions(
                    returnIndex,
                    """
                        invoke-static/range {v$receiver .. v$receiver}, Lapp/morphe/extension/shared/Utils;->setContext(Landroid/content/Context;)V
                        const-string v$scratch, "$patchDefaultPreset"
                        invoke-static {v$scratch}, $THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR->setPatchDefaultPreset(Ljava/lang/String;)V
                        invoke-static/range {v$receiver .. v$receiver}, $THEME_ENGINE_BOOTSTRAP_CLASS_DESCRIPTOR->start(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
