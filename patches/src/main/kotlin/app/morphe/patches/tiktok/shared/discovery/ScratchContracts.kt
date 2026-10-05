package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*

/** Prove that a local's incoming value is dead on every normal and exception path.
 * All handler edges are considered before a write kills the incoming value.
 * Conservative wide reads may reject a usable register, never accept a live one.
 */
internal object ScratchContracts {
    fun locals(method: Method, index: Int, excluded: Set<Int>, count: Int): List<Int> {
        val body = method.implementation ?: throw PatchException("No scratch body in $method")
        val instructions = body.instructions.toList()
        if (index !in instructions.indices || count < 1) throw PatchException("Invalid scratch site $index in $method")
        val offsets = IntArray(instructions.size)
        var offset = 0
        instructions.forEachIndexed { n, i -> offsets[n] = offset; offset += i.codeUnits }
        val byOffset = offsets.withIndex().associate { it.value to it.index }
        fun at(address: Int) = byOffset[address] ?: throw PatchException("Invalid CFG address $address in $method")
        fun handlers(n: Int) = body.tryBlocks.filter {
            offsets[n] in it.startCodeAddress until it.startCodeAddress + it.codeUnitCount
        }.flatMap { it.exceptionHandlers }.map { at(it.handlerCodeAddress) }
        fun successors(n: Int): List<Int> {
            val i = instructions[n]
            if (i.opcode.name.startsWith("return") || i.opcode == Opcode.THROW) return emptyList()
            if (i.opcode.format.isPayloadFormat) throw PatchException("Payload is reachable in scratch CFG")
            val next = listOfNotNull((n + 1).takeIf { it in instructions.indices })
            if (i.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32))
                return listOf(at(offsets[n] + (i as OffsetInstruction).codeOffset))
            if (i.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)) {
                val payload = instructions[at(offsets[n] + (i as OffsetInstruction).codeOffset)] as? SwitchPayload
                    ?: throw PatchException("Missing scratch switch payload")
                return next + payload.switchElements.map { at(offsets[n] + it.offset) }
            }
            if (i.opcode.name.startsWith("if-"))
                return next + at(offsets[n] + (i as OffsetInstruction).codeOffset)
            return next
        }
        fun reads(i: Instruction): Set<Int> {
            if (i is FiveRegisterInstruction || i is RegisterRangeInstruction) return i.argumentRegisters().toSet()
            val regs = when(i) {
                is ThreeRegisterInstruction -> listOf(i.registerB, i.registerC) +
                    if (!i.opcode.setsRegister()) listOf(i.registerA) else emptyList()
                is TwoRegisterInstruction -> listOf(i.registerB) +
                    if (!i.opcode.setsRegister() || i.opcode.name.endsWith("/2addr")) listOf(i.registerA) else emptyList()
                is OneRegisterInstruction -> if (!i.opcode.setsRegister() || i.opcode == Opcode.CHECK_CAST)
                    listOf(i.registerA) else emptyList()
                else -> emptyList()
            }
            return regs.flatMap { listOf(it, it + 1) }.filter { it < body.registerCount }.toSet()
        }
        fun dead(register: Int): Boolean {
            val pending = ArrayDeque<Int>()
            val visited = hashSetOf<Int>()
            pending.add(index)
            while (pending.isNotEmpty()) {
                val n = pending.removeFirst()
                if (!visited.add(n)) continue
                val i = instructions[n]
                if (register in reads(i)) return false
                // The injected call may throw even if the original instruction cannot.
                // Conservatively preserve incoming values used by any protected handler.
                handlers(n).forEach(pending::add)
                val written = i.opcode.setsRegister() && i is OneRegisterInstruction &&
                    (i.registerA == register || i.opcode.setsWideRegister() && i.registerA + 1 == register)
                if (!written) successors(n).forEach(pending::add)
            }
            return true
        }
        val parameterWords = method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } +
            if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
        val locals = body.registerCount - parameterWords
        val result = (0 until minOf(locals, 16)).filter { it !in excluded && dead(it) }.take(count)
        if (result.size != count) throw PatchException("No $count proven low scratch locals at $index in $method")
        return result
    }
}
