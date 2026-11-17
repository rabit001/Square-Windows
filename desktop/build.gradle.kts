plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material)
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
