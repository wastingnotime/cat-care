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
    private val mutableError = MutableStateFlow<Int?>(null)
    val error = mutableError.asStateFlow()
    private val mutableMessage = MutableStateFlow<Int?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableReveal = MutableStateFlow<String?>(null)
    val reveal = mutableReveal.asStateFlow()

    init {
        runAction { withContext(Dispatchers.IO) { reload() } }
    }

    private fun reload() {
        val cats = store.cats()
        val history = cats.associate { it.id to store.history(it.id) }
        val comments = history.values.flatten().associate { it.id to store.comments(it.id) }

        mutableSnapshot.value =
            CareSnapshot(
                cats,
                store.tasks(null),
                history,
                comments,
                cats.associate { it.id to store.summary(it.id, Instant.now()) },
                true,
            )
        CareReminders.sync(getApplication())
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
                    if (error is CareValidation) issueLabel(error.issue) else R.string.error_storage
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
                    val scheduled =
                        task
                            ?.due
                            ?.takeIf { it.isNotEmpty() }
                            ?.let {
                                ZonedDateTime.parse(it).withZoneSameInstant(ZoneId.systemDefault())
                            }
                    putString("dueDate", scheduled?.toLocalDate()?.toString().orEmpty())
                    putString("dueTime", scheduled?.toLocalTime()?.toString().orEmpty())
                    putString("zone", ZoneId.systemDefault().id)
                    putString(
                        "repeatMode",
                        if ((task?.months ?: 0) > 0) "months"
                        else if ((task?.days ?: 0) > 0) "days" else "none",
                    )
                    putString(
                        "interval",
                        when {
                            (task?.months ?: 0) > 0 -> task!!.months
                            (task?.days ?: 0) > 0 -> task!!.days
                            else -> 1
                        }.toString(),
                    )
                    putInt("version", task?.version ?: 0)
                    if (kind == "defer") {
                        putString("dueDate", "")
                        putString("dueTime", "")
                    }
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
        fun interval(unit: String): Int {
            if (value("repeatMode") != unit) return 0
            val number = value("interval").toIntOrNull()
            careRequire(number != null && number > 0, CareIssue.RECURRENCE)
            return number!!
        }
        fun scheduled() =
            CareRules.due(value("dueDate"), value("dueTime"), ZoneId.of(value("zone")))
        runAction {
            val result =
                withContext(Dispatchers.IO) {
                    val target = values.getString("target")
                    val task =
                        target
                            ?.let { id -> store.tasks(null).find { it.id == id } }
                            ?.copy(version = values.getInt("version"))
                    fun requireTask() = task ?: throw CareValidation(CareIssue.TASK_MISSING)
                    fun chosenCat(): String = CareRules.required(value("cat"), CareIssue.CAT)
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
                            careRequire(target == null || task != null, CareIssue.TASK_MISSING)
                            store.saveTask(
                                chosenCat(),
                                task,
                                value("title"),
                                value("category"),
                                scheduled(),
                                interval("days"),
                                interval("months"),
                                if (task != null && scheduled() == task.due) task.zone
                                else value("zone"),
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
                            store.transition(requireTask(), "deferred", scheduled())
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
                if (value("kind") == "delete") R.string.message_deleted else R.string.message_saved
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
            mutableError.value = R.string.error_export_scope
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
                mutableMessage.value = R.string.message_exported
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = R.string.error_export
            }
        }
    }

    override fun onCleared() {
        store.close()
    }
}
