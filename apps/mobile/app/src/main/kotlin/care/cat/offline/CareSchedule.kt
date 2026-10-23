package care.cat.offline

import android.app.Activity
import android.os.Bundle
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import org.json.JSONObject

@Composable
internal fun CatBadge(cat: CareStore.Cat) {
    val label = tr(R.string.cat_badge, cat.name)
    Surface(
        color = Sage,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Line),
        modifier =
            Modifier.testTag("cat-badge-${cat.id}").semantics(mergeDescendants = true) {
                contentDescription = label
            },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CatAvatar(cat.coat, Modifier.size(32.dp), describe = false)
            Text(cat.name, color = Forest, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun LanguageChoice() {
    val context = LocalContext.current
    Choice(
        tr(R.string.language),
        linkedMapOf(
            "system" to tr(R.string.language_system),
            "pt-BR" to tr(R.string.language_pt),
            "en-US" to tr(R.string.language_en),
        ),
        AppLanguage.choice(context),
    ) {
        AppLanguage.change(context as Activity, it)
    }
}

@Composable
internal fun ScheduleFields(draft: Bundle, model: CareViewModel, optional: Boolean = true) {
    val date = draft.getString("dueDate").orEmpty()
    val time = draft.getString("dueTime").orEmpty()
    DateField(
        tr(if (optional) R.string.due_date else R.string.new_due_date),
        date,
        tag = "due-date",
    ) {
        model.field("dueDate", it)
        if (it.isEmpty()) model.field("dueTime", "")
    }
    TimeField(time, date.isNotEmpty()) { model.field("dueTime", it) }
    Text(tr(R.string.timezone_hint, draft.getString("zone").orEmpty()), color = Muted)
    if (date.isNotEmpty() || time.isNotEmpty())
        TextButton(
            onClick = {
                model.field("dueDate", "")
                model.field("dueTime", "")
            },
            modifier = Modifier.testTag("clear-schedule"),
        ) {
            Text(tr(R.string.clear_schedule))
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(value: String, enabled: Boolean, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = careLocale()
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("due-time"),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(tr(R.string.time), color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(CareDates.time(value, locale, use24Hour).ifEmpty { tr(R.string.choose_time) })
        }
    }
    if (open) {
        val initial = remember {
            value.takeIf { it.isNotEmpty() }?.let(LocalTime::parse)
                ?: LocalTime.now().withSecond(0).withNano(0)
        }
        val picker =
            rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = use24Hour,
            )
        var numeric by rememberSaveable { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(tr(R.string.choose_time)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).testTag("time-dialog"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (numeric) TimeInput(picker) else TimePicker(picker)
                    TextButton(
                        onClick = { numeric = !numeric },
                        modifier = Modifier.testTag("toggle-time-input"),
                    ) {
                        Text(tr(if (numeric) R.string.time_clock else R.string.time_input))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        change(LocalTime.of(picker.hour, picker.minute).toString())
                        open = false
                    },
                    modifier = Modifier.testTag("confirm-time"),
                ) {
                    Text(tr(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr(R.string.back)) } },
        )
    }
}

@Composable
internal fun repeatLabel(days: Int, months: Int): String =
    when {
        months > 0 -> quantity(R.plurals.repeat_month_count, months)
        days > 0 -> quantity(R.plurals.repeat_day_count, days)
        else -> tr(R.string.repeat_none)
    }

@Composable
internal fun RepeatFields(draft: Bundle, model: CareViewModel) {
    val mode = draft.getString("repeatMode") ?: "none"
    val interval = draft.getString("interval") ?: "1"
    Choice(
        tr(R.string.repeat),
        linkedMapOf(
            "none" to tr(R.string.repeat_none),
            "days" to tr(R.string.repeat_days),
            "months" to tr(R.string.repeat_months),
        ),
        mode,
    ) {
        model.field("repeatMode", it)
    }
    // Presets keep common routines to a single tap; the interval stays editable.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
                Triple("days", "1", R.string.repeat_daily),
                Triple("days", "7", R.string.repeat_weekly),
                Triple("months", "1", R.string.repeat_monthly),
            )
            .forEach { (unit, count, label) ->
                FilterChip(
                    selected = mode == unit && interval == count,
                    onClick = {
                        model.field("repeatMode", unit)
                        model.field("interval", count)
                    },
                    label = { Text(tr(label)) },
                )
            }
    }
    if (mode != "none") {
        FormField(
            tr(R.string.repeat_every),
            interval,
            "interval",
            model,
            singleLine = true,
            number = true,
        )
        Text(tr(R.string.repeat_hint), color = Muted)
        if (mode == "months") Text(tr(R.string.repeat_month_hint), color = Muted)
        val count = interval.toIntOrNull()?.takeIf { it > 0 }
        val due =
            runCatching {
                    CareRules.due(
                        draft.getString("dueDate").orEmpty(),
                        draft.getString("dueTime").orEmpty(),
                        ZoneId.of(draft.getString("zone")),
                    )
                }
                .getOrNull()
        val next =
            if (count == null || due.isNullOrEmpty()) null
            else
                runCatching {
                        CareRules.successor(
                            due,
                            if (mode == "days") count else 0,
                            if (mode == "months") count else 0,
                        )
                    }
                    .getOrNull()
        if (count != null)
            Text(
                repeatLabel(if (mode == "days") count else 0, if (mode == "months") count else 0),
                color = Forest,
                fontWeight = FontWeight.Bold,
            )
        Text(
            if (count == null) tr(R.string.error_recurrence)
            else if (next == null) tr(R.string.repeat_needs_date)
            else tr(R.string.next_care, localMoment(next)),
            color = Muted,
            modifier = Modifier.testTag("repeat-preview"),
        )
    }
}

/** Localize generated events without translating owner-authored notes or legacy prose. */
@Composable
internal fun HistoryDescription(entry: CareStore.History) {
    val details =
        remember(entry.details) {
            entry.details?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    if (details == null) {
        Text(entry.description)
        return
    }
    val label =
        when (details.optString("event")) {
            "profile_created" -> R.string.history_profile_created
            "profile_edited" -> R.string.history_profile_edited
            "task_created" -> R.string.history_task_created
            "task_edited" -> R.string.history_task_edited
            "task_completed" -> R.string.history_task_completed
            "task_cancelled" -> R.string.history_task_cancelled
            "task_deferred" -> R.string.history_task_deferred
            "task_next" -> R.string.history_task_next
            else -> null
        }
    if (label == null) {
        Text(entry.description)
        return
    }
    Text(tr(label, details.optString("name", details.optString("title"))))
    if (details.has("due"))
        Text(tr(R.string.scheduled_for, localMoment(details.optString("due"))), color = Muted)
    if (details.optString("event") == "task_deferred")
        Text(tr(R.string.previous_due, localMoment(details.optString("from_due"))), color = Muted)
    if (details.has("days") || details.has("months"))
        Text(repeatLabel(details.optInt("days"), details.optInt("months")), color = Muted)
}
