/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.looping

import app.morphe.patcher.Fingerprint

internal object FeedPlayRequestFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("LX/0MIK;"),
    custom = { method, classDef ->
        classDef.type == "LX/0pZG;" &&
            method.name == "LJJJJLI"
    },
)

internal object FeedPrepareNextRequestFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("LX/0MIK;"),
    custom = { method, classDef ->
        classDef.type == "LX/0pZG;" &&
            method.name == "LJJJLZIJ"
    },
)

internal object FeedPlayCompletedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;" &&
            method.name == "onPlayCompleted"
    },
)
