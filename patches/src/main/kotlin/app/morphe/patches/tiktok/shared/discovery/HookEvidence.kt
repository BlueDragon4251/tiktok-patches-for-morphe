package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.google.gson.GsonBuilder
import java.io.File
import java.security.MessageDigest
import java.security.DigestInputStream

/** One evidence session per patcher context, never per process or per weak fingerprint instance. */
internal object HookEvidence {
    private var context: BytecodePatchContext? = null
    private val originals = linkedMapOf<String, ClassDef>()
    private val rows = linkedMapOf<String, MutableMap<String, Any?>>()
    private val sites = mutableListOf<Map<String, Any?>>()
    private val validated = hashSetOf<String>()
    private val validationMode = hashMapOf<String, String>()
    private var reviewed: FixtureContracts.Reviewed? = null
    private var apkSha256: String? = null
    private var experimental = false

    fun begin(current: BytecodePatchContext) {
        if (context === current) return
        context = current
        originals.clear()
        shapes.clear()
        rows.clear()
        sites.clear()
        validated.clear()
        validationMode.clear()
        reviewed = null
        apkSha256 = null
        experimental = false
        current.classDefForEach { owner ->
            if (!owner.type.startsWith("Lapp/morphe/")) {
                originals[owner.type] = if (owner is DexBackedClassDef) owner else ImmutableClassDef.of(owner)
            }
        }
    }

    private val obfuscated = Regex("L(?:X|Y)/[^;]+;|Lkotlin/jvm/internal/(?:A[^;]+);?")
    fun normalizedType(value: String) = obfuscated.replace(value, "L?;")
    fun normalizedMember(owner: String, name: String): String = when {
        owner.startsWith("Landroid/") || owner.startsWith("Ljava/") || owner.startsWith("Ljavax/") -> name
        !name.matches(Regex("L[A-Z0-9]+|[a-zA-Z]{1,2}|invoke\\$[0-9]+")) -> name
        else -> "*"
    }

    fun tokens(method: Method): List<String> = tokens(method, ignoreAppMethodNames = false)

    /** Used only by explicitly reviewed hooks when an obfuscated callee was renamed. */
    fun tokensIgnoringAppMethodNames(method: Method): List<String> = tokens(method, ignoreAppMethodNames = true)

    private fun tokens(method: Method, ignoreAppMethodNames: Boolean): List<String> = buildList {
        add(normalizedType(method.parameterTypes.joinToString("") + ")" + method.returnType))
        method.implementation?.instructions?.forEach { instruction ->
            if (instruction.opcode == com.android.tools.smali.dexlib2.Opcode.NOP) return@forEach
            val detail = when (val ref = (instruction as? ReferenceInstruction)?.reference) {
                is StringReference -> "s:" + ref.string
                is MethodReference -> "m:" + normalizedType(ref.definingClass) + "->" +
                    (if (ignoreAppMethodNames && !ref.definingClass.startsWith("Ljava/") &&
                        !ref.definingClass.startsWith("Ljavax/") && !ref.definingClass.startsWith("Landroid/") &&
                        ref.name !in setOf("<init>", "<clinit>")) "*" else normalizedMember(ref.definingClass, ref.name)) +
                    "(" + normalizedType(ref.parameterTypes.joinToString("")) + ")" + normalizedType(ref.returnType)
                is FieldReference -> "f:" + normalizedType(ref.definingClass) + "->" + normalizedMember(ref.definingClass, ref.name) + ":" + normalizedType(ref.type)
                is TypeReference -> "t:" + normalizedType(ref.type)
                else -> ""
            }
            add(instruction.opcode.name.lowercase().replace('_', '-').replace('/', '-') + " " + detail)
        }
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Hash the patcher's actual input APK once per context; metadata alone cannot certify APK bytes. */
    private fun inputApkSha256(current: BytecodePatchContext): String = apkSha256 ?: try {
        val configField = BytecodePatchContext::class.java.getDeclaredField("config")
        configField.isAccessible = true
        val config = configField.get(current) ?: throw PatchException("Input APK config is absent")
        val apk = config.javaClass.getMethod("getApkFile\$morphe_patcher").invoke(config) as? File
            ?: throw PatchException("Input APK file is absent")
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(apk.inputStream(), digest).use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (stream.read(buffer) != -1) Unit
        }
        digest.digest().joinToString("") { "%02x".format(it) }.also { apkSha256 = it }
    } catch (error: Exception) {
        throw PatchException("Could not verify original APK SHA-256 at patch time: ${error.message}")
    }

    private fun original(method: Method): Method? = originals[method.definingClass]?.methods
        ?.filter { it.name == method.name && it.parameterTypes.map(CharSequence::toString) ==
            method.parameterTypes.map(CharSequence::toString) && it.returnType == method.returnType }
        ?.singleOrNull()

    private fun fieldShape(owner: ClassDef): String = sha256((listOf("super:" + normalizedType(owner.superclass ?: "")) +
        owner.interfaces.map { "interface:" + normalizedType(it) }.sorted() +
        owner.fields.map { "field:" + normalizedType(it.type) + ":" + it.accessFlags }.sorted()
        ).joinToString("\n"))

    private val shapes = hashMapOf<String, String>()
    private fun ownerShape(owner: ClassDef): String = shapes.getOrPut(owner.type) {
        val parts = mutableListOf("super:" + normalizedType(owner.superclass ?: ""))
        parts += owner.interfaces.map { "interface:" + normalizedType(it) }.sorted()
        parts += owner.fields.map { "field:" + normalizedType(it.type) + ":" + it.accessFlags }.sorted()
        parts += owner.methods.map { "method:" + normalizedMember(owner.type, it.name) + ":" + it.accessFlags + ":" + sha256(tokens(it).joinToString("\n")) }.sorted()
        sha256(parts.joinToString("\n"))
    }

    private fun evidence(id: String, method: Method): MutableMap<String, Any?> {
        val source = original(method)
        val signature = source?.takeIf { it.implementation != null }?.let(::tokens)
        return linkedMapOf(
            "hook" to id, "status" to "resolved", "required" to true,
            "owner" to method.definingClass, "name" to method.name,
            "parameters" to method.parameterTypes.map(CharSequence::toString), "returns" to method.returnType,
            "accessFlags" to method.accessFlags, "registers" to source?.implementation?.registerCount,
            "origin" to if (method.definingClass.startsWith("Lapp/morphe/")) "extension" else "apk",
            "semanticContext" to mapOf(
                "stableOwner" to method.definingClass.takeUnless { obfuscated.containsMatchIn(it) },
                "stableName" to normalizedMember(method.definingClass, method.name).takeUnless { it == "*" },
                "ownerShapeSha256" to originals[method.definingClass]?.let(::ownerShape),
                "returnTypeShapeSha256" to originals[method.returnType]?.let(::fieldShape)),
            "fixtureContractSha256" to source?.let(FixtureContracts::signature),
            "fixtureContractValidated" to (!experimental && method.toString() in validated),
            "portableContractValidated" to (experimental && method.toString() in validated),
            "contractMode" to if (source == null) "extension" else validationMode[method.toString()]
                ?: if (experimental) "experimental-unvalidated" else "fixture-locked",
            "tokens" to signature, "structuralSha256" to signature?.let { sha256(it.joinToString("\n")) },
            "literals" to source?.implementation?.instructions?.mapNotNull { (it as? WideLiteralInstruction)?.wideLiteral },
            "exceptionHandlers" to source?.implementation?.tryBlocks?.flatMap { it.exceptionHandlers }
                ?.map { mapOf("type" to it.exceptionType, "address" to it.handlerCodeAddress) },
        )
    }

    fun resolution(fingerprint: TikTokFingerprint, matches: List<Match>, required: Boolean, multiple: Boolean = false) {
        val id = fingerprint.hookId
        val previousRequired = rows[id]?.get("required") == true
        if (matches.isEmpty() || (!multiple && matches.size > 1)) {
            rows[id] = linkedMapOf("hook" to id, "status" to if (matches.isEmpty()) "missing" else "ambiguous",
                "required" to (required || previousRequired), "candidateCount" to matches.size,
                "candidates" to matches.map { it.originalMethod.toString() })
            return
        }
        matches.forEach { match ->
            val key = if (multiple) "$id:${match.originalMethod}" else id
            val row = evidence(key, match.originalMethod)
            row["required"] = required || previousRequired
            row["selection"] = if (multiple) "all-sites" else "unique"
            row["candidateCount"] = if (multiple) matches.size else 1
            row["selector"] = mapOf("owner" to fingerprint.definingClass, "name" to fingerprint.name,
                "parameters" to fingerprint.parameters, "returns" to fingerprint.returnType,
                "strings" to fingerprint.strings, "exactStrings" to fingerprint.exactStrings, "accessFlags" to fingerprint.accessFlags)
            rows[key] = row
        }
    }

    fun touch(method: Method, contract: String = "native-injection") {
        if (context == null) throw PatchException("Hook evidence session was not initialized before $method")
        requireReviewed(method)
        val key = "$contract:$method"
        if (rows.values.none { it["owner"] == method.definingClass && it["name"] == method.name &&
                it["parameters"] == method.parameterTypes.map(CharSequence::toString) && it["returns"] == method.returnType }) {
            rows[key] = evidence(key, method).also { it["selection"] = "explicit-site" }
        }
    }

    private fun selectContracts(current: BytecodePatchContext): FixtureContracts.Reviewed = reviewed ?: run {
        val metadata = current.packageMetadata
        val actualApkSha256 = inputApkSha256(current)
        System.getenv("TIKTOK_FIXTURE_SHA256")?.let { actual ->
            if (actual != actualApkSha256)
                throw PatchException("Fixture SHA-256 mismatch: expected $actual, got $actualApkSha256")
        }
        val exact = FixtureContracts.loadOrNull(metadata.versionName)
        val baseline = exact?.takeIf { it.apkSha256 == actualApkSha256 }
            ?: FixtureContracts.load("46.7.3")
        val selection = FixtureContracts.select(metadata.packageName, metadata.versionCode.toLong(),
            actualApkSha256, exact, baseline, System.getenv("TIKTOK_EXPERIMENTAL_PORTABLE") == "1")
        experimental = selection.experimental
        reviewed = selection.contracts
        selection.contracts
    }

    fun canRelocate(hookId: String): Boolean {
        val current = context ?: return false
        val contracts = selectContracts(current)
        return experimental && hookId in contracts.hookMethods
    }

    /** Only a unique baseline bytecode/class contract may disambiguate a new APK. */
    fun portableCandidates(hookId: String, matches: List<Match>): List<Match> {
        val current = context ?: return matches
        val contracts = selectContracts(current)
        if (!experimental) return matches
        val accepted = contracts.hookMethods[hookId] ?: return matches
        val expectedClass = contracts.portableClasses[accepted.substringBefore("->")] ?: return matches
        val expectedMethod = contracts.portableMethods[accepted] ?: return matches
        return matches.filter { match ->
            val method = original(match.originalMethod) ?: return@filter false
            val owner = originals[method.definingClass] ?: return@filter false
            FixtureContracts.portableSignature(method) == expectedMethod &&
                FixtureContracts.portableClassSignature(owner) == expectedClass
        }
    }

    fun requireReviewed(method: Method) {
        if (method.definingClass.startsWith("Lapp/morphe/")) return
        val current = context ?: throw PatchException("Hook evidence session missing before $method")
        val key = method.toString()
        if (key in validated) return
        val source = original(method) ?: throw PatchException("No original APK method for $method")
        val contracts = selectContracts(current)
        val owner = originals[method.definingClass] ?: throw PatchException("No original APK class for $key")
        var mode = if (experimental) "experimental-identical-class" else "fixture-locked"
        try {
            FixtureContracts.requireNativeMatch(source, owner, contracts, experimental)
        } catch (exactFailure: PatchException) {
            if (!experimental) throw exactFailure
            val hooks = rows.values.filter { row ->
                row["owner"] == method.definingClass && row["name"] == method.name &&
                    row["parameters"] == method.parameterTypes.map(CharSequence::toString) &&
                    row["returns"] == method.returnType && row["selection"] == "unique" &&
                    row["candidateCount"] == 1 && row["required"] == true
            }.mapNotNull { contracts.hookMethods[it["hook"]] }.distinct()
            if (hooks.size != 1)
                throw PatchException("No unique accepted portable hook for $method; ${exactFailure.message}")
            mode = FixtureContracts.requirePortableMatch(source, owner, hooks.single(), contracts)
        }
        validated += key
        validationMode[key] = mode
        rows.values.filter { it["owner"] == method.definingClass && it["name"] == method.name &&
            it["parameters"] == method.parameterTypes.map(CharSequence::toString) && it["returns"] == method.returnType }
            .forEach {
                it["fixtureContractValidated"] = !experimental
                it["portableContractValidated"] = experimental
                it["contractMode"] = mode
            }
    }

    fun injection(method: Method, index: Int, code: String) {
        sites += mapOf("method" to method.toString(), "indexAtInjection" to index,
            "registerCount" to method.implementation?.registerCount, "code" to code.trim())
    }

    fun writeReport() {
        val current = context ?: throw PatchException("Hook report has no APK session")
        selectContracts(current)
        val metadata = current.packageMetadata
        val report = mapOf("schema" to 2, "normalization" to 2, "experimental" to experimental,
            "package" to metadata.packageName, "version" to metadata.versionName,
            "versionCode" to metadata.versionCode, "fixtureSha256" to (apkSha256 ?: inputApkSha256(current)), "featureHead" to System.getenv("TIKTOK_FEATURE_HEAD"),
            "fingerprints" to rows.toSortedMap().values, "injections" to sites)
        File("tiktok-hook-report.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(report))
        val failed = rows.values.filter { it["required"] == true && it["status"] != "resolved" }
        if (failed.isNotEmpty()) throw PatchException("Unresolved mandatory hook contracts: ${failed.map { it["hook"] }}")
        println("[BlueIT Hook Discovery] ${rows.size} hook records, ${sites.size} injections")
    }
}
