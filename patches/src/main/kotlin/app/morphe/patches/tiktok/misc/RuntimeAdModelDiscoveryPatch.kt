package app.morphe.patches.tiktok.misc

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities

@Suppress("unused")
val runtimeAdModelDiscoveryPatch = bytecodePatch(
    name = "BlueIT ad model discovery",
    description = "Temporary exact TikTok 46.4.3 Aweme advertising model evidence collector.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4643())

    execute {
        val target = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
        val hints = listOf("ad", "raw", "commercial", "sponsor", "promot", "commerce", "brand", "cta")

        classDefForEach { classDef ->
            if (classDef.type != target) return@classDefForEach

            println("[BlueITAdDiscovery] CLASS ${classDef.type} superclass=${classDef.superclass}")
            classDef.fields.forEach { field ->
                val text = "${field.name}:${field.type}".lowercase()
                if (hints.any(text::contains)) {
                    println("[BlueITAdDiscovery] FIELD ${field.name}:${field.type} access=${field.accessFlags} annotations=${field.annotations}")
                }
            }
            classDef.methods.forEach { method ->
                val signature = "${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
                val lower = signature.lowercase()
                if (hints.any(lower::contains)) {
                    println("[BlueITAdDiscovery] METHOD $signature access=${method.accessFlags}")
                }
            }
        }
    }
}
