package org.wastingnotime.catcare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CareCategoryPicker(selected: String, choose: (String) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.2f) 1 else 2
    val icons = listOf(
        R.drawable.ic_care_feeding, R.drawable.ic_care_hygiene,
        R.drawable.ic_care_medication, R.drawable.ic_care_vet, R.drawable.ic_care_other,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.category), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Categories.zip(icons).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (category, icon) ->
                        val active = categoryLabel(category) == categoryLabel(selected)
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = if (active) Sage else MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Forest else Line),
                        ) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .selectable(active, role = Role.RadioButton, onClick = { choose(category) })
                                    .testTag("category-$category")
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp), tint = Forest)
                                Text(tr(categoryLabel(category)), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
