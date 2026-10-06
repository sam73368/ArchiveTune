/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

plugins {
    // Same Kotlin version as the Android app (gradle/libs.versions.toml).
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    kotlin("plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
}
