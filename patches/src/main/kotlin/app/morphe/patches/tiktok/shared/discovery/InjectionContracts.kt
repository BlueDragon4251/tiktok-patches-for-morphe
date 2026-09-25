package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction as rawAddInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions as rawAddInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels as rawAddWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction as rawReplaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions as rawRemoveInstructions
import app.morphe.util.returnEarly as rawReturnEarly
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal fun <T> Iterable<T>.singleOrThrow(contract: String): T {
    val matches = toList()
    if (matches.size != 1) throw PatchException("$contract: expected one candidate, found ${matches.size}: ${matches.take(12)}")
    return matches.single()
}

internal fun Method.uniqueInstructionIndex(contract: String, predicate: (Instruction) -> Boolean): Int =
    implementation?.instructions?.withIndex()?.filter { predicate(it.value) }
        ?.singleOrThrow("$contract in $this")?.index ?: throw PatchException("$contract: method has no body: $this")

internal fun Method.parameterRegister(index: Int, expectedType: String? = null): Int {
    val body = implementation ?: throw PatchException("Parameter contract: no body: $this")
    if (index !in parameterTypes.indices || (expectedType != null && parameterTypes[index] != expectedType)) {
        throw PatchException("Parameter contract: expected parameter $index of type $expectedType in $this")
    }
    val words = parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
    return body.registerCount - words + parameterTypes.take(index).sumOf { if (it == "J" || it == "D") 2 else 1 }
}

internal fun Method.requireLocals(count: Int) {
    val words = parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } +
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    val locals = (implementation?.registerCount ?: 0) - words
    if (locals < count) throw PatchException("Scratch contract: need $count local words, have $locals in $this")
}

/** Actual argument words, including the receiver and both words of wide arguments. */
internal fun Instruction.argumentRegisters(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    else -> throw PatchException("Invocation contract: ${opcode.name} is not a supported invoke")
}

internal fun Method.resultRegister(callIndex: Int, expectedReturn: String): Int {
    val instructions = implementation?.instructions?.toList() ?: throw PatchException("Result contract: no body: $this")
    val call = instructions.getOrNull(callIndex)
    val ref = (call as? ReferenceInstruction)?.reference as? MethodReference
    val expectedOpcode = when {
        expectedReturn == "V" -> throw PatchException("Result contract: void call has no result: $this")
        expectedReturn == "J" || expectedReturn == "D" -> Opcode.MOVE_RESULT_WIDE
        expectedReturn.startsWith("L") || expectedReturn.startsWith("[") -> Opcode.MOVE_RESULT_OBJECT
        else -> Opcode.MOVE_RESULT
    }
    val result = instructions.getOrNull(callIndex + 1)
    if (ref?.returnType != expectedReturn || call?.opcode?.name?.startsWith("invoke-") != true ||
        result?.opcode != expectedOpcode || result !is OneRegisterInstruction) {
        throw PatchException("Result contract: expected invoke returning $expectedReturn followed by $expectedOpcode at $callIndex in $this")
    }
    return result.registerA
}

internal val returnOpcodes = setOf(Opcode.RETURN_VOID, Opcode.RETURN, Opcode.RETURN_OBJECT, Opcode.RETURN_WIDE)

/** No incoming branch, switch case or handler can enter in the middle of an injection. */
internal fun MutableMethod.insertAtTarget(index: Int, code: String, labels: Array<out ExternalLabel>? = null) {
    val body = implementation ?: throw PatchException("Injection contract: no body: $this")
    val original = body.instructions.getOrNull(index)
        ?: throw PatchException("Injection contract: invalid target $index in $this")
    if (original.opcode in setOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_EXCEPTION)) {
        throw PatchException("Injection contract: cannot split ${original.opcode} from its producer/handler in $this")
    }
    // Preserve the original MethodLocation and all its labels (including try boundaries).
    // Smali replacement detaches the old instruction; reinsert that same instruction after
    // the hook so branch payload references carried by it remain intact as well.
    rawReplaceInstruction(index, "nop")
    rawAddInstruction(index + 1, original)
    // Total size is unsuitable: dexlib can adjust switch payload alignment NOPs.
    if (labels == null) rawAddInstructions(original.location.index, code)
    else rawAddWithLabels(original.location.index, code, *labels)
}

/** Shared adapters keep existing injection code readable while fixing return-label bypass. */
internal object ContractInstructions {
    private fun MutableMethod.inject(index: Int, code: String, labels: Array<out ExternalLabel>? = null) {
        val body = implementation ?: throw PatchException("Injection contract: no body: $this")
        if (index !in 0..body.instructions.size) throw PatchException("Injection contract: invalid index $index in $this")
        HookEvidence.touch(this)
        try {
            val target = body.instructions.getOrNull(index)
            if (target != null) insertAtTarget(index, code, labels)
            else if (labels != null) rawAddWithLabels(index, code, *labels)
            else rawAddInstructions(index, code)
            HookEvidence.injection(this, index, code)
        } catch (error: PatchException) {
            throw error
        } catch (error: Exception) {
            throw PatchException("Injection contract failed at $index in $this: ${error.message}")
        }
    }

    fun MutableMethod.addInstructions(index: Int, code: String) = inject(index, code)
    fun MutableMethod.addInstructions(index: Int, code: List<com.android.tools.smali.dexlib2.builder.BuilderInstruction>) {
        HookEvidence.touch(this)
        rawAddInstructions(index, code)
    }
    fun MutableMethod.addInstruction(index: Int, code: String) = inject(index, code)
    fun MutableMethod.addInstructionsWithLabels(index: Int, code: String, vararg labels: ExternalLabel) = inject(index, code, labels)

    fun MutableMethod.replaceInstruction(index: Int, code: String) {
        val target = implementation?.instructions?.getOrNull(index)
            ?: throw PatchException("Replacement contract: invalid index $index in $this")
        HookEvidence.touch(this)
        try {
            rawReplaceInstruction(index, code)
            HookEvidence.injection(this, index, "replace ${target.opcode}: $code")
        } catch (error: PatchException) { throw error
        } catch (error: Exception) { throw PatchException("Replacement contract failed in $this: ${error.message}") }
    }

    fun MutableMethod.removeInstructions(index: Int, count: Int) {
        val body = implementation ?: throw PatchException("Removal contract: no body: $this")
        if (count < 1 || index < 0 || index + count > body.instructions.size)
            throw PatchException("Removal contract: invalid range $index + $count in $this")
        val removed = body.instructions.subList(index, index + count)
        if (removed.drop(1).any { it.location.labels.isNotEmpty() })
            throw PatchException("Removal contract: a branch, handler, switch or try boundary enters removed instructions in $this")
        if (body.instructions.getOrNull(index + count)?.opcode in setOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_RESULT_OBJECT))
            throw PatchException("Removal contract: would orphan move-result in $this")
        HookEvidence.touch(this)
        rawRemoveInstructions(index, count)
        HookEvidence.injection(this, index, "remove $count instructions")
    }

    fun MutableMethod.returnEarly() {
        if (returnType != "V") throw PatchException("Early return contract: expected void in $this")
        HookEvidence.touch(this)
        rawReturnEarly()
        HookEvidence.injection(this, 0, "return-void")
    }

    fun MutableMethod.returnEarly(value: Int) {
        if (returnType != "I") throw PatchException("Early return contract: expected int in $this")
        HookEvidence.touch(this)
        rawReturnEarly(value)
        HookEvidence.injection(this, 0, "return $value")
    }
}
