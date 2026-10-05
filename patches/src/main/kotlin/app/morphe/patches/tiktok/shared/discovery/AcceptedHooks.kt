package app.morphe.patches.tiktok.shared.discovery

/** Selection identity only. The selected native method must still pass its full contract. */
internal object AcceptedHooks {
    fun target(hooks: Map<String, String>, hook: String, method: String, selection: String?, candidateCount: Int): String? {
        if (candidateCount != 1 || selection !in setOf("unique", "all-sites")) return null
        val selector = if (selection == "all-sites") {
            if (!hook.endsWith(":$method")) return null
            hook.removeSuffix(":$method")
        } else hook
        hooks[hook]?.let { return it }
        return hooks.entries.filter { (key, value) -> key == selector || key == "$selector:$value" }
            .map { it.value }.distinct().singleOrNull()
    }
}
