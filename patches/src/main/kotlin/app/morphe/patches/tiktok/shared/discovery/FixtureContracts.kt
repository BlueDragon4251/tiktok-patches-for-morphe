package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.google.gson.JsonParser

/** Reviewed patch-time contracts for native assumptions not yet generalized. */
internal object FixtureContracts {
    fun signature(method: Method): String = buildString {
        append(method).append('|').append(method.accessFlags).append('|')
        val body = method.implementation ?: return@buildString
        append(body.registerCount).append('\n')
        body.instructions.forEach { i ->
            append(i.opcode.name)
            if (i is OneRegisterInstruction) append(" a=").append(i.registerA)
            if (i is TwoRegisterInstruction) append(" b=").append(i.registerB)
            if (i is ThreeRegisterInstruction) append(" c=").append(i.registerC)
            if (i is FiveRegisterInstruction || i is RegisterRangeInstruction) append(" args=").append(i.argumentRegisters())
            if (i is WideLiteralInstruction) append(" literal=").append(i.wideLiteral)
            if (i is ReferenceInstruction) append(" ref=").append(i.reference)
            if (i is OffsetInstruction) append(" offset=").append(i.codeOffset)
            if (i is SwitchPayload) i.switchElements.forEach { append(" case=").append(it.key).append(':').append(it.offset) }
            append('\n')
        }
        body.tryBlocks.forEach { block ->
            append("try=").append(block.startCodeAddress).append(':').append(block.codeUnitCount)
            block.exceptionHandlers.forEach { append('|').append(it.exceptionType).append(':').append(it.handlerCodeAddress) }
            append('\n')
        }
    }.let(HookEvidence::sha256)

    fun requireMatch(method: Method, expected: String?) {
        if (expected == null) throw PatchException("Missing reviewed fixture contract for $method; run discovery and review before enabling this APK")
        val actual = signature(method)
        if (expected != actual) throw PatchException("Changed fixture contract for $method: expected $expected, got $actual. Register, reference, literal or control-flow layout changed; injection refused")
    }

    fun load(version: String): Map<String, String> {
        val stream = FixtureContracts::class.java.getResourceAsStream("/tiktok-contracts/$version.json")
            ?: throw PatchException("No reviewed injection contracts for TikTok $version")
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        return root.getAsJsonObject("methods").entrySet().associate { it.key to it.value.asString }
    }
}
