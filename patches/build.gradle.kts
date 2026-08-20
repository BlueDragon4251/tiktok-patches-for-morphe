plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.morphe.patcher)
}

patches {
    about {
        name = "BlueIT TikTok Patches"
        description = "BlueIT Service patches for TikTok 46.4.3, built for Morphe."
        source = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe"
        author = "BlueIT"
        contact = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe/issues"
        website = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe"
        license = "GPL-3.0"
    }
}

dependencies {
    implementation(libs.morphe.patcher)
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-receivers")
    }
}

tasks.register<Copy>("buildAndroid") {
    group = "morphe"
    description = "Build the TikTok patch bundle and copy it to build/outputs."

    dependsOn(":extensions:tiktok:assembleRelease")
    dependsOn(tasks.named("jar"))

    from(tasks.named("jar"))
    into(layout.buildDirectory.dir("outputs"))
    rename { "patches.mpp" }
}
