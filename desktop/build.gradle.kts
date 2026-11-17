plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

val javafxVersion = "17.0.21"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val javafxPlatform = when {
    osName.contains("win") -> "win"
    osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    osArch.contains("aarch64") || osArch.contains("arm64") -> "linux-aarch64"
    else -> "linux"
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        main {
            // Reuse Square's InnerTube catalogue client in the isolated JVM build.
            // The Android innertube Gradle module itself is not modified or included.
            kotlin.srcDir("../innertube/src/main/kotlin")
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-client-encoding:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.brotli:dec:0.1.2")

    // Extract public YouTube stream metadata with the upstream extractor.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")

    // JavaFX Media provides AAC/MP3 decoding and HTTP(S) playback on desktop.
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "dev.lelonio.square.desktop.MainKt"

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "Square Desktop"
            packageVersion = "0.1.0"
            description = "Desktop port of the Square music client"
        }
    }
}
