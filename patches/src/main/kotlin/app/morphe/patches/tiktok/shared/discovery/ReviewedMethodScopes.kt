package app.morphe.patches.tiktok.shared.discovery

import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method

/** Whole bodies, parameter layout, referenced fields and hierarchy were compared
 * against both pinned original APKs. Unrelated class members are not edited.
 * This list never authorizes a changed method body or selects a missing fingerprint.
 */
internal object ReviewedMethodScopes {
    val hooks = setOf(
        "LX/0C0Y;-><clinit>()V",
        "LX/05nh;->dispatchDraw(Landroid/graphics/Canvas;)V",
        "LX/0HK0;->LJ(Ljava/util/List;LX/0Hea;Z)V",
        "Lcom/ss/android/ugc/aweme/video/simplayer/PlayerSettingServiceImpl;->get(Ljava/lang/String;Ljava/lang/reflect/Type;Ljava/lang/Object;ZZ)Ljava/lang/Object;",
        "LX/02z2;->LIZ(ILjava/lang/String;ZZ)Z",
        "LX/02z2;->LIZJ(DILjava/lang/String;Z)D",
        "LX/02z2;->LIZLLL(ILjava/lang/String;ZF)F",
        "LX/02z2;->LJII(IJLjava/lang/String;Z)J",
        "LX/02z2;->LJIIIIZZ(ILjava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;",
        "LX/02z2;->LJIIJJI(Ljava/lang/String;Z)Ljava/lang/Object;",
        "LX/0547;->LIZ(ILandroid/content/Context;)Ljava/lang/Integer;",
        "LX/0547;->LIZIZ(ILandroid/content/Context;Lkotlin/jvm/functions/Function1;)Ljava/lang/Object;",
        "LX/0547;->LIZJ(ILandroid/content/Context;)Ljava/lang/Integer;",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getBooleanValue(Ljava/lang/String;Z)Z",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getDoubleValue(Ljava/lang/String;D)D",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getFloatValue(Ljava/lang/String;F)F",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getIntValue(Ljava/lang/String;I)I",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getLongValue(Ljava/lang/String;J)J",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getStringArrayValue(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getStringValue(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getValueSafely(Ljava/lang/Class;)Ljava/lang/Object;",
        "Lcom/bytedance/android/live_settings/SettingsManager;->getValueSafely(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LIZ(Ljava/lang/String;Z)Z",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LIZIZ(Ljava/lang/String;D)D",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LIZJ(Ljava/lang/String;F)F",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LJ(Ljava/lang/String;I)I",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LJFF(Ljava/lang/String;J)J",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LJI(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LJII(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
        "Lcom/bytedance/ies/abmock/SettingsManager;->LJIIIIZZ(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;",
        "Lcom/ss/android/ugc/feed/platform/panel/clearmode/ClearModePanelComponent;->onDisplayAdded(I)V",
        "Lcom/ss/android/ugc/feed/platform/panel/clearmode/ClearModePanelComponent;->onDisplayRemoved(I)V",
        "Lcom/ss/android/vesdk/VEConfigCenter;->getValue(Ljava/lang/String;F)F",
        "Lcom/ss/android/vesdk/VEConfigCenter;->getValue(Ljava/lang/String;J)J",
    )

    fun candidates(owner: ClassDef, method: Method, contracts: FixtureContracts.Reviewed): List<String> =
        hooks.filter { accepted ->
            val oldOwner = accepted.substringBefore("->")
            val identityMatches = if (oldOwner.startsWith("LX/"))
                owner.type.startsWith("LX/")
            else accepted == method.toString()
            identityMatches && contracts.portableMethods[accepted] == FixtureContracts.portableSignature(method) &&
                contracts.scopedMethods[accepted] == FixtureContracts.portableReturnScopeSignature(owner, method)
        }
}
