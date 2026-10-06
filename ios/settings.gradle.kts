/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Standalone Gradle build for the iOS port. Kept separate from the Android build on purpose:
// the Android app configures dozens of Android-only modules, and keeping the iOS port in its
// own build means neither side can break the other's CI.

pluginManagement {
    repositories {
        google()
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

rootProject.name = "ArchiveTuneIOS"
include(":shared")
