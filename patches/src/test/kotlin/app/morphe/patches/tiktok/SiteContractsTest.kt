package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.SiteContracts
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class SiteContractsTest {
    private val publicStatic = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value
    private val publicFinal = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value

    @Test fun feedCommitPinsBothTypedBoundariesAndRejectsOtherMutations() {
        val feed = "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;"
        val obj = "Landroid/os/Message;->obj:Ljava/lang/Object;"
        fun commit(marker: String = "filter_show_ad", duplicate: Boolean = false): ImmutableMethod {
            val body = MethodImplementationBuilder(3).apply {
                listOf("Feed0VVManager@fixture", "full_feed_commit_process_data", "homepage_hot", marker,
                    "filter_installed_ad", "fyp", "soft_ads", "roi2").forEach {
                    addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(it)))
                }
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 2,
                    ImmutableFieldReference("LX/New;", "callable", "Ljava/util/concurrent/Callable;")))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_INTERFACE, 1, 0, 0, 0, 0, 0,
                    ImmutableMethodReference("Ljava/util/concurrent/Callable;", "call", emptyList(), "Ljava/lang/Object;")))
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1))
                addInstruction(BuilderInstruction21c(Opcode.CHECK_CAST, 1, ImmutableTypeReference(feed)))
                addInstruction(BuilderInstruction10x(Opcode.NOP))
                addInstruction(BuilderInstruction21c(Opcode.NEW_INSTANCE, 0, ImmutableTypeReference("Landroid/os/Message;")))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                    ImmutableMethodReference("Landroid/os/Message;", "<init>", emptyList(), "V")))
                addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, 1, 0,
                    ImmutableFieldReference("Landroid/os/Message;", "obj", "Ljava/lang/Object;")))
                if (duplicate) addInstruction(BuilderInstruction22c(Opcode.IPUT_OBJECT, 1, 0,
                    ImmutableFieldReference("Landroid/os/Message;", "obj", "Ljava/lang/Object;")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }
            return ImmutableMethod("LX/New;", "run", emptyList(), "V", publicFinal,
                emptySet(), emptySet(), body.methodImplementation)
        }
        val method = commit()
        assertEquals(SiteContracts.FEED_COMMIT, SiteContracts.mode(method, "LX/07fn;->run()V"))
        assertNull(SiteContracts.mode(commit(marker = "another_feed"), "LX/07fn;->run()V"))
        assertNull(SiteContracts.mode(commit(duplicate = true), "LX/07fn;->run()V"))
        val guard = "Lapp/morphe/extension/tiktok/feedfilter/ForYouFeedGuard;"
        val castHook = "invoke-static/range {v1 .. v1}, $guard->markAndFilter($feed)V"
        val uiHook = "invoke-static/range {v1 .. v1}, $guard->filterBeforeUiCommit(Ljava/lang/Object;)V"
        assertTrue(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 12, castHook))
        assertTrue(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 15, uiHook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 0, castHook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 15, castHook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 12, castHook.replace("v1", "v0")))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.FEED_COMMIT, method, 15, uiHook + "\nreturn-void"))
    }

    @Test fun shareReplacementUsesTheSecondStringParameterAndNoScratch() {
        val args = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
            "Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;")
        val method = ImmutableMethod("LX/New;", "LIZ", args.map { ImmutableMethodParameter(it, emptySet(), null) },
            "Ljava/lang/String;", publicStatic, emptySet(), emptySet(), MethodImplementationBuilder(6).apply {
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("utm_campaign")))
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("share_link_id")))
                addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 3))
            }.methodImplementation)
        val accepted = "LX/1GG6;->LIZ(${args.joinToString("")})Ljava/lang/String;"
        assertEquals(SiteContracts.SHARE_RETURN, SiteContracts.mode(method, accepted))
        val hook = "invoke-static/range {v3 .. v3}, Lapp/morphe/extension/tiktok/share/ShareUrlSanitizer;->stripAllQueryParams(Ljava/lang/String;)Ljava/lang/String;\nmove-result-object v3\nreturn-object v3"
        assertTrue(SiteContracts.mutationAllowed(SiteContracts.SHARE_RETURN, method, 0, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SHARE_RETURN, method, 1, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SHARE_RETURN, method, 0, hook.replace("v3", "v2")))
    }

    @Test fun searchStateTransformStaysAtItsBooleanReturn() {
        val method = ImmutableMethod("LX/New;", "LIZLLL", emptyList(), "Z",
            publicStatic or AccessFlags.FINAL.value, emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("non_personalized_search_state_fixture")))
                addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
                addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            }.methodImplementation)
        assertEquals(SiteContracts.SEARCH_RETURN, SiteContracts.mode(method, "LX/04uK;->LIZLLL()Z"))
        val hook = "invoke-static/range {v0 .. v0}, Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;->enableNonPersonalizedSearch(Z)Z\nmove-result v0"
        assertTrue(SiteContracts.mutationAllowed(SiteContracts.SEARCH_RETURN, method, 2, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SEARCH_RETURN, method, 1, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SEARCH_RETURN, method, 2, hook + "\nreturn v0"))
    }

    @Test fun seekbarTransformChangesOnlyItsIntParameterAtEntry() {
        val method = ImmutableMethod("LX/New;", "setSeekBarShowType",
            listOf(ImmutableMethodParameter("I", emptySet(), null)), "V", publicFinal,
            emptySet(), emptySet(), MethodImplementationBuilder(3).apply {
                addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("seekbar show type change, change to:")))
                addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 1, 0, 0, 0, 0,
                    ImmutableMethodReference("LX/New;", "setCanDrag", listOf("Z"), "V")))
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation)
        assertEquals(SiteContracts.SEEKBAR_PARAMETER, SiteContracts.mode(method, "LX/06W4;->setSeekBarShowType(I)V"))
        val hook = "invoke-static/range {v2 .. v2}, Lapp/morphe/extension/tiktok/seekbar/SeekbarPatch;->overrideSeekbarShowType(I)I\nmove-result v2"
        assertTrue(SiteContracts.mutationAllowed(SiteContracts.SEEKBAR_PARAMETER, method, 0, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SEEKBAR_PARAMETER, method, 1, hook))
        assertFalse(SiteContracts.mutationAllowed(SiteContracts.SEEKBAR_PARAMETER, method, 0, hook.replace("v2", "v1")))
    }
}
