// Third-party notices for the in-app "출처 · 라이선스" screen.
//
// The list of libraries shipped in an app is generated from its classpath (name, version, project URL and licenses
// from each library's POM, following parent POMs when the licenses are inherited):
//   Android (applied by androidApp): the release runtime classpath into
//     shared/src/androidMain/.../ui/about/PlatformLibraries.android.kt   ./gradlew :androidApp:updateThirdPartyNotices
//   iOS (applied by shared when the iOS targets are on): the iosArm64 klibs, plus what Kotlin/Native and skiko bundle
//     without a POM of their own, into shared/src/iosMain/.../PlatformLibraries.ios.kt
//                                            ./gradlew :shared:updateThirdPartyNotices -Pcoffeejournal.iosKlibs=true
// The project that applies this script sets noticesTarget, noticesConfiguration, noticesApp and noticesBundled
// (extra properties) for anything but the Android default.
// checkThirdPartyNotices runs before every build (Android: preBuild; iOS: linking the framework) and fails when a
// dependency change left the committed list stale, so the screen never misses a library. A library whose POM declares
// no license also fails the generation, so that a new dependency's license is looked at by a person before it ships.
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

val noticesExtra = project.extensions.extraProperties
fun noticesSetting(name: String): Any? = if (noticesExtra.has(name)) noticesExtra.get(name) else null

val noticesTarget: File = noticesSetting("noticesTarget") as File?
    ?: rootProject.file("shared/src/androidMain/kotlin/com/coffeejournal/ui/about/PlatformLibraries.android.kt")
val noticesConfigurationName: String = noticesSetting("noticesConfiguration") as String? ?: "releaseRuntimeClasspath"
val noticesApp: String = noticesSetting("noticesApp") as String? ?: "Android app"
val noticesTaskPath: String = project.path + ":updateThirdPartyNotices"

/**
 * Code an app carries without a POM of its own (group, artifact, version, name, url, SPDX id), listed after the
 * generated libraries: for iOS the Kotlin/Native runtime and the Skia build inside skiko.
 */
@Suppress("UNCHECKED_CAST")
val noticesBundled: List<List<String>> = noticesSetting("noticesBundled") as List<List<String>>? ?: emptyList()
val noticesDependencies = dependencies
val noticesConfigurations = configurations

data class NoticePom(
    val name: String?,
    val url: String?,
    val licenses: List<Pair<String, String?>>,
    val parent: Triple<String, String, String>?,
    val packaging: String?,
)

data class NoticeLibrary(
    val group: String,
    val artifact: String,
    val version: String,
    val name: String,
    val url: String?,
    val licenses: List<String>,
)

fun Element.child(tag: String): Element? {
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n is Element && n.tagName == tag) return n
    }
    return null
}

fun Element.children(tag: String): List<Element> {
    val out = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n is Element && n.tagName == tag) out += n
    }
    return out
}

fun Element.text(tag: String): String? = child(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() && "\${" !in it }

fun resolvePom(group: String, module: String, version: String): NoticePom? {
    val result = noticesDependencies.createArtifactResolutionQuery()
        .forModule(group, module, version)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()
    val file = result.resolvedComponents
        .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
        .filterIsInstance<ResolvedArtifactResult>()
        .firstOrNull()?.file ?: return null
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isExpandEntityReferences = false }
    val project = factory.newDocumentBuilder().parse(file).documentElement
    val licenses = project.child("licenses")?.children("license").orEmpty().mapNotNull { l ->
        l.text("name")?.let { it to l.text("url") }
    }
    val parent = project.child("parent")?.let { p ->
        val g = p.text("groupId"); val a = p.text("artifactId"); val v = p.text("version")
        if (g != null && a != null && v != null) Triple(g, a, v) else null
    }
    return NoticePom(project.text("name"), project.text("url"), licenses, parent, project.text("packaging"))
}

/** SPDX id for the license names found in the POMs; anything unknown fails so a person looks at it. */
fun spdx(name: String, url: String?): String {
    val n = name.lowercase()
    val u = url.orEmpty().lowercase()
    return when {
        "apache" in n && "2" in n || "apache.org/licenses/license-2.0" in u -> "Apache-2.0"
        n == "mit" || "mit license" in n || "opensource.org/licenses/mit" in u -> "MIT"
        "bsd" in n && ("3" in n || "new" in n || "revised" in n) -> "BSD-3-Clause"
        // MapLibre Native Android and its gesture / scale-bar plugins declare "BSD" with the 2-clause license's URL
        "bsd" in n && ("2" in n || "simplified" in n) || "bsd" in n && "bsd-2-clause" in u -> "BSD-2-Clause"
        "public domain" in n -> "Public-Domain"
        "eclipse public license" in n && "2.0" in n -> "EPL-2.0"
        else -> throw GradleException("Unknown license \"$name\" ($url): add it to spdx() in gradle/third-party-notices.gradle.kts and to LicenseTexts")
    }
}

fun collectLibraries(): List<NoticeLibrary> {
    val classpath = noticesConfigurations.getByName(noticesConfigurationName)
    val components = classpath.incoming.resolutionResult.allComponents
        // a Kotlin Multiplatform root module only points at its platform module (both are resolved); list the latter
        .filterNot { c -> c.variants.isNotEmpty() && c.variants.all { it.externalVariant.isPresent } }
        .mapNotNull { it.id as? ModuleComponentIdentifier }
        .distinct()
    return components.mapNotNull { id ->
        val own = resolvePom(id.group, id.module, id.version)
            ?: throw GradleException("No POM for ${id.displayName}")
        // a BOM only aligns versions and ships no code
        if (own.packaging == "pom") return@mapNotNull null
        var licenses = own.licenses
        var url = own.url
        var parent = own.parent
        var depth = 0
        while ((licenses.isEmpty() || url == null) && parent != null && depth < 6) {
            val p = resolvePom(parent.first, parent.second, parent.third) ?: break
            if (licenses.isEmpty()) licenses = p.licenses
            if (url == null) url = p.url
            parent = p.parent
            depth++
        }
        if (licenses.isEmpty()) throw GradleException("${id.displayName} declares no license in its POM or parents: check it by hand")
        NoticeLibrary(
            group = id.group, artifact = id.module, version = id.version,
            name = own.name ?: id.module, url = url,
            licenses = licenses.map { (n, u) -> spdx(n, u) }.distinct(),
        )
    }.sortedWith(compareBy({ it.group }, { it.artifact }))
}

fun kotlinString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$") + "\""

fun renderNotices(): String = buildString {
    val libs = collectLibraries() + noticesBundled.map { b -> NoticeLibrary(b[0], b[1], b[2], b[3], b[4], listOf(b[5])) }
    appendLine("// Generated by `./gradlew $noticesTaskPath` from $noticesConfigurationName")
    appendLine("// (gradle/third-party-notices.gradle.kts). Do not edit by hand.")
    appendLine("package com.coffeejournal.ui.about")
    appendLine()
    appendLine("/** Every library in the $noticesApp (${libs.size}), with the licenses its POM declares. */")
    appendLine("actual val platformLibraries: List<Library> = listOf(")
    for (l in libs) {
        append("    Library(")
        append(listOf(l.group, l.artifact, l.version, l.name).joinToString(", ") { kotlinString(it) })
        append(", ").append(l.url?.let { kotlinString(it) } ?: "null")
        append(", listOf(").append(l.licenses.joinToString(", ") { kotlinString(it) }).append(")")
        appendLine("),")
    }
    appendLine(")")
}

tasks.register("updateThirdPartyNotices") {
    group = "documentation"
    description = "Regenerates the in-app third-party library list from $noticesConfigurationName."
    doLast {
        noticesTarget.parentFile.mkdirs()
        noticesTarget.writeText(renderNotices())
        logger.lifecycle("Wrote ${noticesTarget.relativeTo(rootProject.projectDir)}")
    }
}

val checkThirdPartyNotices = tasks.register("checkThirdPartyNotices") {
    group = "verification"
    description = "Fails when the committed third-party library list no longer matches $noticesConfigurationName."
    doLast {
        val expected = renderNotices()
        val actual = if (noticesTarget.exists()) noticesTarget.readText() else ""
        if (expected != actual) {
            throw GradleException(
                "The in-app third-party library list is out of date with the dependencies.\n" +
                    "Run ./gradlew $noticesTaskPath and commit ${noticesTarget.relativeTo(rootProject.projectDir)}.",
            )
        }
    }
}

// Android: every build of the app; iOS: every framework link (the Xcode build), not the klib compilation
tasks.matching { if (noticesApp == "Android app") it.name == "preBuild" else it.name.startsWith("link") && "FrameworkIos" in it.name }
    .configureEach { dependsOn(checkThirdPartyNotices) }
