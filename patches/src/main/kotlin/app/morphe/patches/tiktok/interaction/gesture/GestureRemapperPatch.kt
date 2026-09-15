package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.cleardisplay.rememberClearDisplayPatch
import app.morphe.patches.tiktok.interaction.quickactions.disableLongPressQuickSharePatch
import app.morphe.patches.tiktok.interaction.speed.longPressSpeedLockPatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val GESTURE_REMAPPER =
    "Lapp/morphe/extension/tiktok/interaction/gesture/GestureRemapper;"

private fun Method.delegate(predicate: (MethodReference) -> Boolean): Pair<FieldReference, MethodReference> {
    val index = uniqueInstructionIndex("Gesture delegate") { it.opcode == Opcode.INVOKE_INTERFACE && it.getReference<MethodReference>()?.let(predicate) == true }
    val body = implementation!!.instructions.toList()
    val read = body.take(index).asReversed().dropWhile { it.opcode.name.startsWith("const") }.firstOrNull()
    val field = read?.getReference<FieldReference>()
    if (read !is TwoRegisterInstruction || read.opcode != Opcode.IGET_OBJECT ||
        read.registerA != body[index].argumentRegisters().first() || field?.definingClass != definingClass) throw PatchException("Gesture delegate field does not feed native call in $this")
    return field to body[index].getReference<MethodReference>()!!
}

@Suppress("unused")
val gestureRemapperPatch = bytecodePatch(
    name = "Gesture remapper",
    description = "Remaps TikTok feed single tap, double tap, left/right seek, and long-press gestures.",
    default = true,
) {
    dependsOn(
        sharedExtensionPatch,
        rememberClearDisplayPatch,
        longPressSpeedLockPatch,
        disableLongPressQuickSharePatch,
    )
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableGestureRemapper()V",
        )

        val singleTap = PortraitSingleTapFingerprint.uniqueMethod
        val doubleTap = PortraitDoubleTapFingerprint.uniqueMethod
        val longPress = PortraitLongPressFingerprint.uniqueMethod
        if (setOf(singleTap.definingClass, doubleTap.definingClass, longPress.definingClass).size != 1) throw PatchException("Gesture callbacks must share one listener")
        val (playbackField, playbackCall) = PortraitSingleTapFingerprint.uniqueOriginalMethod.delegate { it.parameterTypes == listOf("I") && it.returnType == "V" }
        val (doubleField, doubleCall) = PortraitDoubleTapFingerprint.uniqueOriginalMethod.delegate { it.name == "handleDoubleClick" && it.parameterTypes == listOf("Landroid/view/MotionEvent;") && it.returnType == "V" }
        singleTap.requireLocals(2); doubleTap.requireLocals(1); longPress.requireLocals(1)
        singleTap.apply {
            val original = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $GESTURE_REMAPPER->singleTapAction()I
                    move-result v0
                    if-eqz v0, :blueit_single_default

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_single_consume

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_single_play_pause

                    invoke-static {}, $GESTURE_REMAPPER->showClearDisplay()Z
                    move-result v0
                    if-eqz v0, :blueit_single_default
                    const/4 v0, 0x1
                    return v0

                    :blueit_single_play_pause
                    iget-object v1, p0, $playbackField
                    const/4 v0, 0x3
                    invoke-interface {v1, v0}, $playbackCall

                    :blueit_single_consume
                    const/4 v0, 0x1
                    return v0
                """,
                ExternalLabel("blueit_single_default", original),
            )
        }

        doubleTap.apply {
            val original = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {p1}, $GESTURE_REMAPPER->doubleTapAction(Landroid/view/MotionEvent;)I
                    move-result v0
                    if-eqz v0, :blueit_double_default

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_double_consume

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_double_like

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_double_play_pause

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_double_clear_display

                    iget-object v0, p0, $doubleField
                    invoke-static {v0, p1}, $GESTURE_REMAPPER->handleConfiguredSeek(Ljava/lang/Object;Landroid/view/MotionEvent;)Z
                    move-result v0
                    if-eqz v0, :blueit_double_default
                    const/4 v0, 0x1
                    return v0

                    :blueit_double_clear_display
                    invoke-static {}, $GESTURE_REMAPPER->showClearDisplay()Z
                    move-result v0
                    if-eqz v0, :blueit_double_default
                    const/4 v0, 0x1
                    return v0

                    :blueit_double_play_pause
                    iget-object v0, p0, $playbackField
                    const/4 p1, 0x3
                    invoke-interface {v0, p1}, $playbackCall
                    const/4 v0, 0x1
                    return v0

                    :blueit_double_like
                    iget-object v0, p0, $doubleField
                    invoke-interface {v0, p1}, $doubleCall

                    :blueit_double_consume
                    const/4 v0, 0x1
                    return v0
                """,
                ExternalLabel("blueit_double_default", original),
            )
        }

        longPress.apply {
            val original = getInstruction(0)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $GESTURE_REMAPPER->longPressAction()I
                    move-result v0
                    if-eqz v0, :blueit_long_default

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_long_consume

                    add-int/lit8 v0, v0, -0x1
                    if-eqz v0, :blueit_long_default

                    invoke-static {}, $GESTURE_REMAPPER->showClearDisplay()Z

                    :blueit_long_consume
                    return-void
                """,
                ExternalLabel("blueit_long_default", original),
            )
        }
    }
}
