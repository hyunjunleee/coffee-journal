rootProject.name = "coffee-journal"

// Artifacts come from the official repositories: Google Maven for androidx / com.android / com.google, Maven Central
// for the rest and the Gradle Plugin Portal for plugins. Every file is checked against gradle/verification-metadata.xml.
//
// A build machine that Maven Central rate-limits (HTTP 429) can put a Maven Central mirror first with
//   -Pcoffeejournal.mavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2/
// (or the same line in ~/.gradle/gradle.properties); the checksums above still decide whether a file is accepted.
pluginManagement {
    repositories {
        providers.gradleProperty("coffeejournal.mavenCentralMirror").orNull?.let { maven(it) { name = "MavenCentralMirror" } }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        providers.gradleProperty("coffeejournal.mavenCentralMirror").orNull?.let { maven(it) { name = "MavenCentralMirror" } }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared")
include(":androidApp")
