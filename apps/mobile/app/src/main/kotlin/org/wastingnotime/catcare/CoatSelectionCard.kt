package org.wastingnotime.catcare

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Reaction state belongs to this editor instance, never the saved cat profile. */
@Composable
internal fun CoatSelectionCard(
    coat: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    choose: () -> Unit,
) {
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val eyes = remember { Animatable(1f) }
    var blinkRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected, blinkRequest) {
        // Deselecting cancels the old reaction. Restored/initial selection never blinks.
        eyes.snapTo(1f)
        if (selected && blinkRequest > 0 && durationScale?.scaleFactor != 0f) {
            eyes.animateTo(0f, tween(90))
            eyes.animateTo(1f, tween(130))
        }
    }
    SelectionCard(
        selected = selected,
        choose = {
            if (!selected) blinkRequest++
            choose()
        },
        modifier = modifier.semantics { contentDescription = label }.testTag("avatar-$coat"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CatAvatar(coat, Modifier.size(56.dp), describe = false) {
                if (selected && durationScale?.scaleFactor != 0f) eyes.value else 1f
            }
            Text(
                if (selected) stringResource(R.string.selected_avatar_label, label) else label,
                Modifier.clearAndSetSemantics {},
                color = Forest,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
