package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*

/** Receiver aliases that are valid on every incoming normal and exception path. */
internal object ReceiverAliases {
    fun atEveryInstruction(method: Method): Map<Int, Set<Int>> {
        if (AccessFlags.STATIC.isSet(method.accessFlags)) throw PatchException("Static method has no receiver")
        val body = method.implementation ?: throw PatchException("No receiver CFG")
        val insns = body.instructions.toList()
        val offsets = IntArray(insns.size)
        var offset = 0
        insns.forEachIndexed { n, i -> offsets[n] = offset; offset += i.codeUnits }
        val byOffset = offsets.withIndex().associate { it.value to it.index }
        fun at(address: Int) = byOffset[address] ?: throw PatchException("Invalid receiver CFG address $address")
        val states = hashMapOf(0 to setOf(body.registerCount - method.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 } - 1))
        val pending = ArrayDeque<Int>().apply { add(0) }
        fun merge(index: Int, state: Set<Int>) {
            val old = states[index]
            val joined = old?.intersect(state) ?: state
            if (old == null || old != joined) { states[index] = joined; pending.add(index) }
        }
        while (pending.isNotEmpty()) {
            val n = pending.removeFirst()
            val incoming = states.getValue(n)
            val i = insns[n]
            for (t in body.tryBlocks) if (offsets[n] in t.startCodeAddress until t.startCodeAddress + t.codeUnitCount)
                t.exceptionHandlers.forEach { merge(at(it.handlerCodeAddress), incoming) }
            val outgoing = incoming.toMutableSet()
            if (i.opcode.setsRegister() && i is OneRegisterInstruction && i.opcode != Opcode.CHECK_CAST) {
                outgoing -= i.registerA
                if (i.opcode.setsWideRegister()) outgoing -= i.registerA + 1
                if (i.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) &&
                    (i as TwoRegisterInstruction).registerB in incoming) outgoing += i.registerA
            }
            if (i.opcode.name.startsWith("return") || i.opcode == Opcode.THROW) continue
            if (i.opcode.format.isPayloadFormat) throw PatchException("Reachable receiver CFG payload")
            if (i.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) {
                merge(at(offsets[n] + (i as OffsetInstruction).codeOffset), outgoing)
                continue
            }
            if (n + 1 < insns.size) merge(n + 1, outgoing)
            if (i.opcode.name.startsWith("if-")) merge(at(offsets[n] + (i as OffsetInstruction).codeOffset), outgoing)
            if (i.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)) {
                val payload = insns[at(offsets[n] + (i as OffsetInstruction).codeOffset)] as? SwitchPayload
                    ?: throw PatchException("Missing receiver CFG switch")
                payload.switchElements.forEach { merge(at(offsets[n] + it.offset), outgoing) }
            }
        }
        return states
    }
}
