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
        "poc/src/poc/AndroidServerMain.java" to "7b12adaee5ce73176ed345b0fa218c265047e6107a999ce0ca78a433c64d8ab7",
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
        check(sha(bytes) == "0fe4039a1a06afae93099b6eaf140e04fe7e0b1f1145323116468f8f78a884fe")
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
    inputs.files(rootProject.file("scripts/prepare-jvm-probe.py"), rootProject.file("runtime-probe/native/jvm_runner.c"))
    outputs.dir(layout.buildDirectory.dir("generated/jvmProbe"))
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("scripts/prepare-jvm-probe.py").absolutePath)
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
        versionCode = 4
        versionName = "0.3.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = false }
    sourceSets.getByName("main").assets.srcDir(pocAssets)
    buildTypes {
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so" // Preserve pinned upstream binary identity.
        }
    }
}

tasks.named("preBuild").configure { dependsOn(packagePoc) }
tasks.matching { it.name == "preJvmProbeBuild" }.configureEach { dependsOn(prepareJvmProbe, packageProbe) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
