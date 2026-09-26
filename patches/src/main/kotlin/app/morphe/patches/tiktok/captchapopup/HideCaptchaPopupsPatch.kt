/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.captchapopup

import app.morphe.patches.tiktok.shared.discovery.TikTokFingerprint as Fingerprint
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstruction
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.addInstructions
import app.morphe.patches.tiktok.shared.discovery.tiktokBytecodePatch as bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val FEATURE_CONTROLS_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;"
private const val LIVE_CAPTCHA_CALLBACK_DESCRIPTOR = "LX/1NRi;"

private object CaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptchaV2",
    returnType = "V",
    strings = listOf("popCaptchaV2 - riskInfo ="),
    custom = { method, _ ->
        val p = method.parameterTypes.map(CharSequence::toString)
        p.size in 4..5 && p[0] == "Landroid/app/Activity;" && p[1] == "Ljava/lang/String;" &&
            p[2].startsWith("LX/") && p[2].endsWith(";") &&
            p[3] == "Landroidx/fragment/app/Fragment;" &&
            (p.size == 4 || p[4] == "Ljava/lang/String;")
    },
)

private object LegacyCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptcha",
    returnType = "V",
    strings = listOf("popCaptcha - errorcode = "),
    custom = { method, _ ->
        val p = method.parameterTypes.map(CharSequence::toString)
        p.size == 3 && p[0] == "Landroid/app/Activity;" && p[1] == "I" &&
            p[2].startsWith("LX/") && p[2].endsWith(";")
    },
)

private object OecCaptchaPopupFingerprint : Fingerprint(
    definingClass = "Lcom/tts/oecverify/verify/RiskControlService;",
    name = "execute",
    returnType = "Z",
    custom = { method, _ ->
        val parameters = method.parameterTypes.map(CharSequence::toString)
        method.accessFlags == AccessFlags.PUBLIC.value &&
            parameters.size == 2 && parameters[0].startsWith("LX/") &&
            parameters[0].endsWith(";") &&
            parameters[1] == "Lcom/tts/oecverify/BdTuringCallback;"
    },
)

private object LiveHostCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/live/livehostimpl/LiveHostUser;",
    name = "popCaptchaV2",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        LIVE_CAPTCHA_CALLBACK_DESCRIPTOR,
        "Landroidx/fragment/app/Fragment;",
    ),
)

@Suppress("unused")
val hideCaptchaPopupsPatch = bytecodePatch(
    name = "Hide CAPTCHA popups",
    description = "Adds a default-off setting to hide browsing and LIVE puzzle dialogs while preserving login and account verification.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktokVerified())

    execute {
        SettingsStatusLoadFingerprint.uniqueMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCaptchaPopupSuppression()V",
        )

        CaptchaPopupFingerprint.uniqueMethod.let { method ->
            val callback = method.parameterTypes[2]
            method.addInstructions(0,
            """
                invoke-static {p1, p2}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_captcha_popup
                if-eqz p3, :morphe_hide_captcha_popup_return
                invoke-virtual {p3}, $callback->LIZJ()V
                :morphe_hide_captcha_popup_return
                return-void
                :morphe_show_captcha_popup
                nop
            """,
            )
        }

        LegacyCaptchaPopupFingerprint.uniqueMethod.let { method ->
            val callback = method.parameterTypes[2]
            method.addInstructions(0,
            """
                invoke-static {p1}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :morphe_show_legacy_captcha_popup
                if-eqz p3, :morphe_hide_legacy_captcha_popup_return
                invoke-virtual {p3}, $callback->LIZJ()V
                :morphe_hide_legacy_captcha_popup_return
                return-void
                :morphe_show_legacy_captcha_popup
                nop
            """,
            )
        }

        OecCaptchaPopupFingerprint.uniqueMethod.addInstructions(
            0,
            """
                invoke-static {}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup()Z
                move-result v0
                if-eqz v0, :morphe_show_oec_captcha_popup
                const/4 v0, 0x3
                const/4 v1, 0x0
                move-object/from16 v2, p2
                invoke-interface {v2, v0, v1}, Lcom/tts/oecverify/BdTuringCallback;->onFail(ILorg/json/JSONObject;)V
                const/4 v0, 0x1
                return v0
                :morphe_show_oec_captcha_popup
                nop
            """,
        )

        LiveHostCaptchaPopupFingerprint.uniqueMethod.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_live_captcha_popup
                if-eqz p3, :morphe_hide_live_captcha_popup_return
                invoke-interface {p3}, $LIVE_CAPTCHA_CALLBACK_DESCRIPTOR->LIZJ()V
                :morphe_hide_live_captcha_popup_return
                return-void
                :morphe_show_live_captcha_popup
                nop
            """,
        )
    }
}
