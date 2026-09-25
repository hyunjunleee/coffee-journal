package com.coffeejournal.android

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coffeejournal.ui.nav.FormMode
import com.coffeejournal.ui.nav.Route
import com.coffeejournal.ui.nav.appGraph
import com.coffeejournal.ui.theme.CoffeeJournalTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders individual routes on the JVM against seeded data; doubles as a crash check for every screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class RouteScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        startTestKoin(ApplicationProvider.getApplicationContext())
        SampleData.seed()
    }

    private fun settle() { repeat(6) { Thread.sleep(250); compose.waitForIdle() } }

    private fun show(route: Route, file: String, after: (() -> Unit)? = null) {
        compose.setContent {
            CoffeeJournalTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = route) { appGraph(nav) }
            }
        }
        settle()
        after?.invoke()
        compose.onRoot().captureRoboImage("screenshots/$file")
    }

    @Test fun recordForm_brew() = show(Route.RecordForm(mode = FormMode.EXTRACT), "10-form-brew.png")
    @Test fun recordForm_cupping() = show(Route.RecordForm(mode = FormMode.CUPPING), "11-form-cupping.png")
    @Test fun recordForm_edit() = show(Route.RecordForm(mode = FormMode.EXTRACT, entryId = "e1"), "12-form-edit.png")
    @Test fun entryDetail_brew() = show(Route.EntryDetail("e1"), "13-detail-brew.png")
    @Test fun entryDetail_cupping() = show(Route.EntryDetail("e5"), "14-detail-cupping.png")
    @Test fun pantry() = show(Route.Pantry, "15-pantry.png")
    @Test fun pantryEditor() = show(Route.PantryEditor("p2"), "16-pantry-editor.png")
    @Test fun myRecipes() = show(Route.MyRecipes, "17-my-recipes.png")
    @Test fun backup() = show(Route.Backup, "18-backup.png")
    @Test fun miscForm() = show(Route.MiscForm(type = "dripper", itemId = "m1"), "19-misc-form.png")
    @Test fun bookForm() = show(Route.BookForm("b1"), "20-book-form.png")
    @Test fun classForm() = show(Route.ClassForm(), "21-class-form.png")
    @Test fun calendar_study() = show(Route.Calendar, "22-calendar-study.png") { compose.onNodeWithText("스터디").performClick(); settle() }
    @Test fun calendar_cupping() = show(Route.Calendar, "23-calendar-cupping.png") { compose.onNodeWithText("커핑").performClick(); settle() }
    @Test fun bean_notes() = show(Route.Bean, "30-bean-notes.png") { compose.onNodeWithText("커피 노트").performClick(); settle() }
    @Test fun bean_process() = show(Route.Bean, "31-bean-process.png") { compose.onNodeWithText("가공 방식").performClick(); settle() }
    @Test fun bean_roast() = show(Route.Bean, "32-bean-roast.png") { compose.onNodeWithText("배전도").performClick(); settle() }
    @Test fun bean_variety() = show(Route.Bean, "33-bean-variety.png") { compose.onNodeWithText("품종").performClick(); settle() }
    @Test fun bean_specialty() = show(Route.Bean, "34-bean-specialty.png") { compose.onNodeWithText("✦ Competition Lots").performClick(); settle() }
    @Test fun bean_map() = show(Route.Bean, "37-bean-map.png")
    @Test fun bean_roastery() = show(Route.Bean, "38-bean-roastery.png") { compose.onNodeWithText("로스터리").performClick(); settle() }
    @Test fun bean_selection() = show(Route.Bean, "39-bean-selection.png") { compose.onNodeWithText("생두 수입사").performClick(); settle() }
    @Test fun bean_blend() = show(Route.Bean, "40-bean-blend.png") { compose.onNodeWithText("블렌드").performClick(); settle() }
    @Test fun blendForm() = show(Route.BlendForm("bl1"), "41-blend-form.png")
    @Test fun flatItemForm() = show(Route.FlatItemForm(type = "source", itemId = "m4"), "42-flat-item-form.png")
    @Test fun countryDetail() = show(Route.CountryDetail("Ethiopia"), "43-country-detail.png")
    @Test fun variety_detail() = show(Route.VarietyDetail("gesha"), "35-variety-detail.png")
    @Test fun process_detail() = show(Route.ProcessDetail("워시드", "워시드"), "36-process-detail.png")
}
