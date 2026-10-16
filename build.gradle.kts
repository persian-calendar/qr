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

        // The round-trip test decodes what this library encodes, so it needs a decoder
        // that has nothing to do with this library. ZXing is the well known one, and it is
        // a Java library, which is why that test lives in jvmTest rather than in
        // commonTest: it still runs on every platform's CI through `jvmTest`, and no
        // decoder has to be found for each of the other targets.
        jvmTest.dependencies {
            implementation("com.google.zxing:core:3.5.3")
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}

// The fuzz test takes its seed and its number of cases from the command line. A test runs in
// a JVM of its own, so the values have to be handed to it, and they have to be declared as
// inputs as well - otherwise Gradle would consider a task it had already run up to date and
// the flags would silently do nothing.
tasks.withType<Test>().configureEach {
    listOf("qr.fuzz.seed", "qr.fuzz.cases").forEach { name ->
        System.getProperty(name)?.let {
            systemProperty(name, it)
            inputs.property(name, it)
        }
    }
}
