/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.Fingerprint

internal object QuickCommentReactionGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("I"),
    custom = { _, classDef ->
        classDef.type == "LX/0BIZ;"
    },
)

internal object LongPressQuickShareGateFingerprint : Fingerprint(
    returnType = "I",
    parameters = emptyList(),
    custom = { _, classDef ->
        classDef.type == "LX/0BJV;"
    },
)
