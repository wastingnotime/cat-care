package org.wastingnotime.catcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSaveButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun savingKeepsBoundsAnnouncesWorkAndAllowsRetryWhenFinished() {
        val busy = mutableStateOf(false)
        var calls = 0
        var saving = ""
        compose.setContent {
            CareTheme {
                saving = tr(R.string.saving)
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    EditorSaveButton(busy.value) {
                        calls++
                        busy.value = true
                    }
                }
            }
        }
        val button = compose.onNodeWithTag("save-editor")
        val idleBounds = button.fetchSemanticsNode().boundsInRoot
        button.assertIsEnabled().performClick()
        button.assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, saving))
        compose.onNodeWithTag("save-progress", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(idleBounds, button.fetchSemanticsNode().boundsInRoot)
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, calls) }

        // Failed validation/work returns to idle without imposing a minimum spinner time.
        compose.runOnIdle { busy.value = false }
        button.assertIsEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("save-progress", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(idleBounds, button.fetchSemanticsNode().boundsInRoot)
        button.performClick()
        compose.runOnIdle { assertEquals(2, calls) }
    }
}
