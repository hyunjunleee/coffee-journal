package com.coffeejournal.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.platform.rememberJsonSaver
import com.coffeejournal.ui.theme.Chip
import com.coffeejournal.ui.theme.ChipInput
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Android platform integration: navigation shell, system screens (camera, photo picker, SAF) and shared a11y. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PlatformFlowTest : FlowTestBase() {

    private val preview = hasContentDescription("장비 대표 사진 미리보기")
    private val shadow: ShadowActivity get() = Shadows.shadowOf(compose.activity)

    @Before
    fun clearToasts() = ShadowToast.reset()

    private fun waitForToast(text: String) = waitUntil("toast '$text'") { ShadowToast.getTextOfLatestToast() == text }

    private fun dp(value: Int): Float = value * context.resources.displayMetrics.density

    /** 기타 tab → floating "+" → the equipment form (a full-screen route). */
    private fun openEquipmentForm() {
        launchApp()
        tab("tab-misc")
        clickNode(hasContentDescription("추가") and hasClickAction())
        waitFor(button("대표 사진"), "equipment form")
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun deliver(started: ShadowActivity.IntentForResult, data: Intent = Intent()) {
        compose.runOnUiThread { shadow.receiveResult(started.intent, Activity.RESULT_OK, data) }
        settle()
    }

    // ───────────────────────── navigation shell ─────────────────────────

    /** data-6 / platform-11: "Route.Misc" is a string prefix of "Route.MiscForm/{type}…", which used to show the tabs. */
    @Test
    fun platform01_bottomBar_onlyOnTabRoots_notOnTheEquipmentForm() {
        openEquipmentForm()
        assertFalse("no bottom tab bar on the full-screen equipment form", has(hasTestTag("tab-misc")) || has(hasTestTag("tab-extract")))
        back()
        waitFor(hasTestTag("tab-misc"), "tab bar back on the 기타 tab")
    }

    /** platform-10: each tab is one element read as its label, with Tab role and selected state. */
    @Test
    fun platform02_bottomTabs_haveTabRoleSelectedStateAndOneLabel() {
        launchApp()
        fun tabNode(tag: String) = node(hasTestTag(tag)).fetchSemanticsNode()
        val misc = tabNode("tab-misc")
        assertEquals(Role.Tab, misc.config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf("기타"), misc.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(false, misc.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, tabNode("tab-extract").config.getOrNull(SemanticsProperties.Selected))
        assertEquals("the icon carries no second copy of the label", 1, compose.onAllNodes(hasContentDescription("기타"), useUnmergedTree = true).fetchSemanticsNodes().size)
        tab("tab-misc")
        assertTrue(has(hasTestTag("tab-misc") and isSelected()))
    }

    /** platform-13: in landscape the navigation bar and the display cutout sit on the sides; content and tabs stay clear. */
    @Test
    fun platform03_sideInsets_padContentAndBottomBar() {
        SampleData.seed()
        launchApp()
        waitForText("5 entries")
        val left = dp(40).toInt()
        val right = dp(48).toInt()
        compose.runOnUiThread {
            val composeView = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, right, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, 0, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)
        }
        settle()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val title = node(hasText("coffee_journal / 2026")).fetchSemanticsNode().boundsInRoot
        val count = node(hasText("5 entries")).fetchSemanticsNode().boundsInRoot
        assertTrue("title clear of the left cutout (${title.left} < $left)", title.left >= left)
        assertTrue("entry count clear of the right navigation bar (${count.right} > ${width - right})", count.right <= width - right)
        assertTrue("first tab clear of the cutout", node(hasTestTag("tab-extract")).fetchSemanticsNode().boundsInRoot.left >= left - 1)
        assertTrue("last tab clear of the navigation bar", node(hasTestTag("tab-misc")).fetchSemanticsNode().boundsInRoot.right <= width - right + 1)
    }

    // ───────────────────────── glyph buttons ─────────────────────────

    /** platform-10: the chip × is a labelled button with a 48dp target while the chip keeps its compact size. */
    @Test
    fun platform04_chipRemove_labelledButtonWith48dpTarget_chipSizeUnchanged() {
        compose.setContent {
            CoffeeJournalTheme {
                Column {
                    var chips by remember { mutableStateOf(listOf("오렌지", "자두")) }
                    ChipInput(chips = chips, onChipsChange = { chips = it }, input = "", onInputChange = {})
                    Spacer(Modifier.height(80.dp))
                    Chip("단독", onRemove = {}, modifier = Modifier.testTag("chip"))
                }
            }
        }
        settle()
        val remove = node(hasContentDescription("오렌지 삭제") and hasClickAction())
        val target = remove.fetchSemanticsNode()
        assertEquals(Role.Button, target.config.getOrNull(SemanticsProperties.Role))
        assertTrue("touch target ${target.boundsInRoot}", target.boundsInRoot.height >= dp(48) - 1 && target.boundsInRoot.width >= dp(48) - 1)
        val chipHeight = node(hasTestTag("chip")).fetchSemanticsNode().boundsInRoot.height
        assertTrue("chip stays compact ($chipHeight px)", chipHeight < dp(36))
        remove.performClick()
        settle()
        assertFalse(has(hasText("오렌지")))
        assertTrue(has(hasText("자두")))
    }

    // ───────────────────────── photo picker / camera ─────────────────────────

    /** platform-12: undecodable picks are refused with the web's message; a real image is attached. */
    @Test
    fun platform05_photoPicker_refusesUndecodableImage_acceptsRealOne() {
        openEquipmentForm()
        clickText("대표 사진")
        val first = shadow.nextStartedActivityForResult
        assertEquals(MediaStore.ACTION_PICK_IMAGES, first.intent.action)
        val junk = File(context.cacheDir, "not-an-image.jpg").apply { writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 104, 101, 105, 99, 1, 2, 3)) }
        deliver(first, Intent().setData(Uri.fromFile(junk)))
        waitForToast("사진 형식을 인식하지 못했어요. 다른 사진으로 시도해보시겠어요?")
        assertFalse("nothing attached", has(preview))

        clickText("대표 사진")
        val second = shadow.nextStartedActivityForResult
        val png = File(context.cacheDir, "real.png").apply { writeBytes(pngBytes()) }
        deliver(second, Intent().setData(Uri.fromFile(png)))
        waitFor(preview, "picked photo attached")
    }

    /** platform-9: no photo picker app → a short message, the form stays. */
    @Test
    fun platform06_photoPickerMissing_showsMessageInsteadOfCrashing() {
        openEquipmentForm()
        Shadows.shadowOf(context as Application).checkActivities(true)
        clickText("대표 사진")
        waitForToast("사진 선택 창을 열 수 없어요.")
        assertTrue("still on the form", has(button("대표 사진")))
    }

    /** platform-12: every capture gets its own file, which is deleted; an empty answer never attaches an older photo. */
    @Test
    fun platform07_camera_freshFilePerCapture_emptyAnswerAttachesNothing() {
        val pm = Shadows.shadowOf(context.packageManager)
        pm.setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        pm.addResolveInfoForIntent(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.example.camera"
                    name = "com.example.camera.Capture"
                    applicationInfo = ApplicationInfo().apply { packageName = "com.example.camera" }
                }
            },
        )
        openEquipmentForm()

        fun captureFile(started: ShadowActivity.IntentForResult): File {
            val uri = IntentCompat.getParcelableExtra(started.intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)!!
            return File(context.cacheDir, uri.path!!.removePrefix("/capture/"))
        }

        clickText("촬영")
        val first = shadow.nextStartedActivityForResult
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, first.intent.action)
        val firstFile = captureFile(first)
        deliver(first) // the camera app answers OK without writing anything
        waitForToast("사진을 읽는 데 실패했어요.")
        assertFalse("an empty capture attaches nothing", has(preview))

        clickText("촬영")
        val second = shadow.nextStartedActivityForResult
        val secondFile = captureFile(second)
        assertNotEquals("each capture writes to its own file", firstFile, secondFile)
        secondFile.parentFile!!.mkdirs()
        secondFile.writeBytes(pngBytes())
        deliver(second)
        waitFor(preview, "captured photo attached")
        waitUntil("capture file deleted after reading") { !secondFile.exists() }
        assertFalse(firstFile.exists())
    }

    // ───────────────────────── backup "save as" / open ─────────────────────────

    /** platform-9: without DocumentsUI the backup buttons report the problem instead of crashing. */
    @Test
    fun platform08_documentPickerMissing_backupReportsFailureWithoutCrashing() {
        launchApp()
        clickText("💾 백업")
        waitForText("전체 데이터 백업")
        Shadows.shadowOf(context as Application).checkActivities(true)
        clickText("💾 전체 데이터 백업")
        waitForText("파일 저장이 취소되었거나 실패했어요. 아래 버튼으로 다시 저장하거나 텍스트로 공유할 수 있어요.")
        waitForToast("파일 저장 창을 열 수 없어요.")
        clickText("📂 백업 파일에서 복원")
        waitForToast("파일 선택 창을 열 수 없어요.")
        assertTrue(has(button("📂 백업 파일에서 복원")))
    }

    private val saveResults = CopyOnWriteArrayList<Boolean>()

    /** Hosts rememberJsonSaver alone; returns the tester and a getter for the current save function. */
    private fun saverHarness(): Pair<StateRestorationTester, () -> (String, String) -> Unit> {
        val tester = StateRestorationTester(compose)
        var save: ((String, String) -> Unit)? = null
        tester.setContent { save = rememberJsonSaver { ok -> saveResults += ok } }
        settle()
        return tester to { save!! }
    }

    private fun waitForSavePicker(): ShadowActivity.IntentForResult {
        waitUntil("save picker opened") { shadow.peekNextStartedActivityForResult() != null }
        return shadow.nextStartedActivityForResult.also { assertEquals(Intent.ACTION_CREATE_DOCUMENT, it.intent.action) }
    }

    /** platform-6: the activity is recreated while DocumentsUI is open; the result still writes the whole backup. */
    @Test
    fun platform09_saveAs_resultAfterRecreation_writesTheBackup() {
        val (tester, save) = saverHarness()
        val json = "{\"exportedAt\":\"2026-09-25T00:00:00.000Z\",\"data\":{\"entries\":[]}}"
        compose.runOnUiThread { save()("커피일지-백업-2026-09-25.json", json) }
        val picker = waitForSavePicker()

        tester.emulateSavedInstanceStateRestore()
        settle()
        val target = File(context.cacheDir, "picked-backup.json").apply { delete(); createNewFile() }
        deliver(picker, Intent().setData(Uri.fromFile(target)))
        waitUntil("save result") { saveResults.isNotEmpty() }
        assertEquals(listOf(true), saveResults.toList())
        assertEquals(json, target.readText())
    }

    /** platform-6 / miscBackup-12: a created document that cannot be filled is removed and reported as failed. */
    @Test
    fun platform10_saveAs_payloadGone_deletesTheCreatedDocument() {
        val (_, save) = saverHarness()
        compose.runOnUiThread { save()("커피일지-백업.json", "{\"data\":{}}") }
        val picker = waitForSavePicker()
        File(context.cacheDir, "backup-save").deleteRecursively() // e.g. the system cleared the cache meanwhile

        val target = File(context.cacheDir, "created-by-picker.json").apply { delete(); createNewFile() }
        deliver(picker, Intent().setData(Uri.fromFile(target)))
        waitUntil("save result") { saveResults.isNotEmpty() }
        assertEquals(listOf(false), saveResults.toList())
        assertFalse("no empty backup file is left behind", target.exists())
    }

    /** An empty payload is never offered to the picker, so it can never be reported as saved. */
    @Test
    fun platform11_saveAs_emptyPayload_failsWithoutOpeningThePicker() {
        val (_, save) = saverHarness()
        compose.runOnUiThread { save()("빈.json", "") }
        waitUntil("save result") { saveResults.isNotEmpty() }
        assertEquals(listOf(false), saveResults.toList())
        assertNull(shadow.peekNextStartedActivityForResult())
    }
}
