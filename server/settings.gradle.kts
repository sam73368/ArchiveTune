@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }

    repositories {
        google()
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.amitshekhariitbhu")
                includeGroup("com.github.RouHim")
                includeGroup("com.github.evermind-zz")
                includeGroup("com.github.maxrave-dev")
                includeGroup("com.github.maxrave-dev.PipePipeExtractor")
                includeGroup("com.github.maxrave-dev.BravePipeExtractor")
            }
        }
    }
}

rootProject.name = "archivetune-server"

include(":core")
project(":core").projectDir = file("../core")

include(":server")
project(":server").projectDir = file("service")
