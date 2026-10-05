package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.AcceptedHooks
import org.junit.Assert.*
import org.junit.Test

class AcceptedHooksTest {
    private val old = "LX/Old;->render()V"
    private val current = "LX/New;->render()V"
    @Test fun singletonRelocationUsesOneAcceptedSelectorIdentity() {
        val hooks = mapOf("selector" to old, "selector:$old" to old)
        assertEquals(old, AcceptedHooks.target(hooks, "selector:$current", current, "all-sites", 1))
        assertEquals(old, AcceptedHooks.target(hooks, "selector", current, "unique", 1))
        assertEquals(old, AcceptedHooks.target(hooks, "selector:$old", old, "all-sites", 1))
    }
    @Test fun multipleCandidatesOrAmbiguousAcceptedAliasesAreRefused() {
        val hooks = mapOf("selector:$old" to old, "selector:LX/Other;->render()V" to "LX/Other;->render()V")
        assertNull(AcceptedHooks.target(hooks, "selector:$current", current, "all-sites", 1))
        assertNull(AcceptedHooks.target(hooks, "selector:$old", old, "all-sites", 2))
        assertNull(AcceptedHooks.target(hooks, "selector", current, "unique", 0))
    }
    @Test fun aChangedSuffixOrObservationDoesNotGainAnAcceptedMutationIdentity() {
        val hooks = mapOf("selector:$old" to old)
        assertNull(AcceptedHooks.target(hooks, "selector:$old", current, "all-sites", 1))
        assertNull(AcceptedHooks.target(hooks, "selector", current, "explicit-site", 1))
        assertNull(AcceptedHooks.target(hooks, "unknown:$current", current, "all-sites", 1))
    }
}
