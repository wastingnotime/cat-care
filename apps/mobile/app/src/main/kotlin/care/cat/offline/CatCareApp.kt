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
import java.util.Locale

private data class Destination(val route: String, val label: String, val icon: Int)

private val destinations =
    listOf(
        Destination("today", "Hoje", R.drawable.ic_nav_today),
        Destination("cats", "Gatos", R.drawable.ic_nav_cats),
        Destination("history", "Histórico", R.drawable.ic_nav_history),
        Destination("data", "Dados", R.drawable.ic_nav_data),
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
    LaunchedEffect(message) {
        message?.let {
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
                                "NO SEU RITMO",
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
                                    destination.label,
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
                        ErrorText(error!!)
                        TextButton(onClick = model::retry) { Text("Tentar novamente") }
                        TextButton(onClick = model::clearError) { Text("Fechar") }
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
                    if (screen == "cats") "Seus gatos"
                    else if (screen == "history") "Histórias de cuidado"
                    else if (screen == "data") "Seus dados" else "Hoje, com carinho."
                )
                CareCard {
                    CatIllustration(Modifier.fillMaxWidth().height(170.dp))
                    Text(
                        "Uma rotina de carinho começa aqui.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Organize a rotina, acompanhe pequenas mudanças e guarde a história do seu gato."
                    )
                    Primary("Adicionar meu primeiro gato", !busy) { model.openEditor("cat") }
                    Text("Só você e seus gatos. Até sem internet.", color = Muted)
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
                                        DateTimeFormatter.ofPattern(
                                            "EEEE, dd 'de' MMMM",
                                            Locale("pt", "BR"),
                                        )
                                    ),
                                color = Muted,
                                fontSize = 13.sp,
                            )
                            Heading("Hoje, com carinho.")
                            Badge(
                                if (planned.isEmpty()) "Tudo em dia por aqui"
                                else
                                    "${countLabel(planned.size, "cuidado planejado", "cuidados planejados")} · todos os gatos"
                            )
                            Primary("+ Planejar cuidado", !busy) { model.openEditor("task") }
                            Text(
                                "Próximos cuidados",
                                Modifier.padding(top = 8.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Prazos de rotina, sem indicar urgência clínica.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                            if (planned.isEmpty())
                                Text(
                                    "Nenhum cuidado pendente. Aproveite a companhia.",
                                    color = Muted,
                                )
                        }
                    }
                    items(planned, key = { it.id }) { task ->
                        CareCard {
                            Text(
                                "${state.cats.first { it.id == task.catId }.name} · ${task.category}",
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                task.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            val status = CareRules.status(task.state, task.due, Instant.now())
                            Badge(status, status == "Prazo passado")
                            if (task.due.isNotEmpty())
                                Text(CareRules.displayDue(task.due), color = Muted)
                            if (task.days > 0 || task.months > 0)
                                Text(
                                    "Repete a cada " +
                                        if (task.months > 0) countLabel(task.months, "mês", "meses")
                                        else countLabel(task.days, "dia", "dias"),
                                    color = Muted,
                                )
                            Secondary("Ver ações · ${task.title}", !busy) {
                                model.openEditor("actions", task.id)
                            }
                        }
                    }
                }
                "cats" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading("Seus gatos")
                            Text("Cada história tem seu próprio espaço.", color = Muted)
                            Primary("+ Adicionar gato", !busy) { model.openEditor("cat") }
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
                                    countLabel(summary.pending, "pendente", "pendentes") +
                                        if (summary.overdue > 0)
                                            " · ${countLabel(summary.overdue, "atrasado", "atrasados")}"
                                        else "",
                                    summary.overdue > 0,
                                )
                            Text(
                                countLabel(
                                    summary.responsibilities,
                                    "responsabilidade",
                                    "responsabilidades",
                                )
                            )
                            Text(countLabel(summary.observations, "observação", "observações"))
                            Secondary("Editar perfil de ${cat.name}", !busy) {
                                model.openEditor("cat", cat.id)
                            }
                        }
                    }
                }
                "history" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading("Histórias de cuidado")
                            Text("Os pequenos gestos também ficam na memória.", color = Muted)
                            CatFilter(state.cats, selected, model::selectCat)
                            Primary("Registrar cuidado ou observação", !busy) {
                                model.openEditor("record")
                            }
                            if (history.isEmpty())
                                Text("Nenhum registro por aqui ainda.", color = Muted)
                        }
                    }
                    items(history, key = { it.id }) { entry ->
                        CareCard {
                            Text(
                                "${selectedCat?.name.orEmpty()} · ${typeLabel(entry.type)}",
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(entry.description, fontSize = 17.sp)
                            Text(historyTime(entry.at), color = Muted, fontSize = 13.sp)
                            if (entry.type == "observation") {
                                val comments = state.comments[entry.id].orEmpty()
                                Text(
                                    if (comments.isEmpty()) "Sem comentários"
                                    else "Comentários · ${comments.size}",
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
                                OutlinedButton(
                                    onClick = { model.openEditor("comment", entry.id) },
                                    enabled = !busy,
                                    modifier =
                                        Modifier.fillMaxWidth().semantics {
                                            contentDescription =
                                                "Comentar observação: ${entry.description}"
                                        },
                                ) {
                                    Text("Comentar")
                                }
                            }
                        }
                    }
                }
                "data" ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Heading("Seus dados")
                            Badge("Salvos neste aparelho")
                            Text("Você escolhe o que guardar e o que apagar.")
                            CareCard {
                                Text(
                                    "Guarde uma cópia",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Exporte um arquivo para consulta. A importação e a restauração ainda não estão disponíveis."
                                )
                                Secondary("Como os dados são guardados", !busy) {
                                    model.openEditor("storage")
                                }
                            }
                            if (state.cats.isEmpty())
                                Text("Nenhum dado de gato salvo.", color = Muted)
                            else {
                                CatFilter(state.cats, selected, model::selectCat)
                                Primary("Exportar ${selectedCat?.name.orEmpty()}", !busy) {
                                    model.openEditor("export", selected)
                                }
                                Secondary("Exportar todos os gatos", !busy) {
                                    model.openEditor("export")
                                }
                                Text(
                                    "Excluir dados",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("A exclusão é permanente neste app.", color = Muted)
                                Danger("Apagar dados de ${selectedCat?.name.orEmpty()}", !busy) {
                                    model.openEditor("delete", selected)
                                }
                                Danger("Apagar todos os dados locais", !busy) {
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
    Text("CUIDANDO DE", color = Muted, fontSize = 11.sp)
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
    error: String?,
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
            "cat" -> if (target == null) "Adicionar gato" else "Editar perfil"
            "task" -> if (target == null) "Planejar cuidado" else "Editar cuidado"
            "record" -> "Registrar cuidado"
            "comment" -> "Comentar observação"
            "actions" -> "Ações do cuidado"
            "complete" -> "Concluir cuidado?"
            "cancel" -> "Cancelar cuidado?"
            "defer" -> "Adiar cuidado"
            "delete" ->
                if (target == null) "Apagar todos os dados locais?"
                else "Apagar ${cat?.name.orEmpty()}?"
            "export" -> "Exportar dados locais?"
            "export-unavailable" -> "Seletor de arquivos indisponível"
            else -> "Seus dados, neste aparelho"
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
                            FormField("Nome", value("name"), "name", model, singleLine = true)
                            DateField("Nascimento (opcional)", value("birth")) {
                                model.field("birth", it)
                            }
                            DateField("Adoção (opcional)", value("adoption")) {
                                model.field("adoption", it)
                            }
                            Text(
                                "Pelagem mais parecida",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Escolha o rostinho que mais parece com seu gato.", color = Muted)
                            AvatarGrid(
                                CatCoat.entries.map { AvatarOption(it.id, it.label, it.id) },
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
                            FormField("Título", value("title"), "title", model, singleLine = true)
                            Choice(
                                "Categoria",
                                Categories.associateWith { it },
                                value("category"),
                            ) {
                                model.field("category", it)
                            }
                            Text(
                                "Horário em ${value("zone")}. Em uma hora repetida por mudança de fuso, vale a primeira ocorrência.",
                                color = Muted,
                                fontSize = 13.sp,
                            )
                            FormField(
                                "Prazo · dd/mm/aaaa hh:mm (opcional)",
                                value("due"),
                                "due",
                                model,
                                singleLine = true,
                            )
                            Text(
                                "Recorrência: preencha dias OU meses. Deixe ambos vazios para não repetir. Meses usam o último dia disponível; o próximo parte dessa nova data.",
                                color = Muted,
                                fontSize = 13.sp,
                            )
                            FormField(
                                "A cada quantos dias?",
                                value("days"),
                                "days",
                                model,
                                singleLine = true,
                                number = true,
                            )
                            FormField(
                                "A cada quantos meses?",
                                value("months"),
                                "months",
                                model,
                                singleLine = true,
                                number = true,
                            )
                        }
                        "record" -> {
                            FormCatPicker(state.cats, value("cat")) { model.field("cat", it) }
                            Choice("Tipo de registro", RecordTypes, value("type")) {
                                model.field("type", it)
                            }
                            FormField(
                                "Descrição (inclua valor e unidade nas medições)",
                                value("description"),
                                "description",
                                model,
                            )
                            Text(
                                "Registra o momento atual. Observações não são diagnósticos. Este registro não conclui cuidados planejados.",
                                color = Muted,
                                fontSize = 13.sp,
                            )
                        }
                        "comment" -> {
                            Text(
                                "${state.cats.find { it.id == observation?.catId }?.name.orEmpty()} · Observação",
                                color = Forest,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(observation?.description ?: "Observação indisponível.")
                            observation?.let {
                                Text(historyTime(it.at), color = Muted, fontSize = 13.sp)
                            }
                            FormField("Comentário", value("description"), "description", model)
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
                                    Primary("Concluir", !busy) {
                                        model.openEditor("complete", target)
                                    }
                                    Secondary("Editar", !busy) { model.openEditor("task", target) }
                                    Secondary("Adiar", !busy) { model.openEditor("defer", target) }
                                    Danger("Cancelar cuidado", !busy) {
                                        model.openEditor("cancel", target)
                                    }
                                }
                                "complete" ->
                                    if (task != null && (task.days > 0 || task.months > 0))
                                        Text("Um próximo cuidado será criado pela recorrência.")
                                "cancel" -> Text("O histórico será mantido.")
                                "defer" -> {
                                    Text(
                                        "Prazo anterior: ${CareRules.displayDue(task?.due.orEmpty())}\nNovo horário em ${value("zone")}",
                                        color = Muted,
                                    )
                                    FormField(
                                        "Nova data · dd/mm/aaaa hh:mm",
                                        value("due"),
                                        "due",
                                        model,
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                        "delete",
                        "export" -> {
                            Text(
                                "Gatos: " +
                                    if (target == null) state.cats.joinToString { it.name }
                                    else cat?.name.orEmpty(),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (kind == "delete")
                                    "Perfis, cuidados, registros, históricos e comentários deste escopo serão removidos deste app. Não há recuperação pelo app. Arquivos já exportados não serão apagados."
                                else
                                    "Inclui perfis, cuidados, registros, histórico e comentários apenas deste escopo, em JSON. O arquivo pode conter informações pessoais. Não há restauração pelo app nesta versão."
                            )
                        }
                        "export-unavailable" ->
                            Text("Nenhum seletor de arquivos disponível neste aparelho.")
                        "storage" ->
                            Text(
                                "Funciona sem internet e sem conta. Não há sincronização nem lembretes automáticos nesta versão.\n\nA exportação cria um arquivo JSON para consulta. Esta versão não importa nem restaura arquivos. Desinstalar, limpar os dados ou perder o aparelho pode apagar seus registros. O backup e a transferência automáticos estão desativados para os dados do app; alguns fabricantes podem aplicar regras próprias.\n\nArquivos exportados ficam no destino escolhido e não são apagados pelo app. Se escolher um provedor de nuvem, ele pode exigir internet. Use uma pasta local para exportar offline."
                            )
                    }
                }
                // Keep validation visible alongside the actions, even in a long scrolled form.
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorText(error)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = model::closeEditor, enabled = !busy) {
                        Text(if (kind == "storage") "Entendi" else "Voltar")
                    }
                    if (editable || kind in listOf("complete", "cancel", "delete"))
                        TextButton(
                            onClick = model::saveEditor,
                            enabled = !busy,
                            modifier = Modifier.testTag("save-editor"),
                        ) {
                            Text(
                                when (kind) {
                                    "complete" -> "Concluir"
                                    "cancel" -> "Cancelar cuidado"
                                    "delete" -> if (target == null) "Apagar tudo" else "Apagar gato"
                                    else -> "Salvar"
                                },
                                color = if (kind in listOf("delete", "cancel")) Rust else Forest,
                            )
                        }
                    if (kind == "export")
                        TextButton(onClick = { export(target) }, enabled = !busy) {
                            Text("Escolher destino")
                        }
                }
            }
        }
    }
}

@Composable
private fun FormField(
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
private fun Choice(
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
private fun DateField(label: String, value: String, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(CareRules.displayDate(value).ifEmpty { "Escolher data" })
        }
    }
    if (open) {
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
                    }
                ) {
                    Text("Confirmar")
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
                        Text("Limpar data")
                    }
                    TextButton(onClick = { open = false }) { Text("Voltar") }
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
        "Para qual gato?",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text("Toque no avatar para escolher.", color = Muted)
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

private fun countLabel(count: Int, singular: String, plural: String) =
    "$count ${if (count == 1) singular else plural}"

private fun historyTime(at: String) =
    Instant.parse(at).atZone(ZoneId.systemDefault()).format(CareRules.INPUT)
