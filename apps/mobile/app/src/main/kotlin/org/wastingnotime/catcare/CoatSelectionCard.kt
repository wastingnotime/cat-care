package org.wastingnotime.catcare

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val motionEnabled = durationScale?.scaleFactor != 0f
    val scale = animateFloatAsState(
        if (pressed && motionEnabled) .97f else 1f,
        animationSpec = if (pressed) tween(100) else spring(dampingRatio = 1f, stiffness = 700f),
        label = "coat-press",
    )
    val color by animateColorAsState(
        if (selected) Sage else MaterialTheme.colorScheme.surfaceContainerLowest,
        animationSpec = tween(180),
        label = "coat-selection",
    )
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
    // Keep the selectable target fixed while only its visual contents compress.
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).selectable(
            selected = selected,
            interactionSource = interactions,
            indication = ripple(),
            role = Role.RadioButton,
            onClick = {
                if (!selected) blinkRequest++
                choose()
            },
        ).semantics { contentDescription = label }.testTag("avatar-$coat"),
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                val value = if (durationScale?.scaleFactor == 0f) 1f else scale.value
                scaleX = value
                scaleY = value
            },
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
}
