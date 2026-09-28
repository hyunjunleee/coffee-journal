package com.coffeejournal.android

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Host setup of both platforms: Android system bars, and the iOS target's sources and Info.plist. This Linux build
 * cannot compile Kotlin/Native, so the iOS checks read the sources: every expect needs an iOS actual, or the
 * framework does not link at all once the iOS target is enabled.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PlatformSetupTest {

    @Before
    fun startKoin() = startTestKoin(ApplicationProvider.getApplicationContext())

    @After
    fun stop() {
        stopKoin()
    }

    // ───────────────────────── Android system bars ─────────────────────────

    /** platform-5 / design-2: the app is light-only, so a phone in dark mode must still get dark bar icons. */
    @Test
    @Config(qualifiers = "+night")
    fun systemBars_phoneInDarkMode_keepDarkIconsOverTheIvoryApp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                assertEquals("the device is in dark mode", Configuration.UI_MODE_NIGHT_YES, night)
                val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertTrue("dark status-bar icons", bars.isAppearanceLightStatusBars)
                assertTrue("dark navigation-bar buttons", bars.isAppearanceLightNavigationBars)
            }
        }
    }

    // ───────────────────────── gap #13: Android system integration ─────────────────────────

    /** Resizing in multi-window, unfolding or a display-size change keeps the activity (and a half-filled form). */
    @Test
    fun multiWindowFoldAndDensityChanges_doNotRecreateTheActivity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val first = controller.get()
        val changed = Configuration(first.resources.configuration).apply {
            screenWidthDp = 720; screenHeightDp = 600; smallestScreenWidthDp = 600
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK.inv()) or Configuration.SCREENLAYOUT_SIZE_LARGE
            densityDpi = 320
        }
        controller.configurationChange(changed)
        assertSame("handled in place", first, controller.get())
        assertEquals(600, controller.get().resources.configuration.smallestScreenWidthDp)
        controller.pause().stop().destroy()
    }

    private data class Rule(val domain: String, val path: String)

    /** The <include> rules under [section] (or the whole file when null) of a packaged backup-rules XML. */
    private fun includes(resId: Int, section: String?): Set<Rule> {
        val parser = ApplicationProvider.getApplicationContext<android.content.Context>().resources.getXml(resId)
        val out = mutableSetOf<Rule>()
        var inSection = section == null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name == section) inSection = true
                if (inSection && parser.name == "include") out += Rule(parser.getAttributeValue(null, "domain"), parser.getAttributeValue(null, "path"))
                assertFalse("no exclude-by-default gaps: ${parser.name}", parser.name == "include" && parser.getAttributeValue(null, "domain") in setOf("root", "external"))
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == section) {
                inSection = false
            }
        }
        return out
    }

    /** Auto Backup / device transfer carry the Room database and the photos, never the caches (25 MB cloud quota). */
    @Test
    fun autoBackupRules_keepDatabaseAndPhotos_notCaches() {
        val dbName = Regex("""getDatabasePath\("([^"]+)"\)""").find(File(root, "shared/src/androidMain/kotlin/com/coffeejournal/data/db/DatabaseBuilder.android.kt").readText())!!.groupValues[1]
        val photoDir = Regex("""File\([^,]+filesDir, "([^"]+)"\)""").find(File(root, "shared/src/androidMain/kotlin/com/coffeejournal/data/photo/AndroidPhotoStore.kt").readText())!!.groupValues[1]
        val expected = setOf(Rule("database", dbName), Rule("database", "$dbName-wal"), Rule("file", "$photoDir/"))
        assertEquals(expected, includes(R.xml.data_extraction_rules, "cloud-backup"))
        assertEquals(expected, includes(R.xml.data_extraction_rules, "device-transfer"))
        assertEquals(expected, includes(R.xml.backup_rules, null))
        val manifest = File(root, "androidApp/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        // the caches the app writes live under cacheDir, which no rule includes
        val cachePaths = File(root, "androidApp/src/main/res/xml/file_paths.xml").readText()
        assertTrue(cachePaths.contains("<cache-path"))
        assertFalse(expected.any { it.domain == "cache" || it.path.startsWith("cache") })
    }

    // ───────────────────────── iOS target ─────────────────────────

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun sources(sourceSet: String): Map<File, String> =
        File(root, "shared/src/$sourceSet/kotlin").walk().filter { it.isFile && it.extension == "kt" }.associateWith { it.readText() }

    private val expectDecl = Regex("""\bexpect\s+(?:fun|val|object|class)\s+(\w+)""")
    private val actualDecl = Regex("""\bactual\s+(?:fun|val|object|class)\s+(\w+)""")

    /** Room's KSP processor generates the actual of an `expect object … : RoomDatabaseConstructor`. */
    private fun expectedNames(): Set<String> = sources("commonMain").values.flatMap { text ->
        text.lines().filter { "RoomDatabaseConstructor" !in it }.mapNotNull { line -> expectDecl.find(line)?.groupValues?.get(1) }
    }.toSet()

    private fun actualNames(sourceSet: String): Set<String> =
        sources(sourceSet).values.flatMap { text -> actualDecl.findAll(text).map { it.groupValues[1] }.toList() }.toSet()

    /** platform-1: BackupFileIo had no iOS actuals, so :shared could not compile for iosArm64 / iosSimulatorArm64. */
    @Test
    fun ios_everyExpectHasAnActual() {
        val expected = expectedNames()
        assertTrue("found the expects (${expected.sorted()})", expected.containsAll(listOf("rememberJsonSaver", "rememberJsonOpener", "shareText", "rememberImagePicker", "rememberCameraCapture", "openUrl", "platformModule")))
        assertEquals("iosMain actuals missing", emptySet<String>(), expected - actualNames("iosMain"))
        assertEquals("androidMain actuals missing", emptySet<String>(), expected - actualNames("androidMain"))
    }

    /** The iOS sources cannot be compiled here; at least every bracket, brace and parenthesis must pair up. */
    @Test
    fun ios_sourcesAreBalanced() {
        sources("iosMain").forEach { (file, text) -> assertEquals("unbalanced brackets in ${file.name}", null, firstImbalance(text)) }
    }

    /** platform-2: Compose Multiplatform's strict plist check terminates the app at launch without this key. */
    @Test
    fun ios_infoPlist_disablesMinimumFrameDuration() {
        val plist = File(root, "iosApp/iosApp/Info.plist").readText()
        assertTrue(Regex("""<key>CADisableMinimumFrameDurationOnPhone</key>\s*<true/>""").containsMatchIn(plist))
    }

    /** "현재 위치" asks for "while using the app" only, and iOS shows the user why (the prompt stops the app without it). */
    @Test
    fun ios_infoPlist_saysWhyItAsksForTheLocation() {
        val plist = File(root, "iosApp/iosApp/Info.plist").readText()
        assertTrue(Regex("""<key>NSLocationWhenInUseUsageDescription</key>\s*<string>[^<]*현재 위치[^<]*</string>""").containsMatchIn(plist))
        assertFalse("no background location", plist.contains("NSLocationAlways"))
    }

    /** The picker's "현재 위치" is the one user of the location permissions; nothing asks for background location or the Wi-Fi state. */
    @Test
    fun manifest_locationPermissions_forTheCurrentLocationButtonOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertTrue(requested.containsAll(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in requested)
        assertFalse(Manifest.permission.ACCESS_WIFI_STATE in requested)
    }

    /** platform-14: iOS 18 refuses the one-argument openURL(_:) ("Force returning false"). */
    @Test
    fun ios_openUrl_usesOptionsAndCompletionHandler() {
        val picker = sources("iosMain").entries.single { it.key.name == "ImagePicker.ios.kt" }.value
        val openUrl = picker.substringAfter("actual fun openUrl")
        assertTrue(openUrl.contains(Regex("""openURL\(\s*\w+\s*,\s*options\s*=.*completionHandler\s*=""")))
        assertFalse(openUrl.contains(Regex("""openURL\(\s*\w+\s*\)""")))
    }

    /** Index of the first unmatched bracket outside strings, chars and comments; null when everything pairs up. */
    private fun firstImbalance(text: String): Int? {
        val stack = ArrayDeque<Char>()
        val pairs = mapOf(')' to '(', '}' to '{', ']' to '[')
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                text.startsWith("/*", i) -> i = text.indexOf("*/", i + 2).let { if (it < 0) return i else it + 1 }
                text.startsWith("\"\"\"", i) -> i = text.indexOf("\"\"\"", i + 3).let { if (it < 0) return i else it + 2 }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < text.length && text[j] != c) j += if (text[j] == '\\') 2 else 1
                    if (j >= text.length) return i
                    i = j
                }
                c in "({[" -> stack.addLast(c)
                c in ")}]" -> if (stack.removeLastOrNull() != pairs[c]) return i
            }
            i++
        }
        return if (stack.isEmpty()) null else text.length
    }
}
