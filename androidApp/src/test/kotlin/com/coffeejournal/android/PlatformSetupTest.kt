package com.coffeejournal.android

import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
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
