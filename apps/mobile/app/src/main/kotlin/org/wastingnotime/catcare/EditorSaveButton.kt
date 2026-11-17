package org.wastingnotime.catcare

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Reflects actual save work; never holds an editor open to finish an animation. */
@Composable
internal fun EditorSaveButton(busy: Boolean, save: () -> Unit) {
    val saving = tr(R.string.saving)
    val motionEnabled = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    TextButton(
        onClick = save,
        enabled = !busy,
        modifier = Modifier.testTag("save-editor").semantics {
            if (busy) {
                stateDescription = saving
                liveRegion = LiveRegionMode.Polite
            }
        },
    ) {
        // Reserve the indicator slot even when idle so Save and Cancel do not shift.
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                val indicator = Modifier.fillMaxSize().clearAndSetSemantics { testTag = "save-progress" }
                if (motionEnabled)
                    CircularProgressIndicator(modifier = indicator, color = Forest, strokeWidth = 2.dp)
                else
                    // A fixed open ring indicates work without continuous motion or a percentage.
                    CircularProgressIndicator(progress = { .75f }, modifier = indicator, color = Forest, strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(tr(R.string.save), color = Forest)
    }
}
