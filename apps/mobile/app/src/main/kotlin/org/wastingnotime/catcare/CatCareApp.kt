package org.wastingnotime.catcare

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private data class Destination(val route: String, val label: Int, val icon: Int)

private val destinations =
    listOf(
        Destination("today", R.string.nav_today, R.drawable.ic_nav_today),
        Destination("cats", R.string.nav_cats, R.drawable.ic_nav_cats),
        Destination("history", R.string.nav_history, R.drawable.ic_nav_history),
    )

@OptIn(ExperimentalMaterial3Api::class)
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
    val reminderPrompt by model.reminderPrompt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    var more by remember { mutableStateOf(false) }
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
    val primary = screen in listOf("today", "cats", "history")
    val wide =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    BackHandler(enabled = screen != "today" && draft.isEmpty, onBack = model::back)
    CareTheme {
        val title =
            tr(
                when (screen) {
                    "cats" -> R.string.nav_cats
                    "history" -> R.string.nav_history
                    "entry" -> if (state.history.values.flatten().find { it.id == model.entryId.value }?.type == "observation") R.string.observation_details else R.string.entry_details
                    "data" -> R.string.nav_data
                    "settings" -> R.string.settings
                    "about" -> R.string.about
                    else -> R.string.nav_today
                }
            )
        Scaffold(
            containerColor = Paper,
            topBar = {
                TopAppBar(
                    title = { Text(title, Modifier.testTag("activity-title")) },
                    navigationIcon = {
                        if (!primary)
                            IconButton(
                                onClick = model::back,
                                modifier = Modifier.testTag("navigate-up"),
                            ) {
                                Icon(painterResource(R.drawable.ic_back), tr(R.string.back))
                            }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { more = true },
                                modifier = Modifier.testTag("more-options"),
                            ) {
                                Icon(painterResource(R.drawable.ic_more), tr(R.string.more_options))
                            }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                listOf(
                                        "data" to R.string.nav_data,
                                        "settings" to R.string.settings,
                                        "about" to R.string.about,
                                    )
                                    .forEach { (route, label) ->
                                        DropdownMenuItem(
                                            text = { Text(tr(label)) },
                                            onClick = {
                                                more = false
                                                model.navigate(route)
                                            },
                                            modifier = Modifier.testTag("menu-$route"),
                                        )
                                    }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
                )
            },
            bottomBar = {
                if (primary && !wide)
                    NavigationBar(containerColor = Paper) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = screen == destination.route,
                                onClick = { model.navigate(destination.route) },
                                icon = {
                                    Icon(
                                        painterResource(destination.icon),
                                        null,
                                        Modifier.size(26.dp),
                                    )
                                },
                                label = { Text(tr(destination.label)) },
                                modifier = Modifier.testTag("nav-${destination.route}"),
                            )
                        }
                    }
            },
            floatingActionButton = {
                if (primary && screen != "history" && state.cats.isNotEmpty())
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!busy)
                                model.openEditor(
                                    when (screen) {
                                        "cats" -> "cat"
                                        "history" -> "record"
                                        else -> "task"
                                    }
                                )
                        },
                        modifier = Modifier.testTag("create-action"),
                    ) {
                        Text(
                            tr(
                                when (screen) {
                                    "cats" -> R.string.add_cat_action
                                    "history" -> R.string.register_action
                                    else -> R.string.plan_care_action
                                }
                            )
                        )
                    }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                if (primary && wide)
                    NavigationRail(containerColor = Paper) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = screen == destination.route,
                                onClick = { model.navigate(destination.route) },
                                icon = { Icon(painterResource(destination.icon), null) },
                                label = { Text(tr(destination.label)) },
                                modifier = Modifier.testTag("nav-${destination.route}"),
                            )
                        }
                    }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
                    if (error != null && draft.isEmpty) {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            ErrorText(tr(error!!))
                            TextButton(onClick = model::retry) { Text(tr(R.string.retry)) }
                            TextButton(onClick = model::clearError) { Text(tr(R.string.close)) }
                        }
                    }
                    if (state.loaded)
                        screenStates.SaveableStateProvider(screen) {
                            CareScreen(screen, selected, state, busy, reveal, model)
                        }
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
        if (reminderPrompt && draft.isEmpty)
            AlertDialog(
                onDismissRequest = model::dismissReminderPrompt,
                title = { Text(tr(R.string.reminder_offer_title)) },
                text = { Text(tr(R.string.reminder_offer_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.dismissReminderPrompt()
                            model.navigate("settings")
                        }
                    ) {
                        Text(tr(R.string.set_up_reminders))
                    }
                },
                dismissButton = {
                    TextButton(onClick = model::dismissReminderPrompt) {
                        Text(tr(R.string.not_now))
                    }
                },
            )
    }
}

@Composable
internal fun TodayDate(now: Instant) {
    Text(
        now.atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(
                DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)
                    .withLocale(careLocale())
            ),
        modifier = Modifier.testTag("today-date"),
        color = Muted,
        fontSize = 13.sp,
    )
}

@Composable
internal fun Heading(label: String) =
    Text(
        label,
        Modifier.semantics { heading() },
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
    )

@Composable
internal fun CareCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
internal fun Primary(label: String, enabled: Boolean = true, action: () -> Unit) =
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label)
    }

@Composable
internal fun Secondary(
    label: String,
    enabled: Boolean = true,
    description: String? = null,
    action: () -> Unit,
) =
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                if (description != null) contentDescription = description
            },
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label)
    }

@Composable
internal fun Danger(label: String, enabled: Boolean, action: () -> Unit) =
    OutlinedButton(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rust),
    ) {
        Text(label)
    }

@Composable
internal fun Badge(label: String, overdue: Boolean = false) {
    Surface(
        color = if (overdue) MaterialTheme.colorScheme.errorContainer else Sage,
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
internal fun ErrorText(message: String) =
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
internal fun CatFilter(cats: List<CareStore.Cat>, selected: String, choose: (String) -> Unit) {
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
                leadingIcon = { CatAvatar(cat.coat, Modifier.size(28.dp), describe = false) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("filter-${cat.id}"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            "observation" -> tr(R.string.add_observation)
            "record" -> tr(R.string.record_care_only)
            "comment" -> tr(R.string.comment_heading)
            "actions" -> tr(R.string.task_details)
            "complete" -> tr(R.string.complete_question)
            "cancel" -> tr(R.string.cancel_question)
            "defer" -> tr(R.string.defer_care)
            "delete" ->
                if (target == null) tr(R.string.delete_all_question)
                else tr(R.string.delete_cat_question, cat?.name.orEmpty())
            "restore" -> tr(R.string.restore_heading)
            "export" -> tr(R.string.export_question)
            "export-unavailable" -> tr(R.string.export_unavailable_title)
            else -> tr(R.string.storage_heading)
        }
    val editable = kind in listOf("cat", "task", "record", "observation", "comment", "defer")
    val discard by model.discardPrompt.collectAsStateWithLifecycle()
    val errorField by model.errorField.collectAsStateWithLifecycle()
    val fullScreen = editable
    Dialog(
        onDismissRequest = model::requestCloseEditor,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = !fullScreen,
                decorFitsSystemWindows = !fullScreen,
                dismissOnClickOutside = !fullScreen,
            ),
    ) {
        Surface(
            modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier,
            shape = RoundedCornerShape(if (fullScreen) 0.dp else 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                (if (fullScreen) Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                    else
                        Modifier.fillMaxWidth()
                            .heightIn(
                                max =
                                    with(LocalDensity.current) {
                                        LocalWindowInfo.current.containerSize.height.toDp() * .88f
                                    }
                            ))
                    .padding(16.dp)
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.weight(1f, fill = fullScreen)
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
                            var datesExpanded by
                                rememberSaveable(target) {
                                    mutableStateOf(
                                        value("birth").isNotEmpty() ||
                                            value("adoption").isNotEmpty()
                                    )
                                }
                            TextButton(onClick = { datesExpanded = !datesExpanded }) {
                                Text(tr(R.string.optional_details))
                            }
                            if (datesExpanded || errorField == "adoption") {
                                DateField(tr(R.string.birth), value("birth")) {
                                    model.field("birth", it)
                                }
                                ValidationAnchor("adoption", model) {
                                    DateField(tr(R.string.adoption), value("adoption")) {
                                        model.field("adoption", it)
                                    }
                                }
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
                                ValidationAnchor("cat", model) {
                                    FormCatPicker(state.cats, value("cat")) {
                                        model.field("cat", it)
                                    }
                                }
                            else
                                Text(
                                    state.cats.find { it.id == task?.catId }?.name.orEmpty(),
                                    color = Forest,
                                    fontWeight = FontWeight.Bold,
                                )
                            CareCategoryPicker(value("category")) {
                                model.field("category", it)
                            }
                            FormField(
                                tr(R.string.title),
                                value("title"),
                                "title",
                                model,
                                singleLine = true,
                            )
                            ScheduleFields(draft, model)
                            RepeatFields(draft, model)
                        }
                        "record", "observation" -> {
                            ValidationAnchor("cat", model) {
                                FormCatPicker(state.cats, value("cat")) { model.field("cat", it) }
                            }
                            if (kind == "record") Choice(
                                tr(R.string.record_type),
                                RecordTypes.filter { it != "observation" }.associateWith { tr(typeLabel(it)) },
                                value("type"),
                            ) {
                                model.field("type", it)
                            }
                            FormField(
                                tr(if (kind == "observation") R.string.observation_prompt else R.string.description),
                                value("description"),
                                "description",
                                model,
                            )
                            ValidationAnchor("dueDate", model) {
                                DateField(
                                    tr(R.string.occurred_date),
                                    value("dueDate"),
                                    "occurred-date",
                                ) {
                                    model.field("dueDate", it)
                                }
                            }
                            ValidationAnchor("dueTime", model) {
                                TimeField(value("dueTime"), true) { model.field("dueTime", it) }
                            }
                            Text(tr(R.string.record_hint), color = Muted, fontSize = 13.sp)
                        }
                        "comment" -> {
                            state.cats.find { it.id == observation?.catId }?.let { CatBadge(it) }
                            Text(
                                tr(typeLabel(observation?.type ?: "observation")),
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(observation?.description ?: tr(R.string.error_observation))
                            observation?.let {
                                Text(historyDateLabel(it), color = Muted, fontSize = 13.sp)
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
                                    task?.let {
                                        Text(localMoment(it.due), color = Muted)
                                        if (it.days > 0 || it.months > 0)
                                            Text(repeatLabel(it.days, it.months), color = Muted)
                                    }
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
                        "restore" -> {
                            Text(
                                tr(
                                    R.string.restore_preview,
                                    draft.getInt("cats"),
                                    draft.getInt("tasks"),
                                    draft.getInt("history"),
                                    draft.getInt("comments"),
                                )
                            )
                            Text(tr(R.string.restore_warning))
                        }
                        "export-unavailable" -> Text(tr(R.string.export_unavailable))
                        "storage" -> Text(tr(R.string.storage_body))
                    }
                }
                // Keep validation visible alongside the actions, even in a long scrolled form.
                if (error != null && errorField.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ErrorText(tr(error))
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = model::requestCloseEditor,
                        enabled = !busy,
                        modifier = Modifier.testTag("close-editor"),
                    ) {
                        Text(
                            if (kind == "storage") tr(R.string.understood)
                            else if (editable) tr(R.string.cancel_edit) else tr(R.string.close)
                        )
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
                    if (kind == "restore")
                        TextButton(
                            onClick = model::restoreBackup,
                            enabled = !busy,
                            modifier = Modifier.testTag("confirm-restore"),
                        ) {
                            Text(tr(R.string.restore_confirm))
                        }
                    if (kind == "export")
                        TextButton(onClick = { export(target) }, enabled = !busy) {
                            Text(tr(R.string.choose_destination))
                        }
                }
            }
        }
        if (discard)
            AlertDialog(
                onDismissRequest = model::keepEditing,
                title = { Text(tr(R.string.discard_title)) },
                text = { Text(tr(R.string.discard_body)) },
                confirmButton = {
                    TextButton(
                        onClick = model::closeEditor,
                        modifier = Modifier.testTag("discard-changes"),
                    ) {
                        Text(tr(R.string.discard_changes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = model::keepEditing) { Text(tr(R.string.keep_editing)) }
                },
            )
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
    val error by model.error.collectAsStateWithLifecycle()
    val field by model.errorField.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(error, field) { if (error != null && field == key) focus.requestFocus() }
    ValidationAnchor(key, model) {
        OutlinedTextField(
            value,
            { model.field(key, it) },
            Modifier.fillMaxWidth().focusRequester(focus).testTag("field-$key"),
            isError = error != null && field == key,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (number) KeyboardType.Number else KeyboardType.Text
                ),
        )
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormCatPicker(cats: List<CareStore.Cat>, selected: String, choose: (String) -> Unit) {
    Text(tr(R.string.choose_cat_heading), style = MaterialTheme.typography.titleMedium)
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cats.forEach { cat ->
            FilterChip(
                selected = cat.id == selected,
                onClick = { choose(cat.id) },
                label = { Text(cat.name) },
                leadingIcon = { CatAvatar(cat.coat, Modifier.size(28.dp), describe = false) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("avatar-${cat.id}"),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ValidationAnchor(key: String, model: CareViewModel, content: @Composable () -> Unit) {
    val field by model.errorField.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(field, error) {
        if (field == key && error != null) {
            delay(100)
            bring.bringIntoView()
        }
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        content()
        if (field == key && error != null) ErrorText(tr(error!!))
    }
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
                        color =
                            if (active) Sage else MaterialTheme.colorScheme.surfaceContainerLowest,
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
