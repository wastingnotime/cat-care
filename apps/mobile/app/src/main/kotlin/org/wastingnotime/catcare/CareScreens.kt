package org.wastingnotime.catcare

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun CareScreen(
    screen: String,
    selected: String,
    state: CareSnapshot,
    busy: Boolean,
    reveal: String?,
    model: CareViewModel,
) {
    val list = rememberLazyListState()
    var now by remember { mutableStateOf(Instant.now()) }
    var upcoming by rememberSaveable { mutableStateOf(false) }
    var historyType by rememberSaveable { mutableStateOf("all") }
    var historyPeriod by rememberSaveable { mutableStateOf("all") }
    val profileId by model.profileCat.collectAsStateWithLifecycle()
    val entryId by model.entryId.collectAsStateWithLifecycle()
    val selectedCat = state.cats.find { it.id == selected }
    val profile = state.cats.find { it.id == profileId }
    val history = state.history[selected].orEmpty()
    val zone = ZoneId.systemDefault()
    val agenda = careAgenda(state.tasks, now, zone)
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(30_000)
        }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        now = Instant.now()
        onPauseOrDispose {}
    }
    LaunchedEffect(reveal, screen, state.cats) {
        if (screen == "cats")
            state.cats
                .indexOfFirst { it.id == reveal }
                .takeIf { it >= 0 }
                ?.let {
                    list.scrollToItem(it)
                    model.clearReveal()
                }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxHeight().testTag("care-list"),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.cats.isEmpty() && screen in listOf("today", "cats", "history")) {
                item {
                    CareCard {
                        CatIllustration(Modifier.fillMaxWidth().height(150.dp))
                        Heading(tr(R.string.onboarding_title))
                        Text(tr(R.string.onboarding_body))
                        Primary(tr(R.string.add_first_cat), !busy) { model.openEditor("cat") }
                        Text(tr(R.string.offline_tagline), color = Muted)
                    }
                }
            } else
                when (screen) {
                    "today" -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TodayDate(now)
                                Text(
                                    tr(
                                        R.string.agenda_summary,
                                        agenda[AgendaSection.TODAY].orEmpty().size,
                                        agenda[AgendaSection.OVERDUE].orEmpty().size,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (
                                    agenda[AgendaSection.TODAY].isNullOrEmpty() &&
                                        agenda[AgendaSection.OVERDUE].isNullOrEmpty()
                                )
                                    Text(tr(R.string.today_clear), color = Muted)
                            }
                        }
                        AgendaSection.entries.forEach { section ->
                            val tasks = agenda[section].orEmpty()
                            if (tasks.isNotEmpty()) {
                                item(key = section.name) {
                                    val label =
                                        tr(
                                            when (section) {
                                                AgendaSection.OVERDUE -> R.string.agenda_overdue
                                                AgendaSection.TODAY -> R.string.agenda_today
                                                AgendaSection.UPCOMING -> R.string.agenda_upcoming
                                                AgendaSection.UNDATED -> R.string.agenda_undated
                                            }
                                        )
                                    if (section == AgendaSection.UPCOMING)
                                        TextButton(
                                            onClick = { upcoming = !upcoming },
                                            modifier =
                                                Modifier.testTag("toggle-upcoming").semantics {
                                                    stateDescription = if (upcoming) "−" else "+"
                                                },
                                        ) {
                                            Text(
                                                (if (upcoming) "− " else "+ ") +
                                                    tr(R.string.section_count, label, tasks.size)
                                            )
                                        }
                                    else
                                        Text(
                                            label,
                                            Modifier.semantics { heading() },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                }
                                if (section != AgendaSection.UPCOMING || upcoming)
                                    items(tasks, key = { it.id }) {
                                        TaskCard(it, state, now, busy, model)
                                    }
                            }
                        }
                    }
                    "cats" ->
                        items(state.cats, key = { it.id }) { cat ->
                            ClickCard(
                                "cat-card-${cat.id}",
                                tr(R.string.open_profile, cat.name),
                                { model.openProfile(cat.id) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CatAvatar(cat.coat, Modifier.size(56.dp))
                                    Text(
                                        cat.name,
                                        Modifier.weight(1f).padding(horizontal = 12.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    CardMenu(
                                        "cat-menu-${cat.id}",
                                        listOf(
                                            CardAction(tr(R.string.edit_profile)) {
                                                model.openEditor("cat", cat.id)
                                            }
                                        ),
                                        !busy,
                                    )
                                }
                                val catTasks =
                                    careAgenda(state.tasks.filter { it.catId == cat.id }, now, zone)
                                        .values
                                        .flatten()
                                val overdue =
                                    catTasks.count {
                                        CareRules.status(it.state, it.due, now) == "overdue"
                                    }
                                if (overdue > 0)
                                    Badge(quantity(R.plurals.overdue_count, overdue), true)
                                catTasks.firstOrNull()?.let {
                                    Text(
                                        it.title + " · " + relativeDeadline(it.due, now),
                                        color = Muted,
                                    )
                                } ?: Text(tr(R.string.all_up_to_date), color = Muted)
                            }
                        }
                    "profile" -> {
                        if (profile == null) item { Text(tr(R.string.error_cat_missing)) }
                        else {
                            item {
                                CareCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CatAvatar(profile.coat, Modifier.size(64.dp))
                                        Text(
                                            profile.name,
                                            Modifier.weight(1f).padding(start = 12.dp),
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        CardMenu(
                                            "profile-menu",
                                            listOf(
                                                CardAction(tr(R.string.edit_profile)) {
                                                    model.openEditor("cat", profile.id)
                                                }
                                            ),
                                            !busy,
                                        )
                                    }
                                    if (profile.birth.isNotEmpty())
                                        Text(tr(R.string.birth) + ": " + localDate(profile.birth))
                                    if (profile.adoption.isNotEmpty())
                                        Text(
                                            tr(R.string.adoption) +
                                                ": " +
                                                localDate(profile.adoption)
                                        )
                                    TextButton(
                                        onClick = { model.openEditor("task", catId = profile.id) },
                                        enabled = !busy,
                                    ) {
                                        Text(tr(R.string.plan_care))
                                    }
                                    TextButton(
                                        onClick = {
                                            model.openEditor("record", catId = profile.id)
                                        },
                                        enabled = !busy,
                                    ) {
                                        Text(tr(R.string.register_care))
                                    }
                                }
                            }
                            item { Heading(tr(R.string.upcoming_care)) }
                            val tasks =
                                careAgenda(state.tasks.filter { it.catId == profile.id }, now, zone)
                                    .values
                                    .flatten()
                            if (tasks.isEmpty())
                                item { Text(tr(R.string.no_pending), color = Muted) }
                            items(tasks, key = { it.id }) { TaskCard(it, state, now, busy, model) }
                            item { Heading(tr(R.string.recent_history)) }
                            items(state.history[profile.id].orEmpty().take(3), key = { it.id }) {
                                HistoryCard(it, state, model)
                            }
                            item {
                                TextButton(
                                    onClick = {
                                        model.selectCat(profile.id)
                                        model.navigate("history")
                                    }
                                ) {
                                    Text(tr(R.string.view_history))
                                }
                            }
                        }
                    }
                    "history" -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CatFilter(state.cats, selected, model::selectCat)
                                Choice(
                                    tr(R.string.history_type_filter),
                                    linkedMapOf("all" to tr(R.string.all_types)).apply {
                                        (RecordTypes +
                                                listOf(
                                                    "created",
                                                    "completed",
                                                    "edited",
                                                    "deferred",
                                                    "cancelled",
                                                    "profile",
                                                ))
                                            .forEach { put(it, tr(typeLabel(it))) }
                                    },
                                    historyType,
                                ) {
                                    historyType = it
                                }
                                Choice(
                                    tr(R.string.history_period),
                                    linkedMapOf(
                                        "all" to tr(R.string.all_dates),
                                        "7" to tr(R.string.last_week),
                                        "30" to tr(R.string.last_month),
                                    ),
                                    historyPeriod,
                                ) {
                                    historyPeriod = it
                                }
                            }
                        }
                        val filtered =
                            history.filter {
                                (historyType == "all" || it.type == historyType) &&
                                    (historyPeriod == "all" ||
                                        historyDay(it, zone) >=
                                            now.atZone(zone)
                                                .toLocalDate()
                                                .minusDays(historyPeriod.toLong() - 1))
                            }
                        if (filtered.isEmpty())
                            item {
                                Text(
                                    tr(
                                        if (history.isEmpty()) R.string.no_history
                                        else R.string.no_matching_history
                                    ),
                                    color = Muted,
                                )
                            }
                        filtered
                            .groupBy { historyDay(it, zone) }
                            .forEach { (day, entries) ->
                                item(key = day.toString()) {
                                    Text(
                                        relativeDay(day, now),
                                        Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                items(entries, key = { it.id }) { HistoryCard(it, state, model) }
                            }
                    }
                    "entry" -> {
                        val entry = state.history.values.flatten().find { it.id == entryId }
                        if (entry == null) item { Text(tr(R.string.entry_unavailable)) }
                        else {
                            item {
                                CareCard {
                                    state.cats
                                        .find { it.id == entry.catId }
                                        ?.let { CardIdentity(it, tr(typeLabel(entry.type))) }
                                    Text(
                                        historyDescriptionText(entry),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(historyDateLabel(entry), color = Muted)
                                    val recorded =
                                        entry.details?.let { raw ->
                                            runCatching {
                                                JSONObject(raw).optString("recorded_at")
                                                    .takeIf(String::isNotEmpty)
                                                    ?.also { Instant.parse(it) }
                                            }.getOrNull()
                                        }
                                    if (recorded != null)
                                        Text(
                                            tr(R.string.entered_at, historyTime(recorded)),
                                            color = Muted,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    HistoryShareMenu(entry, state)
                                }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        tr(R.string.comments_heading),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    TextButton(
                                        onClick = { model.openEditor("comment", entry.id) },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("comment-${entry.id}"),
                                    ) {
                                        Text(tr(R.string.comment_action))
                                    }
                                }
                            }
                            val comments = state.comments[entry.id].orEmpty()
                            if (comments.isEmpty())
                                item { Text(tr(R.string.no_comments), color = Muted) }
                            items(comments) { comment ->
                                CareCard {
                                    Text(comment.description)
                                    Text(
                                        historyTime(comment.at),
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    "settings" ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                LanguageChoice()
                                ThemeChoice()
                                ReminderSettings()
                            }
                        }
                    "about" ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CareCard {
                                    Text("Cat Care", style = MaterialTheme.typography.titleLarge)
                                    Text(tr(R.string.about_wnt))
                                    Text(tr(R.string.about_purpose))
                                }
                                Heading(tr(R.string.privacy))
                                CareCard {
                                    Text(tr(R.string.privacy_storage))
                                    Text(tr(R.string.privacy_files))
                                    Text(tr(R.string.privacy_delete))
                                }
                            }
                        }
                    "data" ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                BackupSettings(model, busy)
                                Badge(tr(R.string.local_data))
                                Text(tr(R.string.data_hint))
                                CareCard {
                                    Text(
                                        tr(R.string.backup_heading),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(tr(R.string.export_hint))
                                    Secondary(tr(R.string.storage_action), !busy) {
                                        model.openEditor("storage")
                                    }
                                }
                                if (state.cats.isEmpty())
                                    Text(tr(R.string.no_cat_data), color = Muted)
                                else {
                                    CatFilter(state.cats, selected, model::selectCat)
                                    Primary(
                                        tr(R.string.export_cat, selectedCat?.name.orEmpty()),
                                        !busy,
                                    ) {
                                        model.openEditor("export", selected)
                                    }
                                    Secondary(tr(R.string.export_all), !busy) {
                                        model.openEditor("export")
                                    }
                                    Text(
                                        tr(R.string.delete_heading),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(tr(R.string.delete_hint), color = Muted)
                                    Danger(
                                        tr(R.string.delete_cat_data, selectedCat?.name.orEmpty()),
                                        !busy,
                                    ) {
                                        model.openEditor("delete", selected)
                                    }
                                    Danger(tr(R.string.delete_all), !busy) {
                                        model.openEditor("delete")
                                    }
                                }
                            }
                        }
                }
        }
    }
}

@Composable
private fun ClickCard(
    tag: String,
    label: String,
    open: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = open,
        modifier =
            Modifier.fillMaxWidth().testTag(tag).semantics {
                onClick(label) {
                    open()
                    true
                }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

internal data class CardAction(val label: String, val action: () -> Unit)

@Composable
internal fun CardMenu(tag: String, actions: List<CardAction>, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Icon(painterResource(R.drawable.ic_more), tr(R.string.more_options))
        }
        DropdownMenu(open, { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.action()
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: CareStore.Task,
    state: CareSnapshot,
    now: Instant,
    busy: Boolean,
    model: CareViewModel,
) {
    ClickCard(
        "task-${task.id}",
        tr(R.string.task_details),
        { model.openEditor("actions", task.id) },
    ) {
        val cat = state.cats.first { it.id == task.catId }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CatBadge(cat) }
            CardMenu(
                "task-menu-${task.id}",
                listOf(
                    CardAction(tr(R.string.edit)) { model.openEditor("task", task.id) },
                    CardAction(tr(R.string.defer)) { model.openEditor("defer", task.id) },
                    CardAction(tr(R.string.cancel_care)) { model.openEditor("cancel", task.id) },
                ),
                !busy,
            )
        }
        Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            relativeDeadline(task.due, now),
            color = if (CareRules.status(task.state, task.due, now) == "overdue") Rust else Muted,
        )
        if (task.days > 0 || task.months > 0)
            Text(
                repeatLabel(task.days, task.months),
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        val doneLabel = tr(R.string.mark_done_for, task.title)
        Button(
            onClick = { model.openEditor("complete", task.id) },
            enabled = !busy,
            modifier =
                Modifier.heightIn(min = 48.dp).testTag("complete-${task.id}").semantics {
                    contentDescription = doneLabel
                },
        ) {
            Text(tr(R.string.mark_done))
        }
    }
}

@Composable
private fun HistoryCard(entry: CareStore.History, state: CareSnapshot, model: CareViewModel) {
    val description = historyDescriptionText(entry)
    ClickCard(
        "entry-${entry.id}",
        tr(R.string.open_entry, description),
        { model.openHistoryEntry(entry.id) },
    ) {
        state.cats
            .find { it.id == entry.catId }
            ?.let { CardIdentity(it, tr(typeLabel(entry.type))) }
        Text(description, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(historyDateLabel(entry), color = Muted, style = MaterialTheme.typography.bodySmall)
        val count = state.comments[entry.id].orEmpty().size
        if (count > 0) Text(quantity(R.plurals.comment_count, count), color = Forest)
    }
}

@Composable
private fun HistoryShareMenu(entry: CareStore.History, state: CareSnapshot) {
    val context = LocalContext.current
    val share =
        historyShareText(
            state.cats.first { it.id == entry.catId }.name,
            tr(typeLabel(entry.type)),
            historyDescriptionText(entry),
            historyDateLabel(entry),
            state.comments[entry.id].orEmpty(),
        )
    CardMenu(
        "entry-menu-${entry.id}",
        listOf(
            CardAction(tr(R.string.share_card)) {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, share)
                        },
                        null,
                    )
                )
            }
        ),
    )
}

@Composable
internal fun relativeDay(day: LocalDate, now: Instant): String =
    when (day) {
        now.atZone(ZoneId.systemDefault()).toLocalDate() -> tr(R.string.agenda_today)
        now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1) -> tr(R.string.tomorrow)
        now.atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1) -> tr(R.string.yesterday)
        else -> localDate(day.toString())
    }

@Composable
internal fun relativeDeadline(due: String, now: Instant): String {
    if (due.isEmpty()) return tr(R.string.status_unknown)
    val local = ZonedDateTime.parse(due).withZoneSameInstant(ZoneId.systemDefault())
    return tr(
        R.string.relative_time,
        relativeDay(local.toLocalDate(), now),
        CareDates.time(
            local.toLocalTime().toString(),
            careLocale(),
            DateFormat.is24HourFormat(LocalContext.current),
        ),
    )
}
