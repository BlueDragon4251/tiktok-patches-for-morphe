/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/settings/EnableOpenDebugPatch.kt
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructionsWithLabels
import app.morphe.patches.tiktok.shared.discovery.HookEvidence
import app.morphe.patches.tiktok.shared.discovery.SettingsContracts
import app.morphe.patches.tiktok.shared.discovery.SettingsCategoryContracts
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import app.morphe.patches.tiktok.shared.discovery.singleOrThrow
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method as SmaliMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SETTINGS_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/settings/BlueITActivityHook;"
private const val OPEN_DEBUG_CELL_VM_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/group/support/cells/OpenDebugCellVM;"

private const val ANDROID_CONTEXT_GET_STRING = "Landroid/content/Context;->getString(I)Ljava/lang/String;"
private var supportEnumType = ""
private object SupportCategoryRendererFingerprint : app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint(
    custom = { m, _ -> m.accessFlags == 25 && m.returnType == "V" && m.parameterTypes.size == 5 &&
        m.parameterTypes[0] == supportEnumType && m.parameterTypes[1] == "Z" && m.parameterTypes[2] == "Z" && m.parameterTypes[4] == "I" },
)
private class SettingsRowsHelperFingerprint(helper: String) : app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint(
    id = "app.morphe.patches.tiktok.misc.settings.SettingsRowsHelperFingerprint:$helper",
    definingClass = SettingsCategoryContracts.EXTENSION, name = helper, returnType = "Ljava/lang/Object;", parameters = emptyList(),
)

private data class OpenDebugTargets(
    val stateClass: String,
    val composeMutable: MutableMethod,
)

@Suppress("unused")
val settingsPatch = bytecodePatch(
    name = "BlueIT Service",
    description = "Adds the BlueIT Service settings menu to supported TikTok builds.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        addLegacySettingsEntryFallback()

        fun isOpenDebugRowCompose(method: SmaliMethod, stateClass: String): Boolean {
            val impl = method.implementation ?: return false
            val hasGetString = impl.instructions.any { insn ->
                insn.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((insn as? ReferenceInstruction)?.reference as? MethodReference)?.toString() == ANDROID_CONTEXT_GET_STRING
            }
            val readsState = impl.instructions.any { insn ->
                if (insn.opcode != Opcode.IGET_OBJECT) return@any false
                val ref = (insn as? ReferenceInstruction)?.reference as? FieldReference ?: return@any false
                ref.definingClass == stateClass
            }
            return hasGetString && readsState
        }

        fun resolveOpenDebugTargets(): OpenDebugTargets {
            val defaultState = OpenDebugCellVmDefaultStateFingerprint.uniqueReadOnlyOriginalMethod
            val returningRegisters = defaultState.implementation?.instructions?.filter { it.opcode == Opcode.RETURN_OBJECT }
                ?.map { (it as OneRegisterInstruction).registerA }?.distinct()
                ?.singleOrThrow("OpenDebug default state return register")
                ?: throw PatchException("OpenDebug defaultState has no implementation")
            val stateClass = defaultState.implementation!!.instructions.mapNotNull { insn ->
                if (insn.opcode != Opcode.NEW_INSTANCE || (insn as? OneRegisterInstruction)?.registerA != returningRegisters) null
                else ((insn as? ReferenceInstruction)?.reference as? TypeReference)?.type
            }.distinct().singleOrThrow("OpenDebug state class returned from defaultState")

            val composeMethods = mutableListOf<Pair<ClassDef, SmaliMethod>>()
            classDefForEach { classDef ->
                for (method in classDef.methods) {
                    if (method.name != "LIZ" || method.returnType != "V") continue
                    val p = method.parameterTypes
                    if (p.size != 5) continue
                    if (p[0] != stateClass) continue
                    if (p[1] != "Z" || p[2] != "Z" || p[4] != "I") continue
                    if (!p[3].startsWith("LX/")) continue
                    if (!isOpenDebugRowCompose(method, stateClass)) continue
                    composeMethods += classDef to method
                }
            }

            if (composeMethods.isEmpty()) {
                throw PatchException(
                    "Enable Open Debug: no OpenDebug row compose found for state $stateClass.",
                )
            }
            if (composeMethods.size > 1) {
                throw PatchException(
                    "Enable Open Debug: multiple OpenDebug row compose methods found for state $stateClass.",
                )
            }

            val (composeClassDef, composeMethod) = composeMethods.single()
            return OpenDebugTargets(
                stateClass = stateClass,
                composeMutable = mutableClassDefBy(composeClassDef).findMutableMethodOf(composeMethod),
            )
        }

        val targets = resolveOpenDebugTargets()
        val openDebugStateClass = targets.stateClass
        val composeMutable = targets.composeMutable
        HookEvidence.settingsTarget("settings.composeTitle", composeMutable)

        fun clickLambdaScore(method: SmaliMethod, classType: String): Int {
            val impl = method.implementation ?: return 0
            var score = 0
            for (insn in impl.instructions) {
                val ref = (insn as? ReferenceInstruction)?.reference
                if (insn.opcode == Opcode.IGET_OBJECT) {
                    val field = ref as? FieldReference ?: continue
                    if (field.definingClass == classType && field.name == "l1") score += 25
                    if (field.definingClass == classType && field.name == "l0") score += 15
                }
                if (insn.opcode == Opcode.CHECK_CAST) {
                    val type = ref as? TypeReference ?: continue
                    if (type.type == openDebugStateClass) score += 40
                    if (type.type == "Landroid/content/Context;") score += 10
                }
            }
            return score
        }

        fun resolveClickWrapperMethod(): MutableMethod {
            val composeInstructions = composeMutable.implementation!!.instructions.toList()
            val (wrapperClass, wrapperInvokeName) = composeInstructions.withIndex().mapNotNull { (index, insn) ->
                if (insn.opcode != Opcode.INVOKE_DIRECT) return@mapNotNull null
                val instruction = insn as? Instruction35c ?: return@mapNotNull null
                val ref = instruction.reference as? MethodReference
                    ?: return@mapNotNull null
                if (!ref.definingClass.startsWith("Lkotlin/jvm/internal/AwS")) return@mapNotNull null
                if (ref.parameterTypes != listOf(openDebugStateClass, "Landroid/content/Context;", "I")) {
                    return@mapNotNull null
                }

                val discriminatorRegister = when (instruction.registerCount) {
                    4 -> instruction.registerF
                    5 -> instruction.registerG
                    else -> throw PatchException(
                        "Enable Open Debug: unexpected click wrapper constructor register count ${instruction.registerCount}.",
                    )
                }
                val discriminator = composeInstructions
                    .take(index)
                    .asReversed()
                    .firstNotNullOfOrNull { previous ->
                        val register = (previous as? OneRegisterInstruction)?.registerA
                            ?: return@firstNotNullOfOrNull null
                        if (register != discriminatorRegister) return@firstNotNullOfOrNull null
                        (previous as? NarrowLiteralInstruction)?.narrowLiteral
                    } ?: throw PatchException(
                    "Enable Open Debug: could not resolve click wrapper discriminator.",
                )
                ref.definingClass to "invoke\$$discriminator"
            }.distinct().singleOrThrow("OpenDebug click wrapper and constructor discriminator")

            val matches = mutableListOf<MutableMethod>()
            classDefForEach { classDef ->
                if (classDef.type != wrapperClass) return@classDefForEach
                for (method in classDef.methods) {
                    if (method.parameterTypes.size != 1 || method.parameterTypes[0] != classDef.type) continue
                    if (method.name != wrapperInvokeName) continue
                    val score = clickLambdaScore(method, classDef.type)
                    if (score < 70) continue
                    matches += mutableClassDefBy(classDef).findMutableMethodOf(method)
                }
            }

            if (matches.size != 1) {
                throw PatchException(
                    "Enable Open Debug: expected one OpenDebug click handler in $wrapperClass, found ${matches.size}.",
                )
            }
            return matches.single().also { HookEvidence.settingsTarget("settings.clickWrapper", it) }
        }

        fun resolveOpenDebugFunction2Method(): MutableMethod {
            val defaultState = OpenDebugCellVmDefaultStateFingerprint.uniqueReadOnlyOriginalMethod
            val openDebugVmClass = defaultState.definingClass
            val lambdaClass = defaultState.implementation?.instructions?.mapNotNull { insn ->
                if (insn.opcode != Opcode.INVOKE_DIRECT) return@mapNotNull null
                val ref = (insn as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
                if (!ref.definingClass.startsWith("Lkotlin/jvm/internal/AwS")) return@mapNotNull null
                if (ref.parameterTypes.firstOrNull() != openDebugVmClass) return@mapNotNull null
                ref.definingClass
            }?.distinct()?.singleOrThrow("OpenDebug Function2 lambda class")
                ?: throw PatchException("Enable Open Debug: could not resolve OpenDebug Function2 lambda class.")

            val matches = mutableListOf<MutableMethod>()
            classDefForEach { classDef ->
                if (classDef.type != lambdaClass) return@classDefForEach
                for (method in classDef.methods) {
                    if (!method.name.matches(Regex("invoke\\\$\\d+"))) continue
                    if (method.returnType != "Ljava/lang/Object;") continue
                    val parameters = method.parameterTypes
                    if (parameters.size != 3 || parameters[0] != lambdaClass) continue
                    val hasOpenDebugCast = method.implementation?.instructions?.any { insn ->
                        insn.opcode == Opcode.CHECK_CAST &&
                            ((insn as? ReferenceInstruction)?.reference as? TypeReference)?.type == openDebugVmClass
                    } == true
                    if (!hasOpenDebugCast) continue
                    matches += mutableClassDefBy(classDef).findMutableMethodOf(method)
                }
            }

            if (matches.size != 1) {
                throw PatchException(
                    "Enable Open Debug: expected one OpenDebug Function2 lambda, found ${matches.size}.",
                )
            }
            return matches.single().also { HookEvidence.settingsTarget("settings.function2", it) }
        }

        fun MutableMethod.openBlueITServiceAtStart(contextRegister: String) {
            addInstructions(
                0,
                """
                    invoke-static {}, Lapp/morphe/extension/shared/Utils;->getContext()Landroid/content/Context;
                    move-result-object v$contextRegister
                    if-eqz v$contextRegister, :return_unit
                    new-instance v1, Landroid/content/Intent;
                    const-class v2, Lcom/bytedance/ies/ugc/aweme/commercialize/compliance/personalization/AdPersonalizationActivity;
                    invoke-direct {v1, v$contextRegister, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                    const/high16 v2, 0x10000000
                    invoke-virtual { v1, v2 }, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;
                    const-string v2, "blueit_service_settings"
                    invoke-virtual { v1, v2 }, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;
                    invoke-virtual { v$contextRegister, v1 }, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                    :return_unit
                    sget-object v$contextRegister, Lkotlin/Unit;->LIZ:Lkotlin/Unit;
                    return-object v$contextRegister
                """,
            )
        }

        fun addOpenDebugToVisibleSettingsList(): Boolean {
            val composeRowsMethod = SettingsComposeRowsFingerprint.optionalMethod ?: return false
            val openDebugField = SupportGroupDefaultStateFingerprint.uniqueMethod.implementation?.instructions
                ?.firstNotNullOfOrNull { instruction ->
                    if (instruction.opcode != Opcode.SGET_OBJECT) return@firstNotNullOfOrNull null
                    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        ?: return@firstNotNullOfOrNull null
                    field.takeIf { it.name == "SECTION_HEADER" }
                } ?: return false

            supportEnumType = openDebugField.type
            val enumClass = classDefByOrNull(supportEnumType) ?: throw PatchException("Missing native settings enum")
            if (enumClass.superclass != "Ljava/lang/Enum;" || enumClass.fields.none { it.name == "OPEN_DEBUG" && it.type == supportEnumType })
                throw PatchException("Changed native Support enum")
            val ctor = enumClass.methods.singleOrNull { it.name == "<init>" && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                it.parameterTypes.map(CharSequence::toString) == List(5) { "Ljava/lang/String;" } + listOf("I", "Z", "I") }
                ?: throw PatchException("Changed native Support heading constructor")
            fun replaceHelper(name: String, locals: Int, code: String) {
                val original = SettingsRowsHelperFingerprint(name).uniqueMethod
                val replacement = MutableMethod(ImmutableMethod(original.definingClass, original.name,
                    original.parameters, original.returnType, original.accessFlags, original.annotations,
                    original.hiddenApiRestrictions, MutableMethodImplementation(locals)))
                val helperClass = mutableClassDefBy(classDefByOrNull(SettingsCategoryContracts.EXTENSION)!!)
                check(helperClass.methods.remove(original))
                helperClass.methods.add(replacement)
                replacement.addInstructions(0, code)
            }
            replaceHelper("nativeHeader", 9, """
                    new-instance v0, $supportEnumType
                    const-string v1, "BLUEIT_SERVICES"
                    const-string v2, "blueit_services_group"
                    const-string v3, "sectionBlueITServices"
                    const-string v4, "sectionBlueITServices"
                    const-string v5, ""
                    const/4 v6, 0x0
                    const/4 v7, 0x1
                    const/4 v8, 0x0
                    invoke-direct/range {v0 .. v8}, $ctor
                    return-object v0
                """)
            replaceHelper("nativeOpenDebug", 1,
                "sget-object v0, $supportEnumType->OPEN_DEBUG:$supportEnumType\nreturn-object v0")
            SupportCategoryRendererFingerprint.uniqueMethod.apply {
                val site = SettingsCategoryContracts.headerSite(this)
                addInstructions(site.index, site.code)
            }
            val site = SettingsCategoryContracts.rowsSite(composeRowsMethod)
            composeRowsMethod.addInstructions(site.index, site.code)
            return true
        }

        // Observe the complete settings chain before its first refused injection so
        // one candidate run exposes every downstream site for review.
        fun diagnose(role: String, resolve: () -> SmaliMethod?) {
            runCatching(resolve).fold(
                onSuccess = { HookEvidence.diagnostic(role, it) },
                onFailure = { HookEvidence.diagnosticFailure(role, it) },
            )
        }
        diagnose("settings.composeTitle") { composeMutable }
        diagnose("settings.clickWrapper") { resolveClickWrapperMethod() }
        diagnose("settings.function2") { resolveOpenDebugFunction2Method() }
        diagnose("settings.visibleRows") { SettingsComposeRowsFingerprint.uniqueMatchOrNull()?.originalMethod }
        diagnose("settings.supportGroup") { SupportGroupDefaultStateFingerprint.uniqueMatch().originalMethod }
        diagnose("settings.activityCreate") { AdPersonalizationActivityOnCreateFingerprint.uniqueMatch().originalMethod }
        diagnose("settings.activityBack") { AdPersonalizationActivityOnBackPressedFingerprint.uniqueMatch().originalMethod }

        if (!addOpenDebugToVisibleSettingsList()) {
            throw PatchException("Missing native settings category list; refusing an ungrouped fallback")

        }

        AdPersonalizationActivityOnCreateFingerprint.uniqueMethod.apply {
            val site = SettingsContracts.activitySite(this, create = true)
            addInstructionsWithLabels(
                site.index,
                SettingsContracts.activityCode(site, create = true),
                ExternalLabel("do_not_open", getInstruction(site.index)),
            )
        }

        AdPersonalizationActivityOnBackPressedFingerprint.uniqueMethod.apply {
            val site = SettingsContracts.activitySite(this, create = false)
            addInstructionsWithLabels(
                site.index,
                SettingsContracts.activityCode(site, create = false),
                ExternalLabel("blueit_service_settings_not_handled", getInstruction(site.index)),
            )
        }

        val (titleIndex, titleStringRegister) = SettingsContracts.titleSite(composeMutable)
        composeMutable.addInstruction(titleIndex, "const-string v$titleStringRegister, \"BlueIT Service\"")

        val clickWrapperMethod = resolveClickWrapperMethod()
        val openDebugClickWrapperClass = clickWrapperMethod.definingClass
        clickWrapperMethod.apply {
            addInstructions(
                0,
                """
                    iget-object v0, p0, $openDebugClickWrapperClass->l1:Ljava/lang/Object;
                    check-cast v0, Landroid/content/Context;
                    new-instance v1, Landroid/content/Intent;
                    const-class v2, Lcom/bytedance/ies/ugc/aweme/commercialize/compliance/personalization/AdPersonalizationActivity;
                    invoke-direct {v1, v0, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                    const-string v2, "blueit_service_settings"
                    invoke-virtual { v1, v2 }, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;
                    const/high16 v2, 0x10000000
                    invoke-virtual { v1, v2 }, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;
                    invoke-virtual { v0, v1 }, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
                    sget-object v0, Lkotlin/Unit;->LIZ:Lkotlin/Unit;
                    return-object v0
                """,
            )
        }

        resolveOpenDebugFunction2Method().openBlueITServiceAtStart("0")
    }
}
