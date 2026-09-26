package app.morphe.patches.tiktok

import app.morphe.patches.tiktok.shared.discovery.*
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.removeInstructions
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.returnEarly
import app.morphe.patches.tiktok.shared.discovery.ContractInstructions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.SwitchLabelElement
import com.android.tools.smali.dexlib2.builder.instruction.*
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.junit.Assert.*
import org.junit.Test

class HookContractsTest {
    private fun method(b: MethodImplementationBuilder, params: List<String> = emptyList()) = MutableMethod(
        ImmutableMethod("LX/Fixture;", "renamed", params.map { ImmutableMethodParameter(it, emptySet(), null) }, "I",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(), b.methodImplementation))

    @Test fun missingAndAmbiguousCandidatesFail() {
        assertThrows(PatchException::class.java) { emptyList<String>().singleOrThrow("missing") }
        assertThrows(PatchException::class.java) { listOf("a", "b").singleOrThrow("ambiguous") }
        assertEquals("only", listOf("only").singleOrThrow("unique"))
    }

    @Test fun derivesWideParametersAndChangedResultRegisters() {
        for (registers in listOf(7, 13, 23)) {
            val b = MethodImplementationBuilder(registers)
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, ImmutableMethodReference("Ljava/lang/Float;", "value", emptyList(), "F")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, registers - 5))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, registers - 5))
            val m = method(b, listOf("J", "I", "Ljava/lang/Object;"))
            assertEquals(registers - 2, m.parameterRegister(1, "I"))
            assertEquals(registers - 5, m.resultRegister(0, "F"))
            assertThrows(PatchException::class.java) { m.resultRegister(0, "Ljava/lang/Object;") }
            assertThrows(PatchException::class.java) { m.parameterRegister(1, "J") }
        }
    }

    @Test fun protectsBranchesSwitchesAndExceptionEntries() {
        for (kind in listOf("goto", "if", "packed", "sparse", "catch")) {
            val b = MethodImplementationBuilder(2)
            val start = b.addLabel("start"); val target = b.getLabel("return")
            when (kind) {
                "goto" -> b.addInstruction(BuilderInstruction10t(Opcode.GOTO, target))
                "if" -> b.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, target))
                "packed" -> b.addInstruction(BuilderInstruction31t(Opcode.PACKED_SWITCH, 0, b.getLabel("payload")))
                "sparse" -> b.addInstruction(BuilderInstruction31t(Opcode.SPARSE_SWITCH, 0, b.getLabel("payload")))
                else -> b.addInstruction(BuilderInstruction10x(Opcode.NOP))
            }
            val end = b.addLabel("end")
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            b.addLabel("return"); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 1))
            if (kind == "packed") { b.addLabel("payload"); b.addInstruction(BuilderPackedSwitchPayload(0, listOf(target))) }
            if (kind == "sparse") { b.addLabel("payload"); b.addInstruction(BuilderSparseSwitchPayload(listOf(SwitchLabelElement(9, target)))) }
            if (kind == "catch") b.addCatch("Ljava/lang/Throwable;", start, end, target)
            val m = method(b); val body = m.implementation!!; val location = body.instructions[2].location
            m.insertAtTarget(2, "invoke-static {}, Ltest/Hook;->run()V")
            assertEquals(kind, Opcode.NOP, location.instruction!!.opcode)
            assertEquals(Opcode.INVOKE_STATIC, body.instructions[location.index + 1].opcode)
            assertEquals(Opcode.RETURN, body.instructions[location.index + 2].opcode)
            assertEquals(2, body.instructions.count { it.opcode == Opcode.RETURN })
        }
    }

    @Test fun cannotSplitResultOrExceptionProducer() {
        for (opcode in listOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_EXCEPTION)) {
            val b = MethodImplementationBuilder(2)
            b.addInstruction(BuilderInstruction11x(opcode, 0)); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            assertThrows(PatchException::class.java) { method(b).insertAtTarget(0, "nop") }
        }
    }

    @Test fun fixtureContractRejectsChangedRegistersAndLiterals() {
        fun fixture(count: Int, value: Int): MutableMethod {
            val b = MethodImplementationBuilder(count)
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, value)); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            return method(b)
        }
        val baseline = fixture(2, 1); val hash = FixtureContracts.signature(baseline)
        FixtureContracts.requireMatch(baseline, hash)
        assertThrows(PatchException::class.java) { FixtureContracts.requireMatch(fixture(3, 1), hash) }
        assertThrows(PatchException::class.java) { FixtureContracts.requireMatch(fixture(2, 0), hash) }
        assertThrows(PatchException::class.java) { FixtureContracts.requireMatch(baseline, null) }
    }

    @Test fun experimentalSelectionNeverPromotesAnUnknownShaToVerified() {
        val baseline = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap())
        val verified = FixtureContracts.select(baseline.packageName, baseline.versionCode,
            baseline.apkSha256, baseline, baseline, experimentalOptIn = false)
        assertFalse(verified.experimental)
        assertThrows(PatchException::class.java) {
            FixtureContracts.select(baseline.packageName, baseline.versionCode, "new-sha",
                baseline, baseline, experimentalOptIn = false)
        }
        val candidate = FixtureContracts.select(baseline.packageName, 2024701030, "new-sha",
            null, baseline, experimentalOptIn = true)
        assertTrue(candidate.experimental)
        assertSame(baseline, candidate.contracts)
        assertThrows(PatchException::class.java) {
            FixtureContracts.select("com.ss.android.ugc.trill", 2024701030, "new-sha",
                null, baseline, experimentalOptIn = true)
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.select(baseline.packageName, 123, baseline.apkSha256,
                baseline, baseline, experimentalOptIn = false)
        }
    }

    @Test fun classContractCoversFieldTypeSuperclassAndMethodSet() {
        fun owner(superclass: String, fields: List<ImmutableField>, methods: List<MutableMethod> = emptyList()) =
            ImmutableClassDef("LX/Fixture;", AccessFlags.PUBLIC.value, superclass,
                emptyList(), null, emptySet(), fields, methods)
        val field = ImmutableField("LX/Fixture;", "state", "I", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())
        val changed = ImmutableField("LX/Fixture;", "state", "J", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())
        val baseline = FixtureContracts.classSignature(owner("Ljava/lang/Object;", listOf(field)))
        assertNotEquals(baseline, FixtureContracts.classSignature(owner("Ljava/lang/Object;", listOf(changed))))
        assertNotEquals(baseline, FixtureContracts.classSignature(owner("Ljava/lang/Number;", listOf(field))))
    }

    @Test fun experimentalNativeHookRequiresIdenticalClassAndFullOriginalMethod() {
        fun fixture(registers: Int): MutableMethod {
            val b = MethodImplementationBuilder(registers)
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            return method(b)
        }
        fun owner(superclass: String, target: MutableMethod) = ImmutableClassDef(
            "LX/Fixture;", AccessFlags.PUBLIC.value, superclass,
            emptyList(), null, emptySet(), emptyList(), listOf(target))
        val accepted = fixture(3)
        val acceptedOwner = owner("Ljava/lang/Object;", accepted)
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", mapOf(accepted.toString() to FixtureContracts.signature(accepted)),
            mapOf(acceptedOwner.type to FixtureContracts.classSignature(acceptedOwner)))
        FixtureContracts.requireNativeMatch(accepted, acceptedOwner, reviewed, experimental = true)
        val changedRegisters = fixture(4)
        assertThrows(PatchException::class.java) {
            FixtureContracts.requireNativeMatch(changedRegisters, owner("Ljava/lang/Object;", changedRegisters),
                reviewed, experimental = true)
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requireNativeMatch(accepted, owner("Ljava/lang/Number;", accepted),
                reviewed, experimental = true)
        }
    }

    @Test fun relocatedNativeHookRequiresIdenticalPortableBodyAndClassShape() {
        fun hooked(ownerName: String, methodName: String, registers: Int = 2, constant: Int = 1,
                   superclass: String = "Ljava/lang/Object;", classFlags: Int = AccessFlags.PUBLIC.value): Pair<MutableMethod, ImmutableClassDef> {
            val b = MethodImplementationBuilder(registers)
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, constant))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            val m = MutableMethod(ImmutableMethod(ownerName, methodName, emptyList(), "I",
                AccessFlags.STATIC.value, emptySet(), emptySet(), b.methodImplementation))
            return m to ImmutableClassDef(ownerName, classFlags, superclass,
                emptyList(), null, emptySet(), emptyList(), listOf(m))
        }
        val (accepted, oldOwner) = hooked("LX/0AAA;", "LIZ")
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(accepted.toString() to FixtureContracts.portableSignature(accepted)),
            mapOf(oldOwner.type to FixtureContracts.portableClassSignature(oldOwner)))
        val (renamed, newOwner) = hooked("LX/0BBB;", "LJII")
        FixtureContracts.requirePortableMatch(renamed, newOwner, accepted.toString(), reviewed)
        for ((method, owner) in listOf(
            hooked("LX/0BBB;", "LJII", registers = 3),
            hooked("LX/0BBB;", "LJII", constant = 2),
            hooked("LX/0BBB;", "LJII", superclass = "Ljava/lang/Number;"),
            hooked("LX/0BBB;", "LJII", classFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value))) {
            val error = assertThrows(PatchException::class.java) {
                FixtureContracts.requirePortableMatch(method, owner, accepted.toString(), reviewed)
            }
            assertTrue(error.message!!.contains("class ("))
            assertEquals(FixtureContracts.portableSignature(method) != FixtureContracts.portableSignature(accepted),
                error.message!!.contains("method ("))
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(renamed, newOwner, "LX/other;->LIZ()I", reviewed)
        }
    }

    @Test fun oecCallbackParameterRenameStillRequiresWholeMethodAndClassContract() {
        val callback = "Lcom/tts/oecverify/BdTuringCallback;"
        fun target(input: String, callbackType: String = callback, registerCount: Int = 5) =
            MutableMethod(ImmutableMethod("Lcom/tts/oecverify/verify/RiskControlService;", "execute",
                listOf(input, callbackType).map { ImmutableMethodParameter(it, emptySet(), null) },
                "Z", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
                MethodImplementationBuilder(registerCount).apply {
                    addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
                    addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
                }.methodImplementation))
        fun owner(method: MutableMethod, iface: String) = ImmutableClassDef(method.definingClass,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, "Ljava/lang/Object;",
            listOf(iface), null, emptySet(), emptyList(), listOf(method))
        val original = target("LX/16eW;")
        val renamed = target("LX/1PbY;")
        val originalOwner = owner(original, "LX/0AaA;")
        val renamedOwner = owner(renamed, "LX/08ps;")
        assertEquals(FixtureContracts.portableSignature(original), FixtureContracts.portableSignature(renamed))
        assertEquals(FixtureContracts.portableClassSignature(originalOwner),
            FixtureContracts.portableClassSignature(renamedOwner))
        val accepted = original.toString()
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(accepted to FixtureContracts.portableSignature(original)),
            mapOf(originalOwner.type to FixtureContracts.portableClassSignature(originalOwner)))
        FixtureContracts.requirePortableMatch(renamed, renamedOwner, accepted, reviewed)
        assertThrows(PatchException::class.java) {
            val changed = target("LX/1PbY;", registerCount = 6)
            FixtureContracts.requirePortableMatch(changed, owner(changed, "LX/08ps;"), accepted, reviewed)
        }
        assertThrows(PatchException::class.java) {
            val changed = target("LX/1PbY;", "Lother/Callback;")
            FixtureContracts.requirePortableMatch(changed, owner(changed, "LX/08ps;"), accepted, reviewed)
        }
    }

    @Test fun portableSignatureProtectsBranchesSwitchCasesAndExceptionHandlers() {
        fun branched(change: Boolean): MutableMethod {
            val b = MethodImplementationBuilder(2)
            val first = b.getLabel("first"); val second = b.getLabel("second")
            b.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 0, if (change) second else first))
            b.addLabel("first"); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            b.addLabel("second"); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 1))
            return method(b)
        }
        assertNotEquals(FixtureContracts.portableSignature(branched(false)),
            FixtureContracts.portableSignature(branched(true)))
        fun switched(value: Int): MutableMethod {
            val b = MethodImplementationBuilder(2)
            val target = b.getLabel("return")
            b.addInstruction(BuilderInstruction31t(Opcode.SPARSE_SWITCH, 0, b.getLabel("payload")))
            b.addLabel("return"); b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            b.addLabel("payload"); b.addInstruction(BuilderSparseSwitchPayload(listOf(SwitchLabelElement(value, target))))
            return method(b)
        }
        assertNotEquals(FixtureContracts.portableSignature(switched(1)), FixtureContracts.portableSignature(switched(2)))
        fun caught(exceptionType: String): MutableMethod {
            val b = MethodImplementationBuilder(2)
            val start = b.addLabel("start")
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            val end = b.addLabel("end")
            val handler = b.addLabel("handler")
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 1))
            b.addCatch(exceptionType, start, end, handler)
            return method(b)
        }
        assertNotEquals(FixtureContracts.portableSignature(caught("Ljava/lang/Exception;")),
            FixtureContracts.portableSignature(caught("Ljava/lang/Error;")))
    }

    @Test fun methodScopeAllowsUnrelatedClassGrowthButPinsHierarchyAndReferencedFields() {
        assertEquals(FixtureContracts.methodScopedHooks(), FixtureContracts.load("46.7.3").scopedMethods.keys)
        val className = "Lcom/ss/ttvideoengine/TTVideoEngine;"
        val acceptedKey = "$className->setLooping(Z)V"
        fun target(): MutableMethod {
            val body = MethodImplementationBuilder(2)
            body.addInstruction(BuilderInstruction21c(Opcode.SGET, 0,
                ImmutableFieldReference(className, "state", "I")))
            body.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            return MutableMethod(ImmutableMethod(className, "setLooping",
                listOf(ImmutableMethodParameter("Z", emptySet(), null)), "V", AccessFlags.PUBLIC.value,
                emptySet(), emptySet(), body.methodImplementation))
        }
        fun owner(method: MutableMethod, extra: Boolean = false, superclass: String = "Ljava/lang/Object;",
                  fieldType: String = "I") = ImmutableClassDef(className, AccessFlags.PUBLIC.value,
            superclass, emptyList(), null, emptySet(),
            listOf(ImmutableField(className, "state", fieldType, AccessFlags.PUBLIC.value,
                null, emptySet(), emptySet())) + if (extra) listOf(ImmutableField(className,
                "unrelated", "J", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())) else emptyList(),
            listOf(method))
        val accepted = target()
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(acceptedKey to FixtureContracts.portableSignature(accepted)),
            mapOf(className to FixtureContracts.portableClassSignature(owner(accepted))),
            scopedMethods = mapOf(acceptedKey to FixtureContracts.portableScopeSignature(owner(accepted), accepted)))
        val candidate = target()
        assertEquals("experimental-method-scope",
            FixtureContracts.requirePortableMatch(candidate, owner(candidate, extra = true), acceptedKey, reviewed))
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(candidate, owner(candidate, extra = true,
                superclass = "Ljava/lang/Number;"), acceptedKey, reviewed)
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(candidate, owner(candidate, extra = true,
                fieldType = "J"), acceptedKey, reviewed)
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(candidate, owner(candidate, extra = true),
                "LX/Other;->setLooping(Z)V", reviewed)
        }
        val changed = MutableMethod(ImmutableMethod(className, "setLooping",
            listOf(ImmutableMethodParameter("Z", emptySet(), null)), "V", AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), MethodImplementationBuilder(2).apply {
                addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            }.methodImplementation))
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(changed, owner(changed, extra = true), acceptedKey, reviewed)
        }
    }

    @Test fun memberRenameContractIgnoresOnlyAppCalleeNames() {
        val reviewed = FixtureContracts.load("46.7.3")
        assertEquals(FixtureContracts.memberRenameHooks(), reviewed.memberRenameMethods.keys)
        assertEquals(reviewed.memberRenameMethods.keys, reviewed.memberRenameScopes.keys)
        fun calling(name: String, owner: String = "Lcom/bytedance/pumbaa/utility/method_id/MethodIDManager;",
                    registers: Int = 2, constant: Int = 1): MutableMethod {
            val b = MethodImplementationBuilder(registers)
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, constant))
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference(owner, name, listOf("I"), "Z")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            return method(b)
        }
        val obfuscated = calling("LJI")
        assertNotEquals(FixtureContracts.portableSignature(obfuscated),
            FixtureContracts.portableSignature(calling("push")))
        assertEquals(FixtureContracts.portableMemberSignature(obfuscated),
            FixtureContracts.portableMemberSignature(calling("push")))
        for (changed in listOf(calling("push", owner = "Lother/MethodIDManager;"),
            calling("push", registers = 3), calling("push", constant = 2))) {
            assertNotEquals(FixtureContracts.portableMemberSignature(obfuscated),
                FixtureContracts.portableMemberSignature(changed))
        }
    }

    @Test fun relocatedOfflineProviderKeepsMethodAndReferencedFieldContracts() {
        val acceptedKey = "LX/0AIU;->LJFF()Ljava/util/List;"
        fun provider(ownerName: String): MutableMethod {
            val b = MethodImplementationBuilder(2)
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ImmutableMethodReference(ownerName, "LIZIZ", emptyList(), "Z")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 0))
            b.addInstruction(BuilderInstruction21c(Opcode.SGET_OBJECT, 0,
                ImmutableFieldReference(ownerName, "LLL", "Ljava/util/List;")))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
            return MutableMethod(ImmutableMethod(ownerName, "LJFF", emptyList(), "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(), b.methodImplementation))
        }
        fun owner(method: MutableMethod, extra: Boolean = false, fieldType: String = "Ljava/util/List;") =
            ImmutableClassDef(method.definingClass, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                "Ljava/lang/Object;", emptyList(), null, emptySet(),
                listOf(ImmutableField(method.definingClass, "LLL", fieldType, AccessFlags.PUBLIC.value,
                    null, emptySet(), emptySet())) + if (extra) listOf(ImmutableField(method.definingClass,
                    "unrelated", "I", AccessFlags.PUBLIC.value, null, emptySet(), emptySet())) else emptyList(),
                listOf(method))
        val accepted = provider("LX/0AIU;")
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(acceptedKey to FixtureContracts.portableSignature(accepted)),
            mapOf(accepted.definingClass to FixtureContracts.portableClassSignature(owner(accepted))),
            scopedMethods = mapOf(acceptedKey to FixtureContracts.portableMethodScopeSignature(owner(accepted), accepted, acceptedKey)))
        val candidate = provider("LX/09zC;")
        assertEquals("experimental-method-scope", FixtureContracts.requirePortableMatch(candidate,
            owner(candidate, extra = true), acceptedKey, reviewed))
        assertThrows(PatchException::class.java) { FixtureContracts.requirePortableMatch(candidate,
            owner(candidate, extra = true, fieldType = "Ljava/util/Set;"), acceptedKey, reviewed) }
    }

    @Test fun entryContractPermitsLaterBodyChangesButPinsContextBoundary() {
        val ownerName = "Lcom/ss/android/ugc/aweme/legoImp/task/JatoInitTask;"
        val acceptedKey = "$ownerName->run(Landroid/content/Context;)V"
        fun task(tail: Int, entryRegister: Int = 0): MutableMethod {
            val b = MethodImplementationBuilder(5)
            val afterGuard = b.getLabel("afterGuard")
            b.addInstruction(BuilderInstruction22x(Opcode.MOVE_OBJECT_FROM16, entryRegister, 4))
            b.addInstruction(BuilderInstruction21t(Opcode.IF_NEZ, entryRegister, afterGuard))
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            b.addLabel("afterGuard")
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, tail))
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            return MutableMethod(ImmutableMethod(ownerName, "run",
                listOf(ImmutableMethodParameter("Landroid/content/Context;", emptySet(), null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                emptySet(), emptySet(), b.methodImplementation))
        }
        fun owner(method: MutableMethod, superclass: String = "Ljava/lang/Object;") =
            ImmutableClassDef(ownerName, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                superclass, emptyList(), null, emptySet(), emptyList(), listOf(method))
        val accepted = task(1)
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(acceptedKey to FixtureContracts.portableSignature(accepted)),
            mapOf(ownerName to FixtureContracts.portableClassSignature(owner(accepted))),
            entryMethods = mapOf(acceptedKey to FixtureContracts.entrySignature(owner(accepted), accepted)))
        val changedLater = task(2)
        assertEquals("experimental-entry-contract", FixtureContracts.requirePortableMatch(
            changedLater, owner(changedLater), acceptedKey, reviewed))
        val changedEntry = task(2, entryRegister = 1)
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(changedEntry, owner(changedEntry), acceptedKey, reviewed)
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(changedLater,
                owner(changedLater, superclass = "Ljava/lang/Number;"), acceptedKey, reviewed)
        }
        assertEquals(FixtureContracts.entryHooks(), FixtureContracts.load("46.7.3").entryMethods.keys)
    }

    @Test fun cachedFeedEntryPinsOriginalParameterAndNullBranchAcrossBodyChanges() {
        val acceptedKey = "LX/04Ju;->LIZIZ(Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;)V"
        fun method(owner: String, registers: Int, extra: Boolean = false,
                   wrongBranch: Boolean = false, wrongParameter: Boolean = false): MutableMethod {
            val b = MethodImplementationBuilder(registers)
            val end = b.getLabel("end")
            val other = b.getLabel("other")
            val p0 = registers - 1
            b.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ,
                if (wrongParameter) p0 - 1 else p0, if (wrongBranch) other else end))
            b.addLabel("other")
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, p0, 0, 0, 0, 0,
                ImmutableMethodReference("Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;",
                    "getItems", emptyList(), "Ljava/util/List;")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            for (marker in listOf("fetchFeeds, filter by is ad", "fetchFeeds, filter by is duplicate"))
                b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(marker)))
            if (extra) b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
            b.addLabel("end")
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            return MutableMethod(ImmutableMethod(owner, "LIZIZ",
                listOf(ImmutableMethodParameter("Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;", emptySet(), null)),
                "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet(), emptySet(), b.methodImplementation))
        }
        fun owner(method: MutableMethod, superclass: String = "Ljava/lang/Object;") = ImmutableClassDef(
            method.definingClass, AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            superclass, emptyList(), null, emptySet(), emptyList(), listOf(method))
        val original = method("LX/04Ju;", 8)
        val candidate = method("LX/04iE;", 10, extra = true)
        assertTrue(FixtureContracts.cachedFeedEntryBoundary(owner(original), original))
        assertTrue(FixtureContracts.cachedFeedEntryBoundary(owner(candidate), candidate))
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(acceptedKey to FixtureContracts.portableSignature(original)),
            mapOf(original.definingClass to FixtureContracts.portableClassSignature(owner(original))))
        assertEquals("experimental-feed-entry", FixtureContracts.requirePortableMatch(
            candidate, owner(candidate), acceptedKey, reviewed))
        for (changed in listOf(method("LX/04iE;", 10, wrongBranch = true),
            method("LX/04iE;", 10, wrongParameter = true))) {
            assertFalse(FixtureContracts.cachedFeedEntryBoundary(owner(changed), changed))
            assertThrows(PatchException::class.java) {
                FixtureContracts.requirePortableMatch(changed, owner(changed), acceptedKey, reviewed)
            }
        }
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(candidate, owner(candidate, superclass = "Ljava/lang/Number;"),
                acceptedKey, reviewed)
        }
    }

    @Test fun captchaEntryFollowsCallbackTypeAndRejectsChangedCallback() {
        val sec = "Lcom/ss/android/ugc/aweme/sec/SecApiImpl;"
        val acceptedKey = "$sec->popCaptchaV2(Landroid/app/Activity;Ljava/lang/String;LX/17qC;Landroidx/fragment/app/Fragment;)V"
        fun callback(type: String, noop: Boolean = true): ImmutableClassDef {
            val b = MethodImplementationBuilder(1)
            if (!noop) b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 0))
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            val method = ImmutableMethod(type, "LIZJ", emptyList(), "V", AccessFlags.PUBLIC.value,
                emptySet(), emptySet(), b.methodImplementation)
            return ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
                emptyList(), null, emptySet(), emptyList(), listOf(method))
        }
        fun popup(type: String, extra: Boolean, marker: String = "popCaptchaV2 - riskInfo = "): MutableMethod {
            val b = MethodImplementationBuilder(if (extra) 14 else 8)
            b.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ImmutableMethodReference("LX/123;", "LIZ", emptyList(), "Ljava/lang/StringBuilder;")))
            b.addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0))
            b.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(marker)))
            b.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            val params = listOf("Landroid/app/Activity;", "Ljava/lang/String;", type,
                "Landroidx/fragment/app/Fragment;") + if (extra) listOf("Ljava/lang/String;") else emptyList()
            return MutableMethod(ImmutableMethod(sec, "popCaptchaV2",
                params.map { ImmutableMethodParameter(it, emptySet(), null) }, "V",
                AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                emptySet(), emptySet(), b.methodImplementation))
        }
        fun owner(method: MutableMethod) = ImmutableClassDef(sec,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, "Ljava/lang/Object;",
            listOf("Lcom/ss/android/ugc/aweme/secapi/ISecApi;"), null,
            emptySet(), emptyList(), listOf(method))
        val baseline = popup("LX/17qC;", false)
        val candidate = popup("LX/1BeZ;", true)
        val reviewed = FixtureContracts.Reviewed("com.zhiliaoapp.musically", 2024607030,
            "accepted", emptyMap(), emptyMap(),
            mapOf(acceptedKey to FixtureContracts.portableSignature(baseline)),
            mapOf(sec to FixtureContracts.portableClassSignature(owner(baseline))))
        assertTrue(FixtureContracts.captchaEntryBoundary(owner(baseline), baseline, callback("LX/17qC;"), true))
        assertEquals("experimental-captcha-entry", FixtureContracts.requirePortableMatch(
            candidate, owner(candidate), acceptedKey, reviewed, callback("LX/1BeZ;")))
        assertFalse(FixtureContracts.captchaEntryBoundary(owner(candidate), candidate, callback("LX/1BeZ;", false), true))
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(candidate, owner(candidate), acceptedKey,
                reviewed, callback("LX/1BeZ;", false))
        }
        val wrongMarker = popup("LX/1BeZ;", true, "other feature")
        assertThrows(PatchException::class.java) {
            FixtureContracts.requirePortableMatch(wrongMarker, owner(wrongMarker), acceptedKey,
                reviewed, callback("LX/1BeZ;"))
        }
    }

    @Test fun arrayPayloadChangesAndUnreviewedMutationBoundariesFail() {
        fun payload(values: List<Number>): MutableMethod {
            val b = MethodImplementationBuilder(2)
            b.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
            b.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
            b.addInstruction(BuilderArrayPayload(1, values))
            return method(b)
        }
        val baseline = FixtureContracts.signature(payload(listOf(1, 2)))
        assertThrows(PatchException::class.java) { FixtureContracts.requireMatch(payload(listOf(1, 3)), baseline) }
        val m = payload(listOf(1, 2))
        assertThrows(PatchException::class.java) { m.removeInstructions(-1, 1) }
        assertThrows(PatchException::class.java) { m.removeInstructions(1, 99) }
        assertThrows(PatchException::class.java) { m.returnEarly() }
        assertThrows(PatchException::class.java) { m.replaceInstruction(0, "nop") }
    }
}
