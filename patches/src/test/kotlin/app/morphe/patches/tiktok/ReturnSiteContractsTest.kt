package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import org.junit.Assert.*
import org.junit.Test

class ReturnSiteContractsTest {
    private val type = "Lcom/ss/android/vesdk/VEConfigCenter;"
    private fun ve(clobber: Boolean = false): ImmutableMethod = ImmutableMethod(type, "getValue",
        listOf("Ljava/lang/String;", "I").map { ImmutableMethodParameter(it, emptySet(), null) },
        "I", AccessFlags.PUBLIC.value, emptySet(), emptySet(), MethodImplementationBuilder(4).apply {
            addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                ImmutableFieldReference(type, "sConfigs", "Ljava/util/HashMap;")))
            addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 3, 2, 3, 0, 0, 0,
                ImmutableMethodReference("Lcom/ss/android/vesdk/VEGetABManager;", "getIntValue",
                    listOf("Ljava/lang/String;", "I", "Z"), "I")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
            if (clobber) addInstruction(BuilderInstruction11n(Opcode.CONST_4, 2, 0))
            addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        }.methodImplementation)
    private fun owner(m: ImmutableMethod) = ImmutableClassDef(m.definingClass, AccessFlags.PUBLIC.value,
        "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), listOf(m))
    @Test fun changedConfigBodyAllowsOnlyTheExactTypedReturnOverride() {
        val m = ve()
        assertEquals(ReturnSiteContracts.VE, ReturnSiteContracts.mode(m, owner(m), m.toString()))
        val code = "invoke-static {p1, v0}, Lapp/morphe/extension/tiktok/featuregatelab/FeatureGateLabRuntime;->overrideVeInt(Ljava/lang/String;I)I\nmove-result v0"
        assertTrue(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.VE, m, 4, code))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.VE, m, 2, code))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.VE, m, 4, code.replace("p1", "p2")))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.VE, m, 4, code.replace("move-result v0", "move-result v1")))
    }
    @Test fun aReusedKeyParameterRefusesEveryReturnHook() {
        val m = ve(true)
        assertNull(ReturnSiteContracts.mode(m, owner(m), m.toString()))
    }
    @Test fun anUnrelatedExplicitMethodDoesNotGainAnAlias() {
        assertNull(ReturnSiteContracts.explicitTarget(ImmutableMethod(type, "other", emptyList(), "I", 1,
            emptySet(), emptySet(), ve().implementation)))
    }
    @Test fun renamedFollowItemsFieldKeepsTheTypedReceiverBoundary() {
        val follow = "Lcom/ss/android/ugc/aweme/follow/presenter/FollowFeedList;"
        val m = ImmutableMethod(follow, "getItems", emptyList(), "Ljava/util/List;", 17,
            emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
                addInstruction(BuilderInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                    ImmutableFieldReference(follow, "renamedItems", "Ljava/util/List;")))
                addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            }.methodImplementation)
        val field = ImmutableField(follow, "renamedItems", "Ljava/util/List;", 1, null, emptySet(), emptySet())
        val owner = ImmutableClassDef(follow, 1, "Lcom/ss/android/ugc/aweme/base/api/BaseResponse;",
            emptyList(), null, emptySet(), listOf(field), listOf(m))
        assertEquals(ReturnSiteContracts.ITEMS, ReturnSiteContracts.mode(m, owner, m.toString()))
        val code = "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/tiktok/feedfilter/FeedItemsFilter;->filter($follow)V"
        assertTrue(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.ITEMS, m, 1, code))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.ITEMS, m, 1, code.replace("p0", "v0")))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.ITEMS, m, 0, code))
    }

    @Test fun schemaUriOverloadKeepsAllInputsAndWrapsOnlyTheReturnedString() {
        val ownerType = "Lcom/ss/android/ugc/tiktok/pns/activitycenter/EnterActivityCenterAction;"
        val params = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Landroid/net/Uri;")
        val b = MethodImplementationBuilder(8)
        for (marker in listOf("activity_center_entrance_v2", "special", "schemaPath", "url", "urlQuery", "schemaQuery"))
            b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference(marker)))
        b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 0, 1, 1, 0, 0,
            ImmutableMethodReference("Landroid/net/Uri${'$'}Builder;", "appendQueryParameter",
                listOf("Ljava/lang/String;", "Ljava/lang/String;"), "Landroid/net/Uri${'$'}Builder;")))
        b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 2, 0))
        b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 2))
        val m = ImmutableMethod(ownerType, "getSchema", params.map { ImmutableMethodParameter(it, emptySet(), null) },
            "Ljava/lang/String;", 18, emptySet(), emptySet(), b.methodImplementation)
        val accepted = ReturnSiteContracts.explicitTarget(m)!!
        assertEquals(ReturnSiteContracts.SCHEMA, ReturnSiteContracts.mode(m, owner(m), accepted))
        val code = "invoke-static {v2}, Lapp/morphe/extension/tiktok/featuregatelab/FeatureGateLabRuntime;->transformActivityCenterSchema(Ljava/lang/String;)Ljava/lang/String;\nmove-result-object v2"
        assertTrue(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.SCHEMA, m, 8, code))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.SCHEMA, m, 7, code))
        assertFalse(ReturnSiteContracts.mutationAllowed(ReturnSiteContracts.SCHEMA, m, 8, code.replace("v2", "p1")))
        val unrelated = ImmutableMethod(ownerType, "getSchema",
            (params.dropLast(1) + "Ljava/lang/Object;").map { ImmutableMethodParameter(it, emptySet(), null) },
            "Ljava/lang/String;", 18, emptySet(), emptySet(), b.methodImplementation)
        assertNull(ReturnSiteContracts.explicitTarget(unrelated))
    }

}
