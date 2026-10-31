package org.wastingnotime.catcare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryFilterChips(
    tag: String,
    label: String,
    options: Map<String, String>,
    selected: String,
    scrollable: Boolean = true,
    choose: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
        val group = Modifier.fillMaxWidth().selectableGroup().testTag("history-filter-$tag")
        val chip: @Composable (String, String) -> Unit = { key, name ->
            FilterChip(
                selected = key == selected,
                onClick = { choose(key) },
                label = { Text(name) },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag("history-filter-$tag-$key"),
            )
        }
        if (scrollable) {
            LazyRow(group, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options.keys.toList(), key = { it }) { key -> chip(key, options.getValue(key)) }
            }
        } else {
            FlowRow(group, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (key, name) -> chip(key, name) }
            }
        }
    }
}
