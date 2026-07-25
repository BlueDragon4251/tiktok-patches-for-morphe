/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/PlaybackSpeedPatch.kt
 */
package app.morphe.patches.tiktok.interaction.speed

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.shared.GetEnterFromFingerprint
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val FEATURE_CONTROLS_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;"

@Suppress("unused")
val playbackSpeedPatch = bytecodePatch(
    name = "Playback speed",
    description = "Enables the playback speed option for all videos and retains the speed configurations in between videos. (Supports TikTok 43.8.3.)",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4383())

    execute {
        fun resolveSetPlaybackSpeedMethod(): String {
            val matches = mutableListOf<String>()
            classDefForEach { classDef ->
                for (method in classDef.methods) {
                    if (method.name != "LJ") continue
                    if (method.returnType != "V") continue
                    if (method.parameterTypes != listOf(
                            "Ljava/lang/String;",
                            "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
                            "F",
                            "Ljava/lang/String;",
                        )
                    ) continue

                    matches += buildString {
                        append(method.definingClass)
                        append("->")
                        append(method.name)
                        append("(")
                        method.parameterTypes.forEach { append(it) }
                        append(")")
                        append(method.returnType)
                    }
                }
            }

            return matches.singleOrNull()
                ?: throw PatchException("Playback speed: expected one set-speed method, found ${matches.size}: $matches")
        }

        val setPlaybackSpeedMethod = resolveSetPlaybackSpeedMethod()

        GetSpeedFingerprint.method.apply {
            val injectIndex = indexOfFirstInstructionOrThrow { getReference<MethodReference>()?.returnType == "F" } + 2
            val register = getInstruction<OneRegisterInstruction>(injectIndex - 1).registerA

            addInstruction(
                injectIndex,
                "invoke-static { v$register }, " +
                    "Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;->rememberPlaybackSpeed(F)V",
            )
        }

        OnRenderFirstFrameFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-virtual { p0, v0 }, ${GetEnterFromFingerprint.originalMethod}
                move-result-object v0

                invoke-virtual { p0 }, Lcom/ss/android/ugc/aweme/feed/panel/BaseListFragmentPanel;->getCurrentAweme()Lcom/ss/android/ugc/aweme/feed/model/Aweme;
                move-result-object v1
                if-eqz v1, :morphe_skip_set_speed

                invoke-static {}, Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;->getPlaybackSpeed()F
                move-result v2

                const/4 v3, 0x0
                invoke-static { v0, v1, v2, v3 }, $setPlaybackSpeedMethod

                :morphe_skip_set_speed
                nop
            """,
        )

        LongPressSpeedUpEnableFingerprint.method.let { method ->
            val lookupIndex = method.indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.let { reference ->
                    reference.parameterTypes == listOf("I", "Ljava/lang/String;", "Z", "Z") &&
                        reference.returnType == "Z"
                } == true
            }
            val resultRegister = method.getInstruction<OneRegisterInstruction>(lookupIndex + 1).registerA
            method.addInstructions(
                lookupIndex + 2,
                """
                    invoke-static {v$resultRegister}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->overrideLongPressSpeedUpEnabled(Z)Z
                    move-result v$resultRegister
                """,
            )
        }

        LongPressSpeedUpLockFingerprint.method.let { method ->
            val lookupIndex = method.indexOfFirstInstructionOrThrow {
                getReference<MethodReference>()?.let { reference ->
                    reference.parameterTypes == listOf("I", "I", "Ljava/lang/String;", "Z") &&
                        reference.returnType == "I"
                } == true
            }
            val resultRegister = method.getInstruction<OneRegisterInstruction>(lookupIndex + 1).registerA
            method.addInstructions(
                lookupIndex + 2,
                """
                    invoke-static {v$resultRegister}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->overrideLongPressSpeedUpLockDistance(I)I
                    move-result v$resultRegister
                """,
            )
        }

        // Kept in Morphe: supported on 43.8.3.
    }
}

