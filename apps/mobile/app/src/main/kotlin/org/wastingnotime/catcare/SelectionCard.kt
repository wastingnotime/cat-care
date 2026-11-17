package org.wastingnotime.catcare

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Shared tactile feedback for coat and care-category choices. */
@Composable
internal fun SelectionCard(
    selected: Boolean,
    choose: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, Line),
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val motionEnabled = durationScale?.scaleFactor != 0f
    val scale = animateFloatAsState(
        if (pressed && motionEnabled) .97f else 1f,
        animationSpec = if (pressed) tween(100) else spring(dampingRatio = 1f, stiffness = 700f),
        label = "selection-press",
    )
    val targetColor = if (selected) Sage else MaterialTheme.colorScheme.surfaceContainerLowest
    val color by animateColorAsState(targetColor, tween(180), label = "selection-color")
    // Selection, border and hit area stay immediate; only the contents compress.
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).selectable(
            selected = selected,
            interactionSource = interactions,
            indication = ripple(),
            role = Role.RadioButton,
            onClick = choose,
        ),
    ) {
        Surface(
            color = if (motionEnabled) color else targetColor,
            shape = RoundedCornerShape(16.dp),
            border = border,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                val value = if (durationScale?.scaleFactor == 0f) 1f else scale.value
                scaleX = value
                scaleY = value
            },
            content = content,
        )
    }
}
