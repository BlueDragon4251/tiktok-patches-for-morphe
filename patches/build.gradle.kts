group = "app.morphe"

patches {
    about {
        name = "BlueIT TikTok Patches"
        description = "BlueIT Service patches for verified TikTok global versions, built for Morphe."
        source = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe"
        author = "BlueIT"
        contact = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe/issues"
        website = "https://github.com/BlueDragon4251/tiktok-patches-for-morphe"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

dependencies {
    compileOnly(libs.morphe.patcher)
    testImplementation(libs.morphe.patcher)
    testImplementation("junit:junit:4.13.2")

    // Used by JsonGenerator.
    implementation(libs.gson)

    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

// Compile the one-off fixture capture tool separately; it is not part of the patch bundle.
val contractCapture = sourceSets.create("contractCapture") {
    java.srcDir("../scripts/tiktok")
    compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets["main"].runtimeClasspath
}
tasks.named<JavaCompile>(contractCapture.compileJavaTaskName) {
    options.release.set(17)
}

tasks {
    register<JavaExec>("captureTikTokContracts") {
        description = "Capture portable and exact TikTok hook contracts from an accepted APK"
        dependsOn(contractCapture.classesTaskName)
        classpath = contractCapture.runtimeClasspath
        mainClass.set("CaptureFixtureContracts")
        jvmArgs("-Xmx6g")
    }
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
        args(project.version.toString())
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}
