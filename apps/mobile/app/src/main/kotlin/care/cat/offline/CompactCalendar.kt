package care.cat.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Material's dialog requires 360dp. This calendar keeps narrow windows and large text usable. */
@Composable
internal fun CompactDateDialog(value: String, select: (String) -> Unit, dismiss: () -> Unit) {
    val locale = careLocale()
    var chosen by rememberSaveable { mutableStateOf(value) }
    var displayed by rememberSaveable {
        mutableStateOf(
            (value.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) ?: LocalDate.now())
                .let(YearMonth::from)
                .toString()
        )
    }
    var showYears by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(displayed)
    val monthLabel =
        month.format(
            DateTimeFormatter.ofPattern(
                if (locale.language == "pt") "LLLL 'de' uuuu" else "LLLL uuuu",
                locale,
            )
        )
    val yearDescription = tr(R.string.choose_year, monthLabel)
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = Paper,
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 400.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.heightIn(
                        max =
                            with(LocalDensity.current) {
                                LocalWindowInfo.current.containerSize.height.toDp() * .90f
                            }
                    )
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    tr(R.string.choose_date),
                    Modifier.padding(horizontal = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    localDate(chosen).ifEmpty { tr(R.string.choose_date) },
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Forest,
                )
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val previous = tr(R.string.previous_month)
                        val next = tr(R.string.next_month)
                        TextButton(
                            onClick = { displayed = month.minusMonths(1).toString() },
                            enabled = month > YearMonth.of(1900, 1),
                            modifier =
                                Modifier.size(48.dp).testTag("calendar-prev-month").semantics {
                                    contentDescription = previous
                                },
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium)
                        }
                        TextButton(
                            onClick = { showYears = !showYears },
                            modifier =
                                Modifier.weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("calendar-year-toggle")
                                    .semantics { contentDescription = yearDescription },
                        ) {
                            Text("$monthLabel ▾", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { displayed = month.plusMonths(1).toString() },
                            enabled = month < YearMonth.of(2100, 12),
                            modifier =
                                Modifier.size(48.dp).testTag("calendar-next-month").semantics {
                                    contentDescription = next
                                },
                        ) {
                            Text("›", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    if (showYears) {
                        val years = (1900..2100).chunked(3)
                        val scroll =
                            rememberLazyListState(
                                initialFirstVisibleItemIndex = (month.year - 1900) / 3
                            )
                        LazyColumn(
                            Modifier.height(240.dp).testTag("calendar-years"),
                            state = scroll,
                        ) {
                            items(years) { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { year ->
                                        TextButton(
                                            onClick = {
                                                displayed = month.withYear(year).toString()
                                                showYears = false
                                            },
                                            modifier =
                                                Modifier.weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("calendar-year-$year"),
                                        ) {
                                            Text(
                                                year.toString(),
                                                fontWeight =
                                                    if (year == month.year) FontWeight.Bold
                                                    else FontWeight.Normal,
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { index ->
                                val day = DayOfWeek.of((firstDay.value - 1 + index) % 7 + 1)
                                val name = day.getDisplayName(TextStyle.FULL, locale)
                                Box(
                                    Modifier.weight(1f).height(32.dp).clearAndSetSemantics {
                                        contentDescription = name
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        day.getDisplayName(TextStyle.NARROW, locale),
                                        color = Muted,
                                    )
                                }
                            }
                        }
                        repeat(6) { week ->
                            Row(Modifier.fillMaxWidth()) {
                                repeat(7) { day ->
                                    val number = week * 7 + day - leading + 1
                                    if (number !in 1..month.lengthOfMonth())
                                        Spacer(Modifier.weight(1f).height(48.dp))
                                    else {
                                        val date = month.atDay(number)
                                        val active = chosen == date.toString()
                                        val description =
                                            date.format(
                                                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                                    .withLocale(locale)
                                            )
                                        Box(
                                            Modifier.weight(1f)
                                                .height(48.dp)
                                                .background(
                                                    if (active) Forest else Color.Transparent,
                                                    CircleShape,
                                                )
                                                .border(
                                                    1.dp,
                                                    if (date == LocalDate.now()) Forest
                                                    else Color.Transparent,
                                                    CircleShape,
                                                )
                                                .selectable(
                                                    active,
                                                    role = Role.RadioButton,
                                                    onClick = { chosen = date.toString() },
                                                )
                                                .semantics { contentDescription = description }
                                                .testTag("calendar-day-$date"),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                number.toString(),
                                                Modifier.clearAndSetSemantics {},
                                                color =
                                                    if (active) MaterialTheme.colorScheme.onPrimary
                                                    else Ink,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            select("")
                            dismiss()
                        }
                    ) {
                        Text(tr(R.string.clear_date))
                    }
                    TextButton(onClick = dismiss) { Text(tr(R.string.back)) }
                    TextButton(
                        onClick = {
                            select(chosen)
                            dismiss()
                        },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.testTag("confirm-compact-date"),
                    ) {
                        Text(tr(R.string.confirm))
                    }
                }
            }
        }
    }
}
