rootProject.name = "coffee-journal"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Full mirror of Maven Central hosted by Google; listed first because it is not rate limited
        // for this build environment. Missing artifacts fall through to Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Full mirror of Maven Central hosted by Google; listed first because it is not rate limited
        // for this build environment. Missing artifacts fall through to Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
