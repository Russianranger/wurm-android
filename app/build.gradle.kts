import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// JVM bytecode is a runtime asset, not an Android/D8 dependency. No Wurm JARs
// are needed to build the APK. Update all pins together after rebuilding source.
val pocAssets = layout.buildDirectory.dir("generated/pocAssets")
val packagePoc by tasks.registering {
    val artifact = rootProject.file("poc/artifacts/wurm-arm64-poc.jar.base64")
    val sourcePins = mapOf(
        "poc/src/poc/AndroidServerMain.java" to "1ea22c714666a6bd502e44f8fc8091c33d06c57450da6c5ad3e47b4f675ba44e",
        "poc/src/SteamJni/SteamServerApi.java" to "37b5e6e41ade709e1f22a78818fb2f751d120b8aef6bb4195d3482ac3cd55292"
    )
    inputs.files(artifact, *sourcePins.keys.map { rootProject.file(it) }.toTypedArray())
    outputs.dir(pocAssets)
    doLast {
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        sourcePins.forEach { (path, expected) ->
            check(sha(rootProject.file(path).readBytes()) == expected) {
                "POC source changed: rebuild the JAR with user-supplied Wurm dependencies and review/update its pins."
            }
        }
        val bytes = Base64.getDecoder().decode(artifact.readText().filterNot { it.isWhitespace() })
        check(sha(bytes) == "82a39c9797a394b036785ad366e5c1a6ed0de935ab1f3b82e1fcc80f5181dfa4")
        val entries = mutableSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) check(entries.add(entry.name))
            }
        }
        check(entries == setOf("META-INF/MANIFEST.MF", "SteamJni/SteamServerApi.class", "poc/AndroidServerMain.class"))
        val output = pocAssets.get().file("wurm-arm64-poc.jar").asFile
        output.parentFile.mkdirs()
        output.writeBytes(bytes)
    }
}

// An additional install-alongside APK keeps the verified import preview intact.
val probeAssets = layout.buildDirectory.dir("generated/probeClassesAsset")
val compileProbe by tasks.registering(JavaCompile::class) {
    source(rootProject.fileTree("runtime-probe/src") { include("**/*.java") })
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("generated/probeClasses"))
    options.release.set(17)
}
val packageProbe by tasks.registering(Jar::class) {
    dependsOn(compileProbe)
    from(compileProbe.flatMap { it.destinationDirectory })
    destinationDirectory.set(probeAssets)
    archiveFileName.set("runtime-probe.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val prepareJvmProbe by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("scripts/prepare-jvm-probe.py"), rootProject.fileTree("runtime-probe/native"))
    outputs.dir(layout.buildDirectory.dir("generated/jvmProbe"))
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("scripts/prepare-jvm-probe.py").absolutePath)
}

// Compile handwritten client adapters against API-only signatures. The signature
// stubs are never packaged; actual console/profile/ticket classes come from user imports.
val clientCompatAssets = layout.buildDirectory.dir("generated/clientCompatAssets")
val compileClientCompat by tasks.registering(JavaCompile::class) {
    source(rootProject.fileTree("client-compat") { include("src/**/*.java", "stubs/**/*.java") })
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("generated/clientCompatClasses"))
    options.release.set(17)
}
val packageClientCompat by tasks.registering(Jar::class) {
    dependsOn(compileClientCompat)
    from(compileClientCompat.flatMap { it.destinationDirectory }) {
        include("SteamJni/Steam_api.class", "com/wurmonline/client/launcherfx/WurmMain*.class",
            "com/wurmonline/client/launcherfx/WurmSettingsFX.class", "com/wurmonline/client/launcherfx/WurmStage.class",
            "com/wurmonline/client/ErrorReporterPanel.class", "wurm/android/compat/*.class")
    }
    destinationDirectory.set(clientCompatAssets)
    archiveFileName.set("client-compat.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareManagedRuntime by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("scripts/prepare-managed-runtime.py"), rootProject.file("scripts/prepare-jvm-probe.py"),
        rootProject.file("runtime-build/runtime.json"), rootProject.fileTree("runtime-probe/native"))
    outputs.dir(layout.buildDirectory.dir("generated/managedRuntime"))
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("scripts/prepare-managed-runtime.py").absolutePath)
}

val prepareClientGraphics by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("scripts/prepare-client-graphics.py"), rootProject.file("scripts/build-lwjgl-api.py"),
        rootProject.file("scripts/ExportAuditPlatform.java"), rootProject.file("scripts/build-window-api.py"),
        rootProject.file("scripts/patch-gl4es.py"),
        rootProject.file("runtime-probe/src/client/DesktopInput.java"), rootProject.fileTree("graphics-compat"))
    outputs.dir(layout.buildDirectory.dir("generated/clientGraphics"))
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("scripts/prepare-client-graphics.py").absolutePath)
}

android {
    namespace = "io.github.russianranger.wurmlauncher"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "io.github.russianranger.wurmlauncher"
        minSdk = 33
        // This first, sideload-only milestone targets the Android 13 POC.
        targetSdk = 33
        versionCode = 34
        versionName = "0.10.20"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = false }
    sourceSets.getByName("main").assets.srcDir(pocAssets)
    buildTypes {
        create("managedPreview") {
            initWith(getByName("debug"))
            // Separate package preserves the earlier preview's data/debug signature.
            applicationIdSuffix = ".smoothsettings"
            versionNameSuffix = "-managed-preview"
            matchingFallbacks += listOf("debug")
        }
        create("jvmProbe") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".jvmprobe"
            versionNameSuffix = "-jvm-probe"
            matchingFallbacks += listOf("debug")
        }
    }
    sourceSets.getByName("jvmProbe") {
        assets.srcDir(probeAssets)
        assets.srcDir(layout.buildDirectory.dir("generated/jvmProbe/assets"))
        jniLibs.srcDir(layout.buildDirectory.dir("generated/jvmProbe/jniLibs"))
    }
    sourceSets.getByName("managedPreview") {
        // Reuse the tested installer/environment and diagnostic helpers. The
        // diagnostic Activity is not registered in this variant's manifest.
        java.srcDir("src/jvmProbe/java")
        assets.srcDir(probeAssets)
        assets.srcDir(clientCompatAssets)
        assets.srcDir(layout.buildDirectory.dir("generated/managedRuntime/assets"))
        jniLibs.srcDir(layout.buildDirectory.dir("generated/managedRuntime/jniLibs"))
        assets.srcDir(layout.buildDirectory.dir("generated/clientGraphics/assets"))
        jniLibs.srcDir(layout.buildDirectory.dir("generated/clientGraphics/jniLibs"))
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so" // Preserve pinned upstream binary identity.
        }
    }
}

tasks.named("preBuild").configure { dependsOn(packagePoc) }
tasks.matching { it.name == "preJvmProbeBuild" }.configureEach { dependsOn(prepareJvmProbe, packageProbe) }
tasks.matching { it.name == "preManagedPreviewBuild" }.configureEach { dependsOn(prepareManagedRuntime, packageProbe, packageClientCompat, prepareClientGraphics) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
