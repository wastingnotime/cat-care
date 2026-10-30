package org.wastingnotime.catcare

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
    ValidationAnchor("dueDate", model) {
        DateField(
            tr(if (optional) R.string.due_date else R.string.new_due_date),
            date,
            tag = "due-date",
        ) {
            model.field("dueDate", it)
            if (it.isEmpty()) model.field("dueTime", "")
        }
    }
    ValidationAnchor("dueTime", model) {
        TimeField(time, date.isNotEmpty()) { model.field("dueTime", it) }
    }
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
internal fun TimeField(value: String, enabled: Boolean, change: (String) -> Unit) {
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
        var numeric by rememberSaveable { mutableStateOf(false) }
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
    Text(tr(R.string.repeat), style = MaterialTheme.typography.titleMedium)
    /* Single recurrence choice, including the common presets. */
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val selected =
            when {
                mode == "none" -> "none"
                mode == "days" && interval == "1" -> "daily"
                mode == "days" && interval == "7" -> "weekly"
                mode == "months" && interval == "1" -> "monthly"
                else -> "custom"
            }
        listOf(
                "none" to R.string.repeat_none,
                "daily" to R.string.repeat_daily,
                "weekly" to R.string.repeat_weekly,
                "monthly" to R.string.repeat_monthly,
                "custom" to R.string.repeat_custom,
            )
            .forEach { (key, label) ->
                FilterChip(
                    selected =
                        if (draft.getString("repeatChoice") == "custom") key == "custom"
                        else selected == key,
                    onClick = {
                        model.field("repeatChoice", key)
                        model.field(
                            "repeatMode",
                            if (key == "none") "none"
                            else if (key == "monthly") "months" else "days",
                        )
                        model.field(
                            "interval",
                            if (key == "weekly") "7" else if (key == "custom") "2" else "1",
                        )
                    },
                    label = { Text(tr(label)) },
                )
            }
    }
    val custom =
        draft.getString("repeatChoice") == "custom" ||
            (mode == "days" && interval !in listOf("1", "7")) ||
            (mode == "months" && interval != "1")
    if (custom)
        Choice(
            tr(R.string.repeat_unit),
            linkedMapOf("days" to tr(R.string.repeat_days), "months" to tr(R.string.repeat_months)),
            mode,
        ) {
            model.field("repeatMode", it)
        }
    if (mode != "none") {
        if (custom)
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
                            ZoneId.of(draft.getString("zone")),
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
internal fun historyDescriptionText(entry: CareStore.History): String {
    val details =
        remember(entry.details) {
            entry.details?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    if (details == null) return entry.description
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
            else -> return entry.description
        }
    val lines = mutableListOf(tr(label, details.optString("name", details.optString("title"))))
    if (details.has("due"))
        lines += tr(R.string.scheduled_for, localMoment(details.optString("due")))
    if (details.optString("event") == "task_deferred")
        lines += tr(R.string.previous_due, localMoment(details.optString("from_due")))
    if (details.has("days") || details.has("months"))
        lines += repeatLabel(details.optInt("days"), details.optInt("months"))
    return lines.joinToString("\n")
}

@Composable
internal fun historyShareText(
    cat: String,
    type: String,
    description: String,
    dateLabel: String,
    comments: List<CareStore.Comment>,
): String {
    val lines = mutableListOf("Cat Care • $cat", type, "", description, "", dateLabel)
    if (comments.isNotEmpty()) {
        lines += ""
        lines += quantity(R.plurals.comment_count, comments.size)
        comments.forEach { lines += "• ${it.description}\n  ${historyTime(it.at)}" }
    }
    return lines.joinToString("\n")
}

@Composable
internal fun CardIdentity(cat: CareStore.Cat, label: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { CatBadge(cat) }
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = Forest,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
