package org.wastingnotime.catcare

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactCalendarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun monthEndSelectionSurvivesRestoration() {
        var chosen: String? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { CompactDateDialog("2027-01-31", { chosen = it }, {}) }
        }
        compose.onNodeWithTag("calendar-next-month").performClick()
        compose.onNodeWithTag("calendar-day-2027-02-31").assertDoesNotExist()
        compose.onNodeWithTag("calendar-day-2027-02-28").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("calendar-day-2027-02-28").assertIsSelected()
        compose.onNodeWithTag("confirm-compact-date").performClick()
        assertEquals("2027-02-28", chosen)
    }

    @Test
    fun yearChoiceNavigatesWithoutChangingTheSelectedDate() {
        var chosen: String? = null
        compose.setContent {
            MaterialTheme { CompactDateDialog("2026-10-23", { chosen = it }, {}) }
        }
        compose.onNodeWithTag("calendar-year-toggle").performClick()
        compose
            .onNodeWithTag("calendar-years")
            .performScrollToNode(hasTestTag("calendar-year-2020"))
        compose.onNodeWithTag("calendar-year-2020").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-day-2020-10-23").assertExists()
        compose.onNodeWithTag("confirm-compact-date").performClick()
        assertEquals("2026-10-23", chosen)
    }
}
