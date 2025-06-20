import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    `maven-publish`
}

group = "io.github.persiancalendar"
version = "3.1.2"

repositories {
    mavenCentral()
}

dependencies {
    val junit5Version = "5.13.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit5Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    testImplementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib-jdk8"))
    // Can be used to check the validity of our result but isn't used for now.
    // testImplementation("com.google.zxing:core:3.5.1")
}

tasks.test {
    useJUnitPlatform()
}

val javaVersion = JavaVersion.VERSION_17

configure<JavaPluginExtension> {
    sourceCompatibility = javaVersion
}

val sourceJar by tasks.creating(Jar::class) {
    dependsOn(tasks["classes"])
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["kotlin"])
            artifact(sourceJar)
        }
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = javaVersion.majorVersion
}
