package com.coffeejournal.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.domain.reference.FlavorWheel
import com.coffeejournal.domain.reference.NoteCategories
import com.coffeejournal.ui.ai.AiHttpRequest
import com.coffeejournal.ui.ai.AndroidSecretStore
import com.coffeejournal.ui.ai.NoteHelperPrompts
import com.coffeejournal.ui.ai.NoteMode
import com.coffeejournal.ui.ai.NoteQuestion
import com.coffeejournal.ui.ai.TavilyPlan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/**
 * The AI helper's pieces outside the screens: the prompts are word for word the evaluation tool's (tools/ai-eval), and
 * the key store's files (with a software AES key: Robolectric has no Android Keystore).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class, sdk = [35])
class AiPlatformTest {
    /** Gradle runs this module's tests in androidApp/, so the repository root is its parent. */
    private fun evalFile(name: String): File {
        val f = File("../tools/ai-eval/$name")
        assertTrue("${f.absolutePath} (working dir ${File("").absolutePath})", f.isFile)
        return f
    }

    @Test
    fun systemPrompts_areTheEvalToolsFiles() {
        assertEquals(evalFile("system_prompt_ko.txt").readText().trimEnd('\n'), NoteHelperPrompts.SEARCH_SYSTEM)
        assertEquals(evalFile("sources_prompt_ko.txt").readText().trimEnd('\n'), NoteHelperPrompts.SOURCES_SYSTEM)
        assertEquals(evalFile("query_prompt_ko.txt").readText().trimEnd('\n'), NoteHelperPrompts.QUERY_SYSTEM)
    }

    /**
     * eval.py question(): its string literals, with {case} and the joined term lists filled in, equal the app's question,
     * without and with the people line; its PEOPLE_NOTE and PEOPLE_DESCRIBE are the app's word for word.
     */
    @Test
    fun questionTemplates_areEvalPysQuestion() {
        val py = evalFile("eval.py").readText()
        val literal = Regex("""f?(['"])((?:\\.|(?!\1)[^\\\n])*)\1""")
        fun unescape(s: String) = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\'", "'")
        fun constant(name: String): String {
            val block = py.substringAfter("\n$name = (", "").substringBefore(")\n")
            assertTrue("$name in eval.py", block.isNotEmpty())
            return literal.findAll(block).joinToString("") { unescape(it.groupValues[2]) }
        }
        val people = mapOf("PEOPLE_NOTE" to constant("PEOPLE_NOTE"), "PEOPLE_DESCRIBE" to constant("PEOPLE_DESCRIBE"))
        assertEquals(NoteHelperPrompts.PEOPLE_NOTE, people["PEOPLE_NOTE"])
        assertEquals(NoteHelperPrompts.PEOPLE_DESCRIBE, people["PEOPLE_DESCRIBE"])
        // the blog search's sites are eval.py's CROWD_KO
        val crowdKo = py.substringAfter("\nCROWD_KO = (").substringBefore(")\n")
        assertEquals(TavilyPlan.BLOGS, Regex("\"([^\"]+)\"").findAll(crowdKo).map { it.groupValues[1] }.toList())

        val body = py.substringAfter("def question(mode, case, terms, subs, people=False):").substringBefore("\ndef ")
        val returns = body.split("return (").drop(1).map { it.substringBeforeLast(")") }
        assertEquals(2, returns.size)
        val terms = NoteHelperPrompts.wheelTerms
        val subs = NoteHelperPrompts.noteCategories
        val ifPeople = Regex("""\(([^()]*?)\s+if people else ""\)""")
        val piece = Regex(literal.pattern + "|PEOPLE_NOTE|PEOPLE_DESCRIBE")
        fun render(block: String, case: String, withPeople: Boolean): String =
            piece.findAll(ifPeople.replace(block) { if (withPeople) it.groupValues[1] else "" }).joinToString("") { m ->
                people[m.value] ?: unescape(
                    m.groupValues[2]
                        .replace("{case}", case)
                        .replace("{', '.join(terms)}", terms.joinToString(", "))
                        .replace("{', '.join(subs)}", subs.joinToString(", ")),
                )
            }
        for (withPeople in listOf(false, true)) {
            val note = NoteQuestion(NoteMode.NOTE, "베르가못 (bergamot)")
            val describe = NoteQuestion(NoteMode.DESCRIBE, "잘 익은 자두 같아요")
            assertEquals(render(returns[0], note.query, withPeople), NoteHelperPrompts.question(note, withPeople))
            assertEquals(render(returns[1], describe.query, withPeople), NoteHelperPrompts.question(describe, withPeople))
        }
        assertTrue(NoteHelperPrompts.question(NoteQuestion(NoteMode.NOTE, "x"), people = true).endsWith("\n" + NoteHelperPrompts.PEOPLE_NOTE))

        // the lists eval.py reads out of the Kotlin sources (wheel_terms) are the app's
        val root = File("..")
        val wheel = File(root, "shared/src/commonMain/kotlin/com/coffeejournal/domain/reference/FlavorWheel.kt").readText()
        val pyTerms = Regex("""Group\("[^"]*", listOf\(([^)]*)\)\)""").findAll(wheel).flatMap { g -> Regex("\"([^\"]+)\"").findAll(g.groupValues[1]).map { it.groupValues[1] } }.toList()
        assertEquals(FlavorWheel.allTerms, pyTerms)
        val notes = File(root, "shared/src/commonMain/kotlin/com/coffeejournal/domain/reference/NoteCategories.kt").readText()
        assertEquals(NoteCategories.all.flatMap { c -> c.subs.map { it.name } }, Regex("""Sub\("([^"]+)"""").findAll(notes).map { it.groupValues[1] }.toList())
    }

    @Test
    fun keyStore_encryptsUnderNoBackupFiles_roundTrips_andForgetsAlteredFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = AndroidSecretStore(context) { key }
        assertTrue(store.supported)
        store.put("gemini", "AQ.secret-value-1234")
        val file = store.fileFor("gemini")
        assertEquals(File(context.noBackupFilesDir, "ai-keys"), file.parentFile)
        assertFalse("never stored in the clear", String(file.readBytes(), Charsets.ISO_8859_1).contains("secret-value"))
        assertEquals("AQ.secret-value-1234", store.get("gemini"))
        // a fresh IV each time: the same key saved twice is not the same file
        val first = file.readBytes()
        store.put("gemini", "AQ.secret-value-1234")
        assertFalse(first.contentEquals(file.readBytes()))
        // an altered file fails the GCM tag: it is dropped, not returned
        file.writeBytes(file.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
        assertNull(store.get("gemini"))
        assertFalse(file.exists())
        store.put("tavily", "tvly-x")
        store.delete("tavily")
        assertNull(store.get("tavily"))
        // without the Android Keystore (as on Robolectric) the store says so instead of failing later
        assertFalse(AndroidSecretStore(context).supported)
    }

    @Test
    fun requests_neverPrintTheirKeys() {
        val r = AiHttpRequest("https://api.tavily.com/search", mapOf("Authorization" to "Bearer tvly-secret"), "{}")
        assertEquals("POST https://api.tavily.com/search (Authorization)", r.toString())
    }
}
