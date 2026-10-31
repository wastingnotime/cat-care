package org.wastingnotime.catcare

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

class CareViewModel(
    application: Application,
    private val saved: SavedStateHandle,
    private val store: CareStore,
) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("MainActivity", 0)
    private val mutableSnapshot = MutableStateFlow(CareSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()
    val screen = saved.getStateFlow("screen", "today")
    val selected = saved.getStateFlow("selected", preferences.getString("cat", null).orEmpty())
    val draft = saved.getStateFlow("draft", Bundle())
    val discardPrompt = saved.getStateFlow("discardPrompt", false)
    val reminderPrompt = saved.getStateFlow("reminderPrompt", false)
    val entryId = saved.getStateFlow("entryId", "")
    val errorField = saved.getStateFlow("errorField", "")
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
        val backups = CareBackups(getApplication())
        runCatching { backups.ensureScheduled() }
            .onFailure { backups.preferences.edit().putBoolean("failed", true).apply() }
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
                saved["errorField"] =
                    if (error is CareValidation)
                        when (error.issue) {
                            CareIssue.NAME -> "name"
                            CareIssue.TITLE -> "title"
                            CareIssue.DESCRIPTION,
                            CareIssue.COMMENT -> "description"
                            CareIssue.CAT -> "cat"
                            CareIssue.ADOPTION -> "adoption"
                            CareIssue.RECURRENCE -> "interval"
                            CareIssue.TIME,
                            CareIssue.DST -> "dueTime"
                            CareIssue.DATE,
                            CareIssue.DATETIME,
                            CareIssue.RECURRENCE_DATE,
                            CareIssue.DEFER,
                            CareIssue.OCCURRENCE -> "dueDate"
                            else -> ""
                        }
                    else ""
                mutableError.value =
                    if (error is CareValidation) issueLabel(error.issue) else R.string.error_storage
            } finally {
                mutableBusy.value = false
                consumeNotification()
            }
        }
    }

    fun retry() = runAction { withContext(Dispatchers.IO) { reload() } }

    fun navigate(route: String) {
        if (route == screen.value) return
        if (route in listOf("today", "cats", "notes", "history")) {
            saved["backStack"] = arrayListOf<String>()
        } else {
            saved["backStack"] =
                ArrayList(saved.get<ArrayList<String>>("backStack").orEmpty() + screen.value)
        }
        saved["screen"] = route
        clearError()
    }

    fun back() {
        val stack = saved.get<ArrayList<String>>("backStack").orEmpty()
        saved["screen"] = stack.lastOrNull() ?: "today"
        saved["backStack"] = ArrayList(stack.dropLast(1))
        clearError()
    }

    fun openHistoryEntry(id: String) {
        saved["entryId"] = id
        navigate("entry")
    }

    fun dismissReminderPrompt() {
        saved["reminderPrompt"] = false
    }

    fun openNotificationTask(id: String) {
        // Queue until initial data is loaded; never replace an unfinished editor.
        saved["notificationTask"] = id
        consumeNotification()
    }

    private fun consumeNotification() {
        val id = saved.get<String>("notificationTask") ?: return
        if (!snapshot.value.loaded || !draft.value.isEmpty || busy.value) return
        saved["notificationTask"] = null
        navigate("today")
        if (snapshot.value.tasks.any { it.id == id && it.state == "planned" })
            openEditor("actions", id)
        else mutableMessage.value = R.string.task_no_longer_pending
    }

    fun selectCat(id: String) {
        saved["selected"] = id
        preferences.edit { putString("cat", id) }
    }

    fun clearError() {
        mutableError.value = null
        saved["errorField"] = ""
    }

    fun clearMessage() {
        mutableMessage.value = null
    }

    fun clearReveal() {
        mutableReveal.value = null
    }

    fun field(key: String, value: String) {
        saved["draft"] = Bundle(draft.value).apply {
            putString(key, value)
            if (getString("kind") == "task") {
                if (key == "title") putBoolean("titleCustomized", true)
                if (key == "category" && !getBoolean("titleCustomized", true)) {
                    putString("title", suggestedCareTitle(value))
                }
            }
        }
        clearError()
    }

    fun requestCloseEditor() {
        if (busy.value) return
        val baseline = saved.get<Bundle>("draftBaseline")
        val current = draft.value
        val editable =
            current.getString("kind") in listOf("cat", "task", "record", "observation", "comment", "defer")
        @Suppress("DEPRECATION")
        val changed =
            baseline != null &&
                (baseline.keySet() + current.keySet()).any { baseline.get(it) != current.get(it) }
        if (editable && changed) saved["discardPrompt"] = true else closeEditor()
    }

    fun keepEditing() {
        saved["discardPrompt"] = false
    }

    fun closeEditor() {
        if (!busy.value) {
            if (draft.value.getString("kind") == "restore") discardRestore()
            saved["draft"] = Bundle()
            saved["draftBaseline"] = null
            saved["discardPrompt"] = false
            clearError()
            consumeNotification()
        }
    }

    private fun suggestedCareTitle(category: String): String =
        AppLanguage.context(getApplication()).getString(
            if (categoryLabel(category) == R.string.category_other) R.string.care_title_other
            else categoryLabel(category)
        )

    fun openEditor(kind: String, target: String? = null, catId: String? = null) {
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
                    putString(
                        "cat",
                        task?.catId ?: catId ?: state.cats.singleOrNull()?.id.orEmpty(),
                    )
                    putString("title", task?.title ?: suggestedCareTitle(Categories.first()))
                    putBoolean("titleCustomized", task != null)
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
                if (kind in listOf("record", "observation")) {
                    putString("cat", catId ?: selected.value)
                    putString("type", if (kind == "observation") "observation" else "care")
                    val moment = ZonedDateTime.now().withSecond(0).withNano(0)
                    putString("dueDate", moment.toLocalDate().toString())
                    putString("dueTime", moment.toLocalTime().toString())
                    putString("zone", moment.zone.id)
                }
            }
        saved["draftBaseline"] = Bundle(draft.value)
        saved["discardPrompt"] = false
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
                        "record", "observation" -> {
                            store.record(
                                chosenCat(),
                                value("type"),
                                value("description"),
                                CareRules.occurrence(
                                    value("dueDate"),
                                    value("dueTime"),
                                    ZoneId.of(value("zone")),
                                    Instant.now(),
                                ),
                            )
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
            saved["draftBaseline"] = null
            mutableMessage.value =
                if (value("kind") == "delete") R.string.message_deleted else R.string.message_saved
            withContext(Dispatchers.IO) { reload() }
            when (value("kind")) {
                "cat" -> {
                    selectCat(result.orEmpty())
                    navigate("cats")
                    mutableReveal.value = result
                }
                "record", "observation" -> {
                    selectCat(value("cat"))
                    openHistoryEntry(result!!)
                }
                "comment" -> {
                    snapshot.value.history.values
                        .flatten()
                        .find { it.id == result }
                        ?.let { selectCat(it.catId) }
                    if (screen.value != "entry") openHistoryEntry(result!!)
                }
                "task" -> {
                    val prefs = CareReminders.preferences(getApplication())
                    if (
                        value("dueDate").isNotEmpty() &&
                            !prefs.getBoolean("setupOffered", false) &&
                            (!prefs.getBoolean("enabled", true) ||
                                !CareReminders.allowed(getApplication()))
                    ) {
                        prefs.edit { putBoolean("setupOffered", true) }
                        saved["reminderPrompt"] = true
                    }
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

    fun configureBackup(uri: Uri?) {
        if (uri == null) return
        backupAction(R.string.backup_saved) {
            val backups = CareBackups(getApplication())
            backups.configure(uri)
            backups.write()
        }
    }

    fun backupNow() = backupAction(R.string.backup_saved) { CareBackups(getApplication()).write() }

    fun disableBackup() = backupAction { CareBackups(getApplication()).disable() }

    private fun backupAction(success: Int = R.string.backup_updated, action: () -> Unit) =
        runAction {
            try {
                withContext(Dispatchers.IO) { action() }
                mutableMessage.value = success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = R.string.backup_error
            }
        }

    fun prepareRestore(uri: Uri?) {
        if (uri == null) return
        runAction {
            try {
                val preview =
                    withContext(Dispatchers.IO) {
                        val bytes =
                            requireNotNull(
                                    getApplication<Application>()
                                        .contentResolver
                                        .openInputStream(uri)
                                )
                                .use {
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = it.read(buffer)
                                        if (count < 0) break
                                        require(
                                            output.size() + count <= CareBackupDocument.MAX_BYTES
                                        )
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                        require(bytes.size <= CareBackupDocument.MAX_BYTES)
                        val text = bytes.toString(Charsets.UTF_8)
                        val backup = CareBackupDocument.parse(text)
                        val file =
                            java.io.File.createTempFile(
                                "restore-",
                                ".json",
                                getApplication<Application>().cacheDir,
                            )
                        try {
                            file.writeText(text)
                        } catch (error: Exception) {
                            file.delete()
                            throw error
                        }
                        file.absolutePath to
                            Bundle().apply {
                                putString("kind", "restore")
                                putInt("cats", backup.count("cats"))
                                putInt("tasks", backup.count("tasks"))
                                putInt("history", backup.count("history"))
                                putInt("comments", backup.count("observation_comments"))
                            }
                    }
                discardRestore()
                saved["restoreFile"] = preview.first
                saved["draft"] = preview.second
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = R.string.restore_error
            }
        }
    }

    fun restoreBackup() = runAction {
        try {
            withContext(Dispatchers.IO) {
                val file = java.io.File(requireNotNull(saved.get<String>("restoreFile")))
                store.restore(file.readText())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableError.value = R.string.restore_error
            return@runAction
        }
        // Replacement committed: close confirmation before refreshing to prevent resubmission.
        discardRestore()
        saved["draft"] = Bundle()
        saved["selected"] = ""
        mutableMessage.value = R.string.restore_success
        withContext(Dispatchers.IO) { reload() }
    }

    private fun discardRestore() {
        saved.get<String>("restoreFile")?.let { java.io.File(it).delete() }
        saved["restoreFile"] = null
    }

    override fun onCleared() {
        store.close()
    }
}
