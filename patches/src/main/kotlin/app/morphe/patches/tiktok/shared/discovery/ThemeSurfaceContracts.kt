package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*

/** Typed surface hooks preserve native computation, inputs and every return path. */
internal object ThemeSurfaceContracts {
    const val SETTINGS = "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/SettingsComposeRvmpFragment;"
    const val OLD_CHAT = "Lcom/ss/android/ugc/aweme/im/sdk/chat/ui/powerpage/BaseChatRoomFragment;"
    const val CHAT = "Lcom/ss/android/ugc/aweme/im/sdk/chat/ui/singleactivity/BaseChatRootFragment;"
    const val OLD_INBOX = "Lcom/ss/android/ugc/aweme/im/chatlist/impl/ui/viewholder/v2/SessionListBaseVH;"
    const val INBOX = "Lcom/ss/android/ugc/aweme/im/chatlist/impl/ui/viewholder/chatlistvo/ChatListBaseVH;"
    const val NAV = "Lcom/bytedance/tiktok/homepage/mainpagefragment/assem/MainPageBusinessAssem;"
    const val HOME = "Lcom/ss/android/ugc/aweme/main/assems/ui/HomepageViewPagerAssem;"
    const val VIEW = "Landroid/view/View;"
    private const val HOLDER = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;"
    private const val CREATE = "onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"
    const val ROOT_MODE = "experimental-theme-view-return"
    const val BIND_MODE = "experimental-theme-inbox-bind-return"
    const val NAV_MODE = "experimental-theme-navigation-writer"
    const val RENDER_MODE = "experimental-theme-compose-color-read"
    const val VIEW_MODE = "experimental-theme-settings-view-return"
    val modes = setOf(ROOT_MODE, BIND_MODE, NAV_MODE, RENDER_MODE, VIEW_MODE)
    val rootRoles = mapOf(
        "Lcom/ss/android/ugc/profile/business/profile/ui/v2/I18nMyProfileFragment;" to "profilePage",
        "Lcom/ss/android/ugc/aweme/sidebar/profile/ProfileSidebarPageFragment;" to "sidebar",
        "Lcom/ss/android/ugc/aweme/sidebar/SidebarPageFragment;" to "sidebar",
        "Lcom/ss/android/ugc/profile/business/profile/menu/ProfilePageMenuFragment;" to "sidebar",
        "Lcom/ss/android/ugc/aweme/search/middle/AbstractSearchIntermediateFragmentNew;" to "search",
        OLD_CHAT to "chat", CHAT to "chat", SETTINGS to "settings")
    private fun params(m: Method) = m.parameterTypes.map(CharSequence::toString)
    private fun ref(i: Instruction) = (i as? ReferenceInstruction)?.reference
    private fun calls(m: Method) = m.implementation?.instructions?.mapNotNull { ref(it) as? MethodReference }?.toList().orEmpty()
    private fun subtype(type: String, parent: String, resolve: (String) -> ClassDef?): Boolean {
        val pending = ArrayDeque<String>().apply { add(type) }; val seen = hashSetOf<String>()
        while (pending.isNotEmpty()) {
            val t = pending.removeFirst(); if (t == parent) return true
            if (seen.add(t)) resolve(t)?.let { it.superclass?.let(pending::add); it.interfaces.forEach(pending::add) }
        }
        return false
    }
    fun rootBoundary(m: Method, owner: ClassDef, resolve: (String) -> ClassDef?): Boolean =
        owner.type in rootRoles && m.toString() == "${owner.type}->$CREATE" && m.accessFlags == 17 &&
            subtype(owner.type, "Landroidx/fragment/app/Fragment;", resolve) &&
            m.implementation?.instructions?.any { it.opcode == Opcode.RETURN_OBJECT } == true &&
            (owner.type !in setOf(CHAT, OLD_CHAT) || calls(m).any { it.name == "getRootView" && it.returnType == VIEW } &&
                m.implementation!!.instructions.any { (ref(it) as? StringReference)?.string == "charoom_fragment_inflate" })
    fun inboxBoundary(m: Method, owner: ClassDef): Boolean = m.accessFlags == 17 && owner.type in setOf(INBOX, OLD_INBOX) &&
        owner.superclass == HOLDER && m.returnType == "V" && params(m).let { it.size == 2 && it[0].startsWith("LX/") && it[1] == "I" } &&
        m.implementation!!.instructions.any { (ref(it) as? StringReference)?.string == "conversation_load" } &&
        calls(m).any { it.name == "getSessionId" && it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty() } &&
        calls(m).count { it.definingClass == owner.type && it.parameterTypes.size == 2 && it.parameterTypes[1] == "I" && it.returnType == "V" } == 1
    fun homeBoundary(m: Method, resolve: (String) -> ClassDef?): Boolean = runCatching {
        if (m.definingClass != HOME || m.name != "onViewCreated" || params(m) != listOf(VIEW) || m.returnType != "V" || m.accessFlags != 17) return false
        val aliases = ReceiverAliases.atEveryInstruction(m, m.parameterRegister(0, VIEW))
        val types = m.implementation!!.instructions.withIndex().mapNotNull { (n, i) ->
            (ref(i) as? TypeReference)?.type?.takeIf { i.opcode == Opcode.CHECK_CAST && (i as OneRegisterInstruction).registerA in aliases[n].orEmpty() }
        }.distinct()
        types.size == 1 && subtype(types.single(), "Landroid/view/ViewGroup;", resolve)
    }.getOrDefault(false)
    fun explicitTarget(m: Method, owner: ClassDef, resolve: (String) -> ClassDef?): String? = when {
        rootBoundary(m, owner, resolve) -> "${if (m.definingClass == CHAT) OLD_CHAT else m.definingClass}->$CREATE"
        inboxBoundary(m, owner) -> "$OLD_INBOX->Z5(LX/0CcN;I)V"
        navigationSites(m, owner).isNotEmpty() -> "$NAV->" + when {
            m.name == "onViewCreated" -> "onViewCreated(Landroid/view/View;)V"
            m.parameterTypes.isEmpty() -> "sh()V"
            else -> "rc(Z)V"
        }
        else -> null
    }
    fun mode(m: Method, owner: ClassDef, accepted: String, resolve: (String) -> ClassDef?): String? = runCatching {
        if (rootBoundary(m, owner, resolve) && accepted == "${if (owner.type == CHAT) OLD_CHAT else owner.type}->$CREATE") return ROOT_MODE
        if (accepted == "$OLD_INBOX->Z5(LX/0CcN;I)V" && inboxBoundary(m, owner)) { inboxSites(m); return BIND_MODE }
        if (accepted.startsWith("$NAV->") && explicitTarget(m, owner, resolve) == accepted) return NAV_MODE
        if (accepted == "$SETTINGS->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V" &&
            m.toString() == accepted && m.accessFlags == 17 && subtype(owner.type, "Landroidx/fragment/app/Fragment;", resolve)) {
            settingsViewSites(m); return VIEW_MODE
        }
        if (accepted in setOf("LX/0VGt;->LIZ(LX/0VSj;Ljava/util/List;LX/008m;I)V", "LX/0VGt;->LIZIZ(LX/0VSj;Ljava/util/List;LX/008m;I)V") &&
            renderers(resolve(SETTINGS)!!, resolve).any { it.toString() == m.toString() } &&
            m.name == accepted.substringAfter("->").substringBefore('(')) return RENDER_MODE
        null
    }.getOrNull()
    fun rootSites(m: Method): List<ThemeContracts.Site> = m.implementation!!.instructions.withIndex().mapNotNull { (n, i) ->
        if (i.opcode != Opcode.RETURN_OBJECT) null else {
            val r = (i as OneRegisterInstruction).registerA; val role = rootRoles.getValue(m.definingClass)
            ThemeContracts.Site(n, if (role == "settings") "invoke-static/range {v$r .. v$r}, Lapp/morphe/extension/tiktok/theme/ThemeViewHooks;->styleSettingsCompose($VIEW)V"
                else "invoke-static/range {v$r .. v$r}, Lapp/morphe/extension/tiktok/theme/ThemeNativeTargets;->$role($VIEW)V")
        }
    }
    fun inboxSites(m: Method): List<ThemeContracts.Site> {
        val aliases = ReceiverAliases.atEveryInstruction(m)
        return m.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { (n, _) ->
            val receiver = aliases[n]?.minOrNull() ?: throw PatchException("Inbox receiver was overwritten")
            val scratch = ScratchContracts.locals(m, n, setOf(receiver), 1).single()
            ThemeContracts.Site(n, "move-object/from16 v$scratch, v$receiver\niget-object v$scratch, v$scratch, $HOLDER->itemView:$VIEW\n" +
                "invoke-static {v$scratch}, Lapp/morphe/extension/tiktok/theme/ThemeDynamicListGuardV3;->onInboxRowBound($VIEW)V")
        }
    }
    fun settingsViewSites(m: Method): List<ThemeContracts.Site> {
        val aliases = ReceiverAliases.atEveryInstruction(m, m.parameterRegister(0, VIEW))
        return m.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { (n, _) ->
            val r = aliases[n]?.minOrNull() ?: throw PatchException("Settings View input was overwritten")
            ThemeContracts.Site(n, "invoke-static/range {v$r .. v$r}, Lapp/morphe/extension/tiktok/theme/ThemeViewHooks;->styleSettingsCompose($VIEW)V")
        }
    }
    /** Follow the actual Settings-created lambda and its native screen -> group call. */
    fun renderers(settings: ClassDef, resolve: (String) -> ClassDef?): List<Method> {
        val candidates = settings.methods.filter { it.parameterTypes.any { p -> p == "Lcom/ss/android/ugc/aweme/setting/ui/rvmpcompose/SettingsRvmpComposeViewModel;" } }
        val lambdaTypes = candidates.flatMap { m -> m.implementation?.instructions?.mapNotNull {
            (ref(it) as? TypeReference)?.type?.takeIf { _ -> it.opcode == Opcode.NEW_INSTANCE }
        }?.toList().orEmpty() }.distinct()
        fun schema(r: MethodReference): Boolean = r.accessibleSchema()
        val screens = lambdaTypes.flatMap { t -> resolve(t)?.methods?.filter { m ->
            m.implementation?.instructions?.any { (ref(it) as? FieldReference)?.definingClass == SETTINGS } == true
        }?.flatMap { calls(it).filter(::schema) }.orEmpty() }.distinctBy { it.toString() }
        val screen = screens.singleOrThrow("Unique Settings lambda renderer")
        val owner = resolve(screen.definingClass) ?: throw PatchException("Missing native Settings renderer")
        val screenMethod = owner.methods.single { it.toString() == screen.toString() }
        val groupRef = calls(screenMethod).filter { it.definingClass == owner.type && it.toString() != screen.toString() && schema(it) }
            .distinctBy { it.toString() }.singleOrThrow("Unique Settings inner renderer")
        val group = owner.methods.single { it.toString() == groupRef.toString() }
        if (owner.superclass != "Ljava/lang/Object;" || owner.interfaces.isNotEmpty() || listOf(screenMethod, group).any { it.accessFlags != 25 })
            throw PatchException("Changed Settings renderer hierarchy")
        val state = resolve(screen.parameterTypes[0].toString()) ?: throw PatchException("Missing Settings state")
        if (state.fields.map { it.type }.sorted() != listOf("Ljava/lang/String;", "Ljava/lang/String;", "Z", "Z", "Z", "Z", "Lkotlin/jvm/functions/Function1;").sorted())
            throw PatchException("Changed Settings state schema")
        return listOf(group, screenMethod)
    }
    private fun MethodReference.accessibleSchema(): Boolean = parameterTypes.size == 4 && parameterTypes[0].startsWith("LX/") &&
        parameterTypes[1] == "Ljava/util/List;" && parameterTypes[2].startsWith("LX/") && parameterTypes[3] == "I" && returnType == "V"
    fun rendererSites(m: Method, resolve: (String) -> ClassDef?): List<ThemeContracts.Site> = m.implementation!!.instructions.withIndex().mapNotNull { (n, i) ->
        val field = ref(i) as? FieldReference
        if (i.opcode != Opcode.IGET_WIDE || field?.type != "J" || resolve(field.definingClass)?.fields?.count { it.type == "J" }?.let { it >= 200 } != true) null
        else {
            val prev = m.implementation!!.instructions.toList().getOrNull(n - 1)
            val producer = m.implementation!!.instructions.toList().getOrNull(n - 2)
            if (prev?.opcode != Opcode.MOVE_RESULT_OBJECT || (prev as OneRegisterInstruction).registerA != (i as TwoRegisterInstruction).registerB ||
                (ref(producer!!) as? MethodReference)?.returnType != field.definingClass) throw PatchException("Unproven Settings packed color")
            val r = i.registerA
            ThemeContracts.Site(n, "invoke-static/range {v$r .. v${r + 1}}, Lapp/morphe/extension/tiktok/theme/ThemeComposeColorResolver;->mapColor(J)J\nmove-result-wide v$r")
        }
    }
    fun navigationSites(m: Method, owner: ClassDef): List<ThemeContracts.Site> = runCatching {
        if (owner.type != NAV || owner.superclass != "Lcom/bytedance/tiktok/homepage/mainfragment/BaseMainPageFragmentUIAssem;" ||
            m.accessFlags != 17 || m.returnType != "V") return emptyList()
        val insns = m.implementation!!.instructions.toList()
        val initializer = m.name == "onViewCreated" && params(m) == listOf(VIEW)
        if (!initializer && params(m) !in listOf(emptyList<String>(), listOf("Z"))) return emptyList()
        val divider = owner.methods.filter { it.parameterTypes.isEmpty() && it.returnType == "V" && calls(it).any { r -> r.toString() == "$VIEW->setBackgroundColor(I)V" } }.single()
        val dividerField = divider.implementation!!.instructions.mapNotNull { (ref(it) as? FieldReference)?.takeIf { f -> f.definingClass == NAV && f.type == VIEW } }.distinctBy { it.toString() }.single()
        val show = owner.methods.single { it.name == "showBottomTab" && params(it) == listOf("Z") && it.returnType == "V" }
        val getter = calls(show).filter { it.definingClass == NAV && it.parameterTypes.isEmpty() && it.returnType == VIEW }.distinctBy { it.toString() }.single()
        val container = owner.methods.single { it.toString() == getter.toString() }.implementation!!.instructions.mapNotNull {
            (ref(it) as? FieldReference)?.takeIf { f -> f.definingClass == NAV && f.type == VIEW }
        }.distinctBy { it.toString() }.single()
        val receivers = ReceiverAliases.atEveryInstruction(m)
        insns.mapIndexedNotNull { n, i ->
            val f = ref(i) as? FieldReference
            val call = ref(i) as? MethodReference
            val site = when {
                initializer && i.opcode == Opcode.IPUT_OBJECT && f?.toString() in setOf(dividerField.toString(), container.toString()) &&
                    (i as TwoRegisterInstruction).registerB in receivers[n].orEmpty() && insns.getOrNull(n - 1)?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                    (insns[n - 1] as OneRegisterInstruction).registerA == i.registerA &&
                    (ref(insns[n - 2]) as? MethodReference)?.toString() == "$VIEW->findViewById(I)$VIEW" ->
                    i.registerA to if (f.toString() == container.toString()) "navigation" else "navigationDivider"
                !initializer && call?.toString() == "$VIEW->setBackgroundColor(I)V" -> {
                    val r = i.argumentRegisters().first()
                    val read = insns.withIndex().take(n).lastOrNull { it.value.opcode == Opcode.IGET_OBJECT && ref(it.value)?.toString() == dividerField.toString() }
                        ?: throw PatchException("Missing divider value")
                    if ((read.value as TwoRegisterInstruction).registerA != r || (read.value as TwoRegisterInstruction).registerB !in receivers[read.index].orEmpty() ||
                        insns.subList(read.index + 1, n).any { it.opcode.setsRegister() && it is OneRegisterInstruction && it.registerA == r }) throw PatchException("Changed divider receiver")
                    r to "navigationDivider"
                }
                else -> null
            } ?: return@mapIndexedNotNull null
            ThemeContracts.Site(n, "invoke-static/range {v${site.first} .. v${site.first}}, Lapp/morphe/extension/tiktok/theme/ThemeNativeTargets;->${site.second}($VIEW)V")
        }
    }.getOrDefault(emptyList())
    fun mutationAllowed(mode: String, m: Method, owner: ClassDef, index: Int?, code: String?, resolve: (String) -> ClassDef?): Boolean = runCatching {
        val sites = when (mode) {
            ROOT_MODE -> rootSites(m)
            BIND_MODE -> inboxSites(m)
            VIEW_MODE -> settingsViewSites(m)
            NAV_MODE -> navigationSites(m, owner)
            RENDER_MODE -> rendererSites(m, resolve)
            else -> emptyList()
        }
        sites.any { it.index == index && it.code.filterNot(Char::isWhitespace) == code?.filterNot(Char::isWhitespace) }
    }.getOrDefault(false)
}
