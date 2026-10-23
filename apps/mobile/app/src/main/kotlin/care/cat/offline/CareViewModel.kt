package care.cat.offline

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Categories = listOf("Alimentação", "Higiene", "Medicação", "Veterinário", "Outro")
val RecordTypes =
    linkedMapOf(
        "measurement" to "Medição",
        "feeding" to "Alimentação",
        "medication" to "Medicação",
        "hygiene" to "Higiene",
        "care" to "Cuidado",
        "observation" to "Observação (não é diagnóstico)",
    )

fun typeLabel(type: String): String =
    RecordTypes[type]
        ?: mapOf(
            "created" to "Planejamento",
            "edited" to "Edição",
            "completed" to "Conclusão",
            "cancelled" to "Cancelamento",
            "deferred" to "Adiamento",
        )[type]
        ?: "Perfil"

/**
 * UI snapshots are immutable; SQLite work stays off the UI thread. Drafts survive process
 * recreation.
 */
data class CareSnapshot(
    val cats: List<CareStore.Cat> = emptyList(),
    val tasks: List<CareStore.Task> = emptyList(),
    val history: Map<String, List<CareStore.History>> = emptyMap(),
    val comments: Map<String, List<CareStore.Comment>> = emptyMap(),
    val summaries: Map<String, CareStore.CatSummary> = emptyMap(),
    val loaded: Boolean = false,
)

class CareViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {
    private val store = CareStore(application)
    private val preferences = application.getSharedPreferences("MainActivity", 0)
    private val mutableSnapshot = MutableStateFlow(CareSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()
    val screen = saved.getStateFlow("screen", "today")
    val selected = saved.getStateFlow("selected", preferences.getString("cat", null).orEmpty())
    val draft = saved.getStateFlow("draft", Bundle())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableReveal = MutableStateFlow<String?>(null)
    val reveal = mutableReveal.asStateFlow()

    init {
        runAction { withContext(Dispatchers.IO) { reload() } }
    }

    private fun reload() {
        val cats = store.cats()
        val history = cats.associate { it.id to store.history(it.id) }
        val comments =
            history.values
                .flatten()
                .filter { it.type == "observation" }
                .associate { it.id to store.comments(it.id) }
        mutableSnapshot.value =
            CareSnapshot(
                cats,
                store.tasks(null),
                history,
                comments,
                cats.associate { it.id to store.summary(it.id, Instant.now()) },
                true,
            )
    }

    private fun runAction(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try {
                action()
                val cats = mutableSnapshot.value.cats
                if (cats.none { it.id == selected.value })
                    selectCat(cats.firstOrNull()?.id.orEmpty())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableError.value =
                    if (error is IllegalArgumentException) error.message
                    else "Não foi possível acessar os dados. Tente novamente."
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun retry() = runAction { withContext(Dispatchers.IO) { reload() } }

    fun navigate(route: String) {
        saved["screen"] = route
        clearError()
    }

    fun selectCat(id: String) {
        saved["selected"] = id
        preferences.edit { putString("cat", id) }
    }

    fun clearError() {
        mutableError.value = null
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    fun clearReveal() {
        mutableReveal.value = null
    }

    fun field(key: String, value: String) {
        saved["draft"] = Bundle(draft.value).apply { putString(key, value) }
        clearError()
    }

    fun closeEditor() {
        if (!busy.value) {
            saved["draft"] = Bundle()
            clearError()
        }
    }

    fun openEditor(kind: String, target: String? = null) {
        val state = snapshot.value
        val task = state.tasks.find { it.id == target }
        val cat = state.cats.find { it.id == target }
        saved["draft"] =
            Bundle().apply {
                putString("kind", kind)
                putString("target", target)
                if (kind == "cat") {
                    putString("name", cat?.name.orEmpty())
                    putString("birth", cat?.birth.orEmpty())
                    putString("adoption", cat?.adoption.orEmpty())
                    putString("coat", cat?.coat ?: "gray")
                }
                if (kind in listOf("task", "defer", "actions", "complete", "cancel")) {
                    putString("cat", task?.catId ?: state.cats.singleOrNull()?.id.orEmpty())
                    putString("title", task?.title.orEmpty())
                    putString("category", task?.category ?: Categories.first())
                    putString(
                        "due",
                        task
                            ?.due
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { ZonedDateTime.parse(it).format(CareRules.INPUT) }
                            .orEmpty(),
                    )
                    putString(
                        "zone",
                        task
                            ?.due
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { ZonedDateTime.parse(it).zone.id } ?: ZoneId.systemDefault().id,
                    )
                    putString("days", task?.days?.takeIf { it > 0 }?.toString().orEmpty())
                    putString("months", task?.months?.takeIf { it > 0 }?.toString().orEmpty())
                    putInt("version", task?.version ?: 0)
                    if (kind == "defer") putString("due", "")
                }
                if (kind == "record") {
                    putString("cat", selected.value)
                    putString("type", "measurement")
                }
            }
        clearError()
    }

    fun saveEditor() {
        val values = Bundle(draft.value)
        fun value(key: String) = values.getString(key).orEmpty()
        fun number(key: String): Int =
            value(key).trim().let {
                if (it.isEmpty()) 0
                else
                    it.toIntOrNull()
                        ?: throw IllegalArgumentException(
                            "Informe um número inteiro válido para a recorrência."
                        )
            }
        runAction {
            val result =
                withContext(Dispatchers.IO) {
                    val target = values.getString("target")
                    val task =
                        target
                            ?.let { id -> store.tasks(null).find { it.id == id } }
                            ?.copy(version = values.getInt("version"))
                    fun requireTask() = requireNotNull(task) { "Cuidado indisponível." }
                    fun chosenCat(): String = CareRules.required(value("cat"), "o gato")
                    when (value("kind")) {
                        "cat" ->
                            store.saveCat(
                                target,
                                value("name"),
                                value("birth"),
                                value("adoption"),
                                value("coat"),
                            )
                        "task" -> {
                            require(target == null || task != null) { "Cuidado indisponível." }
                            store.saveTask(
                                chosenCat(),
                                task,
                                value("title"),
                                value("category"),
                                CareRules.due(value("due"), ZoneId.of(value("zone"))),
                                number("days"),
                                number("months"),
                            )
                            null
                        }
                        "record" -> {
                            store.record(chosenCat(), value("type"), value("description"))
                            store.history(value("cat")).first().id
                        }
                        "comment" -> {
                            store.comment(requireNotNull(target), value("description"))
                            target
                        }
                        "defer" -> {
                            store.transition(
                                requireTask(),
                                "deferred",
                                CareRules.due(value("due"), ZoneId.of(value("zone"))),
                            )
                            null
                        }
                        "complete" -> {
                            store.transition(requireTask(), "completed", "")
                            null
                        }
                        "cancel" -> {
                            store.transition(requireTask(), "cancelled", "")
                            null
                        }
                        "delete" -> {
                            store.delete(target)
                            null
                        }
                        else -> error("Unknown editor")
                    }
                }
            // A committed mutation must not be resubmitted if refreshing the UI fails.
            saved["draft"] = Bundle()
            mutableMessage.value =
                if (value("kind") == "delete") "Dados apagados deste app."
                else "Salvo neste aparelho."
            withContext(Dispatchers.IO) { reload() }
            when (value("kind")) {
                "cat" -> {
                    selectCat(result.orEmpty())
                    navigate("cats")
                    mutableReveal.value = result
                }
                "record" -> {
                    selectCat(value("cat"))
                    mutableReveal.value = result
                }
                "comment" -> {
                    snapshot.value.history.values
                        .flatten()
                        .find { it.id == result }
                        ?.let { selectCat(it.catId) }
                    mutableReveal.value = result
                }
            }
        }
    }

    fun prepareExport(cat: String?) {
        saved["exportScope"] = cat ?: "*"
    }

    fun export(uri: Uri?) {
        // A document result may arrive before the restored activity finishes loading SQLite.
        if (uri != null && busy.value) {
            viewModelScope.launch {
                busy.first { !it }
                export(uri)
            }
            return
        }
        val scope = saved.get<String>("exportScope")
        saved["exportScope"] = null
        if (uri == null) return
        if (scope == null) {
            mutableError.value = "Selecione novamente o escopo da exportação."
            return
        }
        runAction {
            try {
                withContext(Dispatchers.IO) {
                    val bytes =
                        store.export(scope.takeUnless { it == "*" }).toByteArray(Charsets.UTF_8)
                    requireNotNull(
                            getApplication<Application>()
                                .contentResolver
                                .openOutputStream(uri, "wt")
                        )
                        .use { it.write(bytes) }
                }
                mutableMessage.value = "Exportação salva."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value =
                    "Não foi possível exportar. Um arquivo parcial pode precisar ser removido do destino."
            }
        }
    }

    override fun onCleared() {
        store.close()
    }
}
