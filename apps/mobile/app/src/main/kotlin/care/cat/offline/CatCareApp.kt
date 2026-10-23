package care.cat.offline

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter

private data class Destination(val route: String, val label: Int, val icon: Int)

private val destinations =
    listOf(
        Destination("today", R.string.nav_today, R.drawable.ic_nav_today),
        Destination("cats", R.string.nav_cats, R.drawable.ic_nav_cats),
        Destination("history", R.string.nav_history, R.drawable.ic_nav_history),
        Destination("data", R.string.nav_data, R.drawable.ic_nav_data),
    )

@Composable
fun CatCareApp(model: CareViewModel) {
    val state by model.snapshot.collectAsStateWithLifecycle()
    val screen by model.screen.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val reveal by model.reveal.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
            model::export,
        )
    val messageText = message?.let { tr(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            model.clearMessage()
        }
    }
    BackHandler(enabled = screen != "today" && draft.isEmpty) { model.navigate("today") }
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Forest,
                onPrimary = androidx.compose.ui.graphics.Color.White,
                primaryContainer = Sage,
                onPrimaryContainer = Forest,
                background = Paper,
                onBackground = Ink,
                surface = Paper,
                onSurface = Ink,
                tertiary = Forest,
                onTertiary = androidx.compose.ui.graphics.Color.White,
                tertiaryContainer = Sage,
                onTertiaryContainer = Forest,
                secondary = Forest,
                secondaryContainer = Sage,
                onSecondaryContainer = Ink,
                surfaceTint = Forest,
                surfaceContainerHighest = Sage,
                surfaceContainerHigh = Paper,
                surfaceContainer = Paper,
                surfaceContainerLow = Paper,
                surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
                surfaceVariant = Sage,
                onSurfaceVariant = Muted,
                outline = Line,
                error = Rust,
            ),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
    ) {
        Scaffold(
            containerColor = Paper,
            topBar = {
                Surface(color = Paper) {
                    Row(
                        Modifier.fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_nav_cats),
                            null,
                            Modifier.size(32.dp),
                            tint = Forest,
                        )
                        Text(
                            "cat care",
                            Modifier.padding(start = 10.dp).weight(1f),
                            color = Forest,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (
                            with(LocalDensity.current) {
                                LocalWindowInfo.current.containerSize.width.toDp()
                            } >= 360.dp && LocalDensity.current.fontScale <= 1.2f
                        )
                            Text(
                                tr(R.string.brand_tagline),
                                color = Muted,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                            )
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Paper) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = screen == destination.route,
                            onClick = { model.navigate(destination.route) },
                            icon = {
                                Icon(painterResource(destination.icon), null, Modifier.size(26.dp))
                            },
                            label = {
                                Text(
                                    tr(destination.label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.testTag("nav-${destination.route}"),
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = Sage,
                                    selectedIconColor = Forest,
                                    selectedTextColor = Forest,
                                ),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
                if (error != null && draft.isEmpty) {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        ErrorText(tr(error!!))
                        TextButton(onClick = model::retry) { Text(tr(R.string.retry)) }
                        TextButton(onClick = model::clearError) { Text(tr(R.string.close)) }
                    }
                }
                if (state.loaded)
                    key(screen, if (screen == "history") selected else "") {
                        CareScreen(screen, selected, state, busy, reveal, model)
                    }
            }
        }
        if (!draft.isEmpty)
            CareEditor(draft, state, busy, error, model) { cat ->
                model.prepareExport(cat)
                model.closeEditor()
                try {
                    export.launch("cat-care-${LocalDate.now()}.json")
                } catch (_: android.content.ActivityNotFoundException) {
                    model.field("kind", "export-unavailable")
                }
            }
    }
}

@Composable
private fun CareScreen(
    screen: String,
    selected: String,
    state: CareSnapshot,
    busy: Boolean,
    reveal: String?,
    model: CareViewModel,
) {
    val list = rememberLazyListState()
    val selectedCat = state.cats.find { it.id == selected }
    val history = state.history[selected].orEmpty()
    LaunchedEffect(reveal, screen, state.cats, history) {
        val index =
            when (screen) {
                "cats" -> state.cats.indexOfFirst { it.id == reveal }.takeIf { it >= 0 }?.plus(1)
                "history" -> history.indexOfFirst { it.id == reveal }.takeIf { it >= 0 }?.plus(1)
                else -> null
            }
        if (index != null) {
            list.scrollToItem(index)
            model.clearReveal()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("care-list"),
        state = list,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.cats.isEmpty() && screen != "data") {
            item {
                Heading(
                    if (screen == "cats") tr(R.string.cats_heading)
                    else if (screen == "history") tr(R.string.history_heading)
                    else if (screen == "data") tr(R.string.data_heading)
                    else tr(R.string.today_heading)
                )
                CareCard {
                    CatIllustration(Modifier.fillMaxWidth().height(170.dp))
                    Text(
                        tr(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(tr(R.string.onboarding_body))
                    Primary(tr(R.string.add_first_cat), !busy) { model.openEditor("cat") }
                    Text(tr(R.string.offline_tagline), color = Muted)
                }
            }
        } else
            when (screen) {
                "today" -> {
                    val planned = state.tasks.filter { it.state == "planned" }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                LocalDate.now()
                                    .format(
                                        DateTimeFormatter.ofLocalizedDate(
                                                java.time.format.FormatStyle.FULL
                                            )
                                            .withLocale(careLocale())
                                    ),
                                color = Muted,
                                fontSize = 13.sp,
                            )
                            Heading(tr(R.string.today_heading))
                            Badge(
                                if (planned.isEmpty()) tr(R.string.all_up_to_date)
                                else quantity(R.plurals.planned_count, planned.size)
                            )
                            Primary(tr(R.string.plan_care_action), !busy) {
                                model.openEditor("task")
                            }
                            Text(
                                tr(R.string.upcoming_care),
                                Modifier.padding(top = 8.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(tr(R.string.routine_hint), color = Muted, fontSize = 12.sp)
                            if (planned.isEmpty()) Text(tr(R.string.no_pending), color = Muted)
                        }
                    }
                    items(planned, key = { it.id }) { task ->
                        CareCard {
                            CatBadge(state.cats.first { it.id == task.catId })
                            Text(
                                tr(categoryLabel(task.category)),
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                task.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val status = CareRules.status(task.state, task.due, Instant.now())
                            Badge(tr(statusLabel(status)), status == "overdue")
                            if (task.due.isNotEmpty()) Text(localMoment(task.due), color = Muted)
                            if (task.days > 0 || task.months > 0)
                                Text(repeatLabel(task.days, task.months), color = Muted)
                            Secondary(tr(R.string.view_care_actions, task.title), !busy) {
                                model.openEditor("actions", task.id)
                            }
                        }
                    }
                }
                "cats" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading(tr(R.string.cats_heading))
                            Text(tr(R.string.cats_hint), color = Muted)
                            Primary(tr(R.string.add_cat_action), !busy) { model.openEditor("cat") }
                        }
                    }
                    items(state.cats, key = { it.id }) { cat ->
                        val summary = state.summaries.getValue(cat.id)
                        CareCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CatAvatar(cat.coat, Modifier.size(64.dp))
                                Text(
                                    cat.name,
                                    Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (summary.pending > 0)
                                Badge(
                                    quantity(R.plurals.pending_count, summary.pending) +
                                        if (summary.overdue > 0)
                                            " · ${quantity(R.plurals.overdue_count, summary.overdue)}"
                                        else "",
                                    summary.overdue > 0,
                                )
                            Text(quantity(R.plurals.responsibility_count, summary.responsibilities))
                            Text(quantity(R.plurals.observation_count, summary.observations))
                            Secondary(tr(R.string.edit_cat_profile, cat.name), !busy) {
                                model.openEditor("cat", cat.id)
                            }
                        }
                    }
                }
                "history" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading(tr(R.string.history_heading))
                            Text(tr(R.string.history_hint), color = Muted)
                            CatFilter(state.cats, selected, model::selectCat)
                            Primary(tr(R.string.register_action), !busy) {
                                model.openEditor("record")
                            }
                            if (history.isEmpty()) Text(tr(R.string.no_history), color = Muted)
                        }
                    }
                    items(history, key = { it.id }) { entry ->
                        CareCard {
                            state.cats.find { it.id == entry.catId }?.let { CatBadge(it) }
                            Text(
                                tr(typeLabel(entry.type)),
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            HistoryDescription(entry)
                            Text(
                                tr(R.string.recorded_at, historyTime(entry.at)),
                                color = Muted,
                                fontSize = 13.sp,
                            )
                            if (entry.type == "observation") {
                                val comments = state.comments[entry.id].orEmpty()
                                Text(
                                    if (comments.isEmpty()) tr(R.string.no_comments)
                                    else quantity(R.plurals.comment_count, comments.size),
                                    color = Forest,
                                    fontWeight = FontWeight.Bold,
                                )
                                comments.forEach { comment ->
                                    Surface(
                                        color = Paper,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(comment.description)
                                            Text(
                                                historyTime(comment.at),
                                                color = Muted,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                                val commentLabel =
                                    tr(R.string.comment_observation, entry.description)
                                OutlinedButton(
                                    onClick = { model.openEditor("comment", entry.id) },
                                    enabled = !busy,
                                    modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            contentDescription = commentLabel
                                        },
                                ) {
                                    Text(tr(R.string.comment_action))
                                }
                            }
                        }
                    }
                }
                "data" ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Heading(tr(R.string.data_heading))
                            LanguageChoice()
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
                            if (state.cats.isEmpty()) Text(tr(R.string.no_cat_data), color = Muted)
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

@Composable
private fun Heading(label: String) =
    Text(
        label,
        Modifier.semantics { heading() },
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
    )

@Composable
private fun CareCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Primary(label: String, enabled: Boolean = true, action: () -> Unit) =
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label)
    }

@Composable
private fun Secondary(label: String, enabled: Boolean = true, action: () -> Unit) =
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label)
    }

@Composable
private fun Danger(label: String, enabled: Boolean, action: () -> Unit) =
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rust),
    ) {
        Text(label)
    }

@Composable
private fun Badge(label: String, overdue: Boolean = false) {
    Surface(
        color = if (overdue) androidx.compose.ui.graphics.Color(0xfff8e9e1) else Sage,
        shape = RoundedCornerShape(24.dp),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (overdue) Rust else Forest,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ErrorText(message: String) =
    Text(
        message,
        Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                error(message)
            }
            .testTag("validation-error"),
        color = Rust,
    )

@Composable
private fun CatFilter(cats: List<CareStore.Cat>, selected: String, choose: (String) -> Unit) {
    Text(tr(R.string.filter_label), color = Muted, fontSize = 11.sp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cats.forEach { cat ->
            FilterChip(
                selected = cat.id == selected,
                onClick = { choose(cat.id) },
                label = { Text(cat.name) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("filter-${cat.id}"),
            )
        }
    }
}

@Composable
private fun CareEditor(
    draft: Bundle,
    state: CareSnapshot,
    busy: Boolean,
    error: Int?,
    model: CareViewModel,
    export: (String?) -> Unit,
) {
    fun value(key: String) = draft.getString(key).orEmpty()
    val kind = value("kind")
    val target = draft.getString("target")
    val task = state.tasks.find { it.id == target }
    val cat = state.cats.find { it.id == target }
    val observation = state.history.values.flatten().find { it.id == target }
    val title =
        when (kind) {
            "cat" -> if (target == null) tr(R.string.add_cat) else tr(R.string.edit_profile)
            "task" -> if (target == null) tr(R.string.plan_care) else tr(R.string.edit_care)
            "record" -> tr(R.string.register_care)
            "comment" -> tr(R.string.comment_heading)
            "actions" -> tr(R.string.care_actions)
            "complete" -> tr(R.string.complete_question)
            "cancel" -> tr(R.string.cancel_question)
            "defer" -> tr(R.string.defer_care)
            "delete" ->
                if (target == null) tr(R.string.delete_all_question)
                else tr(R.string.delete_cat_question, cat?.name.orEmpty())
            "export" -> tr(R.string.export_question)
            "export-unavailable" -> tr(R.string.export_unavailable_title)
            else -> tr(R.string.storage_heading)
        }
    val editable = kind in listOf("cat", "task", "record", "comment", "defer")
    Dialog(onDismissRequest = model::closeEditor) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = androidx.compose.ui.graphics.Color.White,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            with(LocalDensity.current) {
                                LocalWindowInfo.current.containerSize.height.toDp() * .88f
                            }
                    )
                    .padding(24.dp)
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .testTag("editor-scroll"),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (kind) {
                        "cat" -> {
                            FormField(
                                tr(R.string.name),
                                value("name"),
                                "name",
                                model,
                                singleLine = true,
                            )
                            DateField(tr(R.string.birth), value("birth")) {
                                model.field("birth", it)
                            }
                            DateField(tr(R.string.adoption), value("adoption")) {
                                model.field("adoption", it)
                            }
                            Text(
                                tr(R.string.coat_heading),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(tr(R.string.coat_hint), color = Muted)
                            AvatarGrid(
                                CatCoat.entries.map { AvatarOption(it.id, tr(it.label), it.id) },
                                value("coat"),
                            ) {
                                model.field("coat", it)
                            }
                        }
                        "task" -> {
                            if (target == null)
                                FormCatPicker(state.cats, value("cat")) { model.field("cat", it) }
                            else
                                Text(
                                    state.cats.find { it.id == task?.catId }?.name.orEmpty(),
                                    color = Forest,
                                    fontWeight = FontWeight.Bold,
                                )
                            FormField(
                                tr(R.string.title),
                                value("title"),
                                "title",
                                model,
                                singleLine = true,
                            )
                            Choice(
                                tr(R.string.category),
                                Categories.associateWith { tr(categoryLabel(it)) },
                                value("category"),
                            ) {
                                model.field("category", it)
                            }
                            ScheduleFields(draft, model)
                            RepeatFields(draft, model)
                        }
                        "record" -> {
                            FormCatPicker(state.cats, value("cat")) { model.field("cat", it) }
                            Choice(
                                tr(R.string.record_type),
                                RecordTypes.associateWith { tr(typeLabel(it)) },
                                value("type"),
                            ) {
                                model.field("type", it)
                            }
                            FormField(
                                tr(R.string.description),
                                value("description"),
                                "description",
                                model,
                            )
                            Text(tr(R.string.record_hint), color = Muted, fontSize = 13.sp)
                        }
                        "comment" -> {
                            state.cats.find { it.id == observation?.catId }?.let { CatBadge(it) }
                            Text(
                                tr(R.string.type_observation_short),
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(observation?.description ?: tr(R.string.error_observation))
                            observation?.let {
                                Text(historyTime(it.at), color = Muted, fontSize = 13.sp)
                            }
                            FormField(
                                tr(R.string.comment),
                                value("description"),
                                "description",
                                model,
                            )
                        }
                        "actions",
                        "complete",
                        "cancel",
                        "defer" -> {
                            Text(
                                "${state.cats.find { it.id == task?.catId }?.name.orEmpty()} · ${task?.title.orEmpty()}",
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            when (kind) {
                                "actions" -> {
                                    Primary(tr(R.string.complete), !busy) {
                                        model.openEditor("complete", target)
                                    }
                                    Secondary(tr(R.string.edit), !busy) {
                                        model.openEditor("task", target)
                                    }
                                    Secondary(tr(R.string.defer), !busy) {
                                        model.openEditor("defer", target)
                                    }
                                    Danger(tr(R.string.cancel_care), !busy) {
                                        model.openEditor("cancel", target)
                                    }
                                }
                                "complete" ->
                                    if (task != null && (task.days > 0 || task.months > 0))
                                        Text(tr(R.string.successor_hint))
                                "cancel" -> Text(tr(R.string.history_kept))
                                "defer" -> {
                                    Text(
                                        tr(R.string.previous_due, localMoment(task?.due.orEmpty())),
                                        color = Muted,
                                    )
                                    ScheduleFields(draft, model, optional = false)
                                }
                            }
                        }
                        "delete",
                        "export" -> {
                            Text(
                                tr(
                                    R.string.cats_scope,
                                    if (target == null) state.cats.joinToString { it.name }
                                    else cat?.name.orEmpty(),
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (kind == "delete") tr(R.string.delete_scope_hint)
                                else tr(R.string.export_scope_hint)
                            )
                        }
                        "export-unavailable" -> Text(tr(R.string.export_unavailable))
                        "storage" -> Text(tr(R.string.storage_body))
                    }
                }
                // Keep validation visible alongside the actions, even in a long scrolled form.
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorText(tr(error))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = model::closeEditor, enabled = !busy) {
                        Text(if (kind == "storage") tr(R.string.understood) else tr(R.string.back))
                    }
                    if (editable || kind in listOf("complete", "cancel", "delete"))
                        TextButton(
                            onClick = model::saveEditor,
                            enabled = !busy,
                            modifier = Modifier.testTag("save-editor"),
                        ) {
                            Text(
                                when (kind) {
                                    "complete" -> tr(R.string.complete)
                                    "cancel" -> tr(R.string.cancel_care)
                                    "delete" ->
                                        if (target == null) tr(R.string.delete_everything)
                                        else tr(R.string.delete_cat)
                                    else -> tr(R.string.save)
                                },
                                color = if (kind in listOf("delete", "cancel")) Rust else Forest,
                            )
                        }
                    if (kind == "export")
                        TextButton(onClick = { export(target) }, enabled = !busy) {
                            Text(tr(R.string.choose_destination))
                        }
                }
            }
        }
    }
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    key: String,
    model: CareViewModel,
    singleLine: Boolean = false,
    number: Boolean = false,
) {
    OutlinedTextField(
        value,
        { model.field(key, it) },
        Modifier.fillMaxWidth().testTag("field-$key"),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions =
            KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Choice(
    label: String,
    options: Map<String, String>,
    selected: String,
    choose: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            options[selected].orEmpty(),
            {},
            Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag("choice-$label"),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        choose(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    value: String,
    tag: String = "date-$label",
    change: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(localDate(value).ifEmpty { tr(R.string.choose_date) })
        }
    }
    val compact =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } <
            360.dp || LocalDensity.current.fontScale > 1.2f
    if (open && compact) {
        CompactDateDialog(value, change) { open = false }
    } else if (open) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    value
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            LocalDate.parse(it)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                        }
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            change(
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toString()
                            )
                        }
                        open = false
                    },
                    enabled = picker.selectedDateMillis != null,
                ) {
                    Text(tr(R.string.confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            change("")
                            open = false
                        }
                    ) {
                        Text(tr(R.string.clear_date))
                    }
                    TextButton(onClick = { open = false }) { Text(tr(R.string.back)) }
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}

private data class AvatarOption(val id: String, val label: String, val coat: String)

@Composable
private fun FormCatPicker(cats: List<CareStore.Cat>, selected: String, choose: (String) -> Unit) {
    Text(
        tr(R.string.choose_cat_heading),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(tr(R.string.choose_cat_hint), color = Muted)
    AvatarGrid(cats.map { AvatarOption(it.id, it.name, it.coat) }, selected, choose)
}

@Composable
private fun AvatarGrid(options: List<AvatarOption>, selected: String, choose: (String) -> Unit) {
    val columns = if (LocalDensity.current.fontScale > 1.2f) 1 else 2
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val active = option.id == selected
                    Surface(
                        color = if (active) Sage else androidx.compose.ui.graphics.Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Line),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    active,
                                    role = Role.RadioButton,
                                    onClick = { choose(option.id) },
                                )
                                .semantics { contentDescription = option.label }
                                .testTag("avatar-${option.id}")
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CatAvatar(option.coat, Modifier.size(56.dp), describe = false)
                            Text(
                                if (active)
                                    stringResource(R.string.selected_avatar_label, option.label)
                                else option.label,
                                Modifier.clearAndSetSemantics {},
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
