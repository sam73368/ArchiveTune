/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val ktorVersion = "3.5.1"
val coilVersion = "3.5.0"

// The YouTube Music response models and page parsers are compiled straight from the :core
// submodule (sam73368/core), so Android and iOS share one copy of them. Only the portable
// part is taken: models/, pages/ (minus the NewPipe stream extractors) and SearchFilter.
val coreSourceRoot = rootDir.resolve("../core/src/main/kotlin")
val syncCoreSources = tasks.register<Sync>("syncCoreSources") {
    from(coreSourceRoot) {
        include("moe/rukamori/archivetune/innertube/models/**")
        include("moe/rukamori/archivetune/innertube/pages/**")
        include("moe/rukamori/archivetune/innertube/SearchFilter.kt")
        exclude("**/NewPipe*.kt")
    }
    into(layout.buildDirectory.dir("generated/coreSources/kotlin"))
}

kotlin {
    jvmToolchain(21)

    // JVM target: only used to compile and test the shared code quickly on Linux CI.
    jvm()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(syncCoreSources)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
                implementation("io.coil-kt.coil3:coil-network-ktor3:$coilVersion")
            }
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events("passed", "failed", "skipped")
    }
}
