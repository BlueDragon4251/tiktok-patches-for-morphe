package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.patches.tiktok.shared.discovery.calls
import com.android.tools.smali.dexlib2.iface.ClassDef

/** Identical double-tap bodies need the sibling callback family to disambiguate. */
private fun portraitListener(owner: ClassDef): Boolean {
    val single = owner.methods.filter { it.name == "onSingleTapConfirmed" && it.parameterTypes == listOf("Landroid/view/MotionEvent;") && it.returnType == "Z" }.singleOrNull() ?: return false
    val long = owner.methods.filter { it.name == "onLongPress" && it.parameterTypes == single.parameterTypes && it.returnType == "V" }.singleOrNull() ?: return false
    return single.calls(parameters = listOf("I"), returns = "V") && long.calls("Ljava/lang/Runnable;", "run") && long.calls("Lkotlin/jvm/functions/Function1;", "invoke")
}
internal object PortraitSingleTapFingerprint : Fingerprint(name = "onSingleTapConfirmed", returnType = "Z", parameters = listOf("Landroid/view/MotionEvent;"), custom = { _, c -> portraitListener(c) })
internal object PortraitDoubleTapFingerprint : Fingerprint(name = "onDoubleTap", returnType = "Z", parameters = listOf("Landroid/view/MotionEvent;"), custom = { _, c -> portraitListener(c) })
internal object PortraitLongPressFingerprint : Fingerprint(name = "onLongPress", returnType = "V", parameters = listOf("Landroid/view/MotionEvent;"), custom = { _, c -> portraitListener(c) })
