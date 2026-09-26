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
