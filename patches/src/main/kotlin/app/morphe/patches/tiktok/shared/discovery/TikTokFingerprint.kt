package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method

/** The patcher matcher is retained; consumers must use the cardinality-checked API. */
open class TikTokFingerprint(
    definingClass: String? = null,
    name: String? = null,
    accessFlags: List<AccessFlags>? = null,
    returnType: String? = null,
    parameters: List<String>? = null,
    filters: List<InstructionFilter>? = null,
    strings: List<String>? = null,
    val exactStrings: List<String> = emptyList(),
    custom: ((Method, ClassDef) -> Boolean)? = null,
    private val id: String? = null,
) : Fingerprint(definingClass = definingClass, name = name, accessFlags = accessFlags,
    returnType = returnType, parameters = parameters, filters = filters, strings = strings ?: exactStrings.takeIf { it.isNotEmpty() }, custom = custom) {
    private val semanticFilters = filters
    private val selectorAccessFlags = accessFlags
    private val semanticCustom = custom
    private var session: BytecodePatchContext? = null
    private var selected: List<Match>? = null
    val hookId: String get() = id ?: (javaClass.name + ":" + (definingClass ?: "") + ":" +
        (name ?: "") + if (javaClass == TikTokFingerprint::class.java) ":" + (strings ?: parameters ?: emptyList<String>()).joinToString("|") else "")

    context(BytecodePatchContext)
    private fun candidates(): List<Match> {
        HookEvidence.begin(this@BytecodePatchContext)
        if (session !== this@BytecodePatchContext) {
            session = this@BytecodePatchContext
            selected = null
            clearMatch()
        }
        selected?.let { return it }
        // matchAll must never see a previous first-match cache from a base-typed caller.
        clearMatch()
        val owner = definingClass?.takeIf { it.startsWith("L") && it.endsWith(";") }
            ?.let { classDefByOrNull(it) }
        val initial = (if (owner != null) super.matchAllOrNull(owner) else super.matchAllOrNull()).orEmpty()
        val oldOwner = definingClass
        val relocated = if (initial.isEmpty() && oldOwner != null && HookEvidence.normalizedType(oldOwner) != oldOwner &&
            HookEvidence.canRelocate(hookId)) {
            // The original owner/name/type spelling is obfuscated. Keep the real
            // predicate and prove the unique original class and method below.
            TikTokFingerprint(name = name?.takeUnless { HookEvidence.normalizedMember(oldOwner, it) == "*" },
                accessFlags = selectorAccessFlags,
                returnType = returnType?.takeIf { HookEvidence.normalizedType(it) == it },
                parameters = parameters?.takeIf { types -> types.all { HookEvidence.normalizedType(it) == it } },
                filters = semanticFilters, strings = strings ?: exactStrings.takeIf { it.isNotEmpty() },
                exactStrings = exactStrings, custom = semanticCustom).candidates()
        } else emptyList()
        val matches = (initial + relocated)
            .distinctBy { it.originalMethod.toString() }
            .filter { match ->
                val literals = match.originalMethod.implementation?.instructions?.mapNotNull {
                    ((it as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference as?
                        com.android.tools.smali.dexlib2.iface.reference.StringReference)?.string
                }.orEmpty()
                strings.orEmpty().all { marker -> literals.any { it.contains(marker) } } && exactStrings.all { it in literals }
            }
        clearMatch()
        val proven = if (relocated.isNotEmpty() || matches.size > 1)
            HookEvidence.portableCandidates(hookId, matches) else matches
        // Keep the raw candidates if none pass: a singleton gets a concrete
        // contract error at injection, and multiple candidates fail as ambiguous.
        selected = proven.ifEmpty { matches }
        return selected!!
    }

    context(BytecodePatchContext)
    fun uniqueMatchOrNull(): Match? {
        val matches = candidates()
        HookEvidence.resolution(this, matches, required = false)
        if (matches.size > 1) throw PatchException("Ambiguous hook $hookId: ${matches.map { it.originalMethod }}")
        return matches.singleOrNull()
    }

    context(BytecodePatchContext)
    fun uniqueMatch(): Match {
        val matches = candidates()
        HookEvidence.resolution(this, matches, required = true)
        return matches.singleOrThrow("Hook $hookId")
    }

    context(BytecodePatchContext)
    fun allMatches(range: IntRange = 1..Int.MAX_VALUE): List<Match> {
        val matches = candidates()
        HookEvidence.resolution(this, matches, required = range.first > 0, multiple = true)
        if (matches.size !in range) throw PatchException("Hook $hookId: expected $range matches, found ${matches.size}")
        matches.forEach { HookEvidence.requireReviewed(it.originalMethod) }
        return matches
    }

    context(BytecodePatchContext)
    val uniqueMethod get() = uniqueMatch().also { HookEvidence.requireReviewed(it.originalMethod) }.method
    context(BytecodePatchContext)
    val optionalMethod get() = uniqueMatchOrNull()?.also { HookEvidence.requireReviewed(it.originalMethod) }?.method
    context(BytecodePatchContext)
    val uniqueOriginalMethod get() = uniqueMatch().originalMethod.also(HookEvidence::requireReviewed)
    context(BytecodePatchContext)
    val uniqueOriginalClassDef get() = uniqueMatch().also { HookEvidence.requireReviewed(it.originalMethod) }.originalClassDef

    companion object {
        context(BytecodePatchContext)
        fun captureOriginalClasses() = HookEvidence.begin(this@BytecodePatchContext)
        context(BytecodePatchContext)
        fun writeReport() = HookEvidence.writeReport()
    }
}
