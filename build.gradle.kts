@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.4.21"
    `maven-publish`
}

group = (findProperty("group") as? String) ?: "io.github.persiancalendar"
version = (findProperty("version") as? String) ?: "4.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    js {
        nodejs()
        browser()
    }

    wasmJs {
        nodejs()
        browser()
    }

    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()

    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}

val webIconsSvg = file("src/webMain/resources/icons/icon.svg")
val webIconsDir = file("src/webMain/resources/icons")

val generateWebIcons = tasks.register("generateWebIcons") {
    description = "Generate PNG app icons from icon.svg using rsvg-convert"
    inputs.file(webIconsSvg)
    outputs.files(
        webIconsDir.resolve("icon-192.png"),
        webIconsDir.resolve("icon-512.png"),
    )
    doLast {
        listOf(192, 512).forEach { size ->
            val exit = ProcessBuilder(
                "rsvg-convert",
                "-w", size.toString(),
                "-h", size.toString(),
                webIconsSvg.absolutePath,
                "-o", webIconsDir.resolve("icon-$size.png").absolutePath,
            ).redirectErrorStream(true).start().waitFor()
            check(exit == 0) { "rsvg-convert failed for size $size with exit code $exit" }
        }
    }
}
