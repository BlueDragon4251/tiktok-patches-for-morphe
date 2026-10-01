package app.morphe.patches.tiktok.shared.discovery

import app.morphe.patcher.patch.BytecodePatch
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.bytecodePatch

/** Dependency executes first and finalizes last, including extension lifecycle hooks. */
private val discoverySessionPatch = bytecodePatch {
    execute { HookEvidence.begin(this) }
    finalize { HookEvidence.writeReport() }
}

internal fun tiktokBytecodePatch(
    name: String? = null,
    description: String? = null,
    default: Boolean = true,
    block: BytecodePatchBuilder.() -> Unit,
): BytecodePatch = bytecodePatch(name, description, default) {
    dependsOn(discoverySessionPatch)
    block()
}
