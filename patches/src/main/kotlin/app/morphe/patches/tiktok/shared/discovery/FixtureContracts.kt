package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.formatter.DexFormatter
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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

    /** Register and control-flow preserving signature with only obfuscated DEX names normalized. */
    fun portableSignature(method: Method): String = HookEvidence.sha256(buildString {
        append(HookEvidence.normalizedType(method.parameterTypes.joinToString("") + ")" + method.returnType))
            .append('|').append(method.accessFlags).append('|')
        val body = method.implementation ?: return@buildString
        append(body.registerCount).append('\n')
        val tokens = HookEvidence.tokens(method).drop(1).iterator()
        body.instructions.forEach { instruction ->
            if (instruction.opcode == com.android.tools.smali.dexlib2.Opcode.NOP) return@forEach
            append(tokens.next())
            if (instruction is OneRegisterInstruction) append(" a=").append(instruction.registerA)
            if (instruction is TwoRegisterInstruction) append(" b=").append(instruction.registerB)
            if (instruction is ThreeRegisterInstruction) append(" c=").append(instruction.registerC)
            if (instruction is FiveRegisterInstruction || instruction is RegisterRangeInstruction)
                append(" args=").append(instruction.argumentRegisters())
            if (instruction is WideLiteralInstruction) append(" literal=").append(instruction.wideLiteral)
            if (instruction is OffsetInstruction) append(" offset=").append(instruction.codeOffset)
            if (instruction is SwitchPayload) instruction.switchElements.forEach {
                append(" case=").append(it.key).append(':').append(it.offset)
            }
            if (instruction is ArrayPayload) {
                append(" width=").append(instruction.elementWidth)
                instruction.arrayElements.forEach { append(" element=").append(it) }
            }
            append('\n')
        }
        body.tryBlocks.forEach { block ->
            append("try=").append(block.startCodeAddress).append(':').append(block.codeUnitCount)
            block.exceptionHandlers.forEach {
                append('|').append(HookEvidence.normalizedType(it.exceptionType ?: "<all>"))
                    .append(':').append(it.handlerCodeAddress)
            }
            append('\n')
        }
    })

    fun portableClassSignature(owner: ClassDef): String = HookEvidence.sha256((
        listOf("access:" + owner.accessFlags, "super:" + HookEvidence.normalizedType(owner.superclass ?: "")) +
            owner.interfaces.map { "interface:" + HookEvidence.normalizedType(it) }.sorted() +
            owner.fields.map { field ->
                "field:" + HookEvidence.normalizedType(field.type) + ":" + field.accessFlags + ":" +
                    (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
            }.sorted() +
            owner.methods.map {
                "method:" + HookEvidence.normalizedMember(owner.type, it.name) + ":" + portableSignature(it)
            }.sorted()).joinToString("\n"))

    /** Only these reviewed entry hooks may survive unrelated changes to their declaring class. */
    fun methodScopedHooks(): Set<String> = setOf(
        "Lcom/ss/android/ugc/aweme/main/MainActivity;->onCreate(Landroid/os/Bundle;)V",
        "Lcom/ss/ttvideoengine/TTVideoEngine;->setLooping(Z)V",
    )

    /** The target body is pinned separately. This pins its class hierarchy and fields it reads. */
    fun portableScopeSignature(owner: ClassDef, method: Method): String {
        if (method.definingClass != owner.type || method.implementation == null)
            throw PatchException("No original method body in scoped owner ${owner.type}")
        val fields = method.implementation!!.instructions.mapNotNull { instruction ->
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
        }.filter { it.definingClass == owner.type }.map { reference ->
            val field = owner.fields.singleOrNull { it.name == reference.name && it.type == reference.type }
                ?: throw PatchException("Unresolved scoped field $reference in ${owner.type}")
            "field:" + HookEvidence.normalizedMember(owner.type, field.name) + ":" +
                HookEvidence.normalizedType(field.type) + ":" + field.accessFlags + ":" +
                (field.initialValue?.let(DexFormatter.INSTANCE::getEncodedValue) ?: "null")
        }.sorted()
        if (method.implementation!!.instructions.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)
                    ?.let { it.definingClass == owner.type && it.name != method.name } == true
            }) throw PatchException("Scoped hook calls another method on ${owner.type}")
        return HookEvidence.sha256((listOf(
            "access:" + owner.accessFlags,
            "super:" + HookEvidence.normalizedType(owner.superclass ?: ""),
        ) + owner.interfaces.map { "interface:" + HookEvidence.normalizedType(it) }.sorted() + fields)
            .joinToString("\n"))
    }

    data class Reviewed(val packageName: String, val versionCode: Long, val apkSha256: String,
                        val methods: Map<String, String>, val classes: Map<String, String>,
                        val portableMethods: Map<String, String> = emptyMap(),
                        val portableClasses: Map<String, String> = emptyMap(),
                        val hookMethods: Map<String, String> = emptyMap(),
                        val scopedMethods: Map<String, String> = emptyMap())

    data class Selection(val contracts: Reviewed, val experimental: Boolean)

    /** An unknown APK may only reuse native hooks byte-for-byte from the baseline.
     * The method and its entire declaring class are separately checked before each edit.
     */
    fun select(packageName: String, versionCode: Long, actualSha256: String, exact: Reviewed?,
               baseline: Reviewed, experimentalOptIn: Boolean): Selection {
        if (packageName != baseline.packageName)
            throw PatchException("Expected global TikTok ${baseline.packageName}, got $packageName")
        if (exact?.apkSha256 == actualSha256) {
            if (versionCode != exact.versionCode)
                throw PatchException("Fixture version code changed: expected ${exact.versionCode}, got $versionCode")
            return Selection(exact, experimental = false)
        }
        if (!experimentalOptIn)
            throw PatchException("Unreviewed TikTok APK SHA-256: $actualSha256. Use TIKTOK_EXPERIMENTAL_PORTABLE=1 only for explicitly experimental patching")
        return Selection(baseline, experimental = true)
    }

    fun requireMatch(method: Method, expected: String?) {
        if (expected == null) throw PatchException("Missing reviewed fixture contract for $method; run discovery and review before enabling this APK")
        val actual = signature(method)
        if (expected != actual) throw PatchException("Changed fixture contract for $method: expected $expected, got $actual. Register, reference, literal or control-flow layout changed; injection refused")
    }

    fun requireNativeMatch(method: Method, owner: ClassDef, contracts: Reviewed, experimental: Boolean) {
        if (method.definingClass != owner.type || contracts.classes[owner.type] != classSignature(owner))
            throw PatchException("Changed class contract for ${owner.type}: inheritance, fields or method set changed${if (experimental) "; unreviewed APK injection refused" else ""}")
        requireMatch(method, contracts.methods[method.toString()])
    }

    /** A relocated hook needs the accepted hook identity and the complete portable contract. */
    fun requirePortableMatch(method: Method, owner: ClassDef, acceptedMethod: String, contracts: Reviewed): Boolean {
        if (method.definingClass != owner.type)
            throw PatchException("Portable hook owner mismatch for $method")
        val oldOwner = acceptedMethod.substringBefore("->")
        val expectedClass = contracts.portableClasses[oldOwner]
            ?: throw PatchException("Missing accepted portable class contract for $oldOwner")
        val expectedMethod = contracts.portableMethods[acceptedMethod]
            ?: throw PatchException("Missing accepted portable method contract for $acceptedMethod")
        val fullClassMatches = portableClassSignature(owner) == expectedClass
        val methodMatches = portableSignature(method) == expectedMethod
        val scopedMatch = !fullClassMatches && methodMatches && acceptedMethod in methodScopedHooks() &&
            oldOwner == owner.type && contracts.scopedMethods[acceptedMethod]?.let {
                portableScopeSignature(owner, method) == it
            } == true
        val classMatches = fullClassMatches || scopedMatch
        if (!classMatches || !methodMatches) {
            val changed = buildList {
                if (!classMatches) add("class (field types, inheritance or member structure)")
                if (!methodMatches) add("method (registers, literals, references, branches, switch or exception paths)")
            }
            throw PatchException("Changed portable contract for $method: ${changed.joinToString(" and ")}; injection refused")
        }
        return scopedMatch
    }

    fun loadOrNull(version: String): Reviewed? {
        val stream = FixtureContracts::class.java.getResourceAsStream("/tiktok-contracts/$version.json") ?: return null
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        if (root.get("version").asString != version || root.get("schema").asInt != 1)
            throw PatchException("Invalid reviewed fixture contract for TikTok $version")
        fun entries(name: String) = root.getAsJsonObject(name).entrySet().associate { it.key to it.value.asString }
        return Reviewed(root.get("package").asString, root.get("versionCode").asLong,
            root.get("sha256").asString, entries("methods"), entries("classes"),
            if (root.has("portableMethods")) entries("portableMethods") else emptyMap(),
            if (root.has("portableClasses")) entries("portableClasses") else emptyMap(),
            if (root.has("hookMethods")) entries("hookMethods") else emptyMap(),
            if (root.has("scopedMethods")) entries("scopedMethods") else emptyMap())
    }

    fun load(version: String): Reviewed = loadOrNull(version)
        ?: throw PatchException("No reviewed injection contracts for TikTok $version")
}
