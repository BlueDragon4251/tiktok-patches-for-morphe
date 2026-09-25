package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.formatter.DexFormatter
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
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
            if (i is ArrayPayload) {
                append(" width=").append(i.elementWidth)
                i.arrayElements.forEach { append(" element=").append(it) }
            }
            append('\n')
        }
        body.tryBlocks.forEach { block ->
            append("try=").append(block.startCodeAddress).append(':').append(block.codeUnitCount)
            block.exceptionHandlers.forEach { append('|').append(it.exceptionType).append(':').append(it.handlerCodeAddress) }
            append('\n')
        }
    }.let(HookEvidence::sha256)

    /** Pin class relationships and fields as well as the method body before editing native bytecode. */
    fun classSignature(owner: ClassDef): String = HookEvidence.sha256(buildString {
        append(owner.type).append('|').append(owner.accessFlags).append('|').append(owner.superclass).append('\n')
        owner.interfaces.sorted().forEach { append("interface:").append(it).append('\n') }
        owner.fields.map { field ->
            "field:$field:${field.accessFlags}:" +
                (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
        }.sorted().forEach { append(it).append('\n') }
        owner.methods.map { "method:$it:${it.accessFlags}" }.sorted().forEach { append(it).append('\n') }
    })

    data class Reviewed(val packageName: String, val versionCode: Long, val apkSha256: String,
                        val methods: Map<String, String>, val classes: Map<String, String>)

    fun requireMatch(method: Method, expected: String?) {
        if (expected == null) throw PatchException("Missing reviewed fixture contract for $method; run discovery and review before enabling this APK")
        val actual = signature(method)
        if (expected != actual) throw PatchException("Changed fixture contract for $method: expected $expected, got $actual. Register, reference, literal or control-flow layout changed; injection refused")
    }

    fun load(version: String): Reviewed {
        val stream = FixtureContracts::class.java.getResourceAsStream("/tiktok-contracts/$version.json")
            ?: throw PatchException("No reviewed injection contracts for TikTok $version")
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        if (root.get("version").asString != version || root.get("schema").asInt != 1)
            throw PatchException("Invalid reviewed fixture contract for TikTok $version")
        fun entries(name: String) = root.getAsJsonObject(name).entrySet().associate { it.key to it.value.asString }
        return Reviewed(root.get("package").asString, root.get("versionCode").asLong,
            root.get("sha256").asString, entries("methods"), entries("classes"))
    }
}
