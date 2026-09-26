package com.coffeejournal.ui.about

/** A library shipped in the app, as its POM describes it; [licenses] are SPDX ids (see [LicenseTexts]). */
data class Library(
    val group: String,
    val artifact: String,
    val version: String,
    val name: String,
    val url: String?,
    val licenses: List<String>,
)

/**
 * The libraries of this platform's build. Android's list is generated from the release runtime classpath by
 * `./gradlew :androidApp:updateThirdPartyNotices` (gradle/third-party-notices.gradle.kts) and checked before every build.
 */
expect val platformLibraries: List<Library>

/** One license and the libraries under it, most used first. */
internal data class LicenseGroup(val spdx: String, val libraries: List<Library>)

internal object LibraryGroups {
    fun byLicense(libraries: List<Library>): List<LicenseGroup> =
        libraries.flatMap { lib -> lib.licenses.map { it to lib } }
            .groupBy({ it.first }, { it.second })
            .map { (spdx, libs) -> LicenseGroup(spdx, libs.sortedWith(compareBy({ it.group }, { it.artifact }))) }
            .sortedWith(compareByDescending<LicenseGroup> { it.libraries.size }.thenBy { it.spdx })
}
