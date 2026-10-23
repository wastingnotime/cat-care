package care.cat.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Database name, schema, identifiers and transaction boundaries survive the UI migration. */
class CareStore(context: Context, name: String = "cat-care.db") :
    SQLiteOpenHelper(context, name, null, 4) {
    data class Cat(
        val id: String,
        val name: String,
        val birth: String,
        val adoption: String,
        val coat: String,
    )

    data class Task(
        val id: String,
        val catId: String,
        val title: String,
        val category: String,
        val due: String,
        val state: String,
        val days: Int,
        val months: Int,
        val version: Int,
    )

    data class History(
        val type: String,
        val description: String,
        val at: String,
        val id: String,
        val catId: String,
        val details: String?,
    )

    data class Comment(val description: String, val at: String)

    data class CatSummary(
        val responsibilities: Int,
        val observations: Int,
        val pending: Int,
        val overdue: Int,
    )

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cats(id TEXT PRIMARY KEY, name TEXT NOT NULL, birth TEXT NOT NULL, adoption TEXT NOT NULL, coat TEXT NOT NULL DEFAULT 'gray')"
        )
        db.execSQL(
            "CREATE TABLE tasks(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE, title TEXT NOT NULL, category TEXT NOT NULL, due TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('planned','completed','cancelled')), days INTEGER NOT NULL CHECK(days>=0), months INTEGER NOT NULL CHECK(months>=0), version INTEGER NOT NULL DEFAULT 0, CHECK(days=0 OR months=0))"
        )
        db.execSQL("CREATE INDEX task_cat ON tasks(cat_id)")
        db.execSQL(
            "CREATE TABLE history(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE, task_id TEXT, type TEXT NOT NULL, description TEXT NOT NULL, at TEXT NOT NULL, is_diagnosis INTEGER NOT NULL DEFAULT 0 CHECK(is_diagnosis=0), details TEXT)"
        )
        db.execSQL("CREATE INDEX history_cat ON history(cat_id,at)")
        createComments(db)
    }

    private fun createComments(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE observation_comments(id TEXT PRIMARY KEY, observation_id TEXT NOT NULL REFERENCES history(id) ON DELETE CASCADE, description TEXT NOT NULL, at TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX comment_observation ON observation_comments(observation_id,at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when {
            oldVersion == 1 && newVersion == 4 -> {
                // Retain the explicitly authorized legacy reset for coat selection.
                db.execSQL("DROP TABLE history")
                db.execSQL("DROP TABLE tasks")
                db.execSQL("DROP TABLE cats")
                onCreate(db)
            }
            oldVersion in 2..3 && newVersion == 4 -> {
                if (oldVersion == 2) createComments(db)
                val hasDetails =
                    db.rawQuery("PRAGMA table_info(history)", null).use { columns ->
                        var exists = false
                        while (columns.moveToNext()) if (columns.getString(1) == "details")
                            exists = true
                        exists
                    }
                if (!hasDetails) db.execSQL("ALTER TABLE history ADD COLUMN details TEXT")
            }
            else -> throw CareValidation(CareIssue.VERSION)
        }
    }

    private fun transaction(change: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            change(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun id() = UUID.randomUUID().toString()

    private fun timestamp() = HISTORY_TIME.format(Instant.now())

    private fun event(
        db: SQLiteDatabase,
        cat: String,
        task: String?,
        type: String,
        text: String,
        details: JSONObject? = null,
    ) {
        db.insertOrThrow(
            "history",
            null,
            ContentValues().apply {
                put("id", id())
                put("cat_id", cat)
                put("task_id", task)
                put("type", type)
                put("description", text)
                put("at", timestamp())
                put("details", details?.toString())
            },
        )
    }

    private fun <T> query(sql: String, args: Array<String>? = null, read: (Cursor) -> T): List<T> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(read(cursor)) }
        }

    fun cats(): List<Cat> =
        query("SELECT id,name,birth,adoption,coat FROM cats ORDER BY name COLLATE NOCASE,id") {
            Cat(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4))
        }

    private fun requireCat(db: SQLiteDatabase, cat: String) {
        db.rawQuery("SELECT id FROM cats WHERE id=?", arrayOf(cat)).use {
            careRequire(it.moveToFirst(), CareIssue.CAT_MISSING)
        }
    }

    fun saveCat(
        existing: String?,
        name: String,
        birth: String,
        adoption: String,
        coat: String,
    ): String {
        CareRules.profile(name, birth, adoption)
        CatCoat.fromId(coat)
        val cat = existing ?: id()
        transaction { db ->
            val values =
                ContentValues().apply {
                    put("name", name.trim())
                    put("birth", birth)
                    put("adoption", adoption)
                    put("coat", coat)
                }
            if (existing == null) {
                values.put("id", cat)
                db.insertOrThrow("cats", null, values)
            } else
                careRequire(
                    db.update("cats", values, "id=?", arrayOf(cat)) == 1,
                    CareIssue.CAT_MISSING,
                )
            event(
                db,
                cat,
                null,
                "profile",
                (if (existing == null) "Perfil criado: " else "Perfil atualizado: ") + name.trim(),
                JSONObject()
                    .put("event", if (existing == null) "profile_created" else "profile_edited")
                    .put("name", name.trim()),
            )
        }
        return cat
    }

    fun summary(cat: String, now: Instant): CatSummary {
        val tasks = tasks(cat)
        val pending = tasks.filter { it.state == "planned" }
        return CatSummary(
            tasks.size,
            history(cat).count { it.type == "observation" },
            pending.size,
            pending.count {
                it.due.isNotEmpty() && ZonedDateTime.parse(it.due).toInstant().isBefore(now)
            },
        )
    }

    fun tasks(cat: String?): List<Task> =
        query(
                "SELECT id,cat_id,title,category,due,state,days,months,version FROM tasks" +
                    (if (cat == null) "" else " WHERE cat_id=?") +
                    " ORDER BY id",
                cat?.let { arrayOf(it) },
            ) {
                Task(
                    it.getString(0),
                    it.getString(1),
                    it.getString(2),
                    it.getString(3),
                    it.getString(4),
                    it.getString(5),
                    it.getInt(6),
                    it.getInt(7),
                    it.getInt(8),
                )
            }
            .sortedWith(
                compareBy<Task> { it.state != "planned" }
                    .thenBy {
                        if (it.due.isEmpty()) Instant.MAX
                        else ZonedDateTime.parse(it.due).toInstant()
                    }
                    .thenBy { it.id }
            )

    fun saveTask(
        cat: String,
        existing: Task?,
        title: String,
        category: String,
        due: String,
        days: Int,
        months: Int,
    ) {
        val cleanTitle = CareRules.required(title, CareIssue.TITLE)
        CareRules.required(category, CareIssue.CATEGORY)
        CareRules.recurrence(days, months)
        if (due.isNotEmpty()) ZonedDateTime.parse(due)
        CareRules.successor(due, days, months)
        careRequire(existing == null || existing.catId == cat, CareIssue.CAT_INCORRECT)
        transaction { db ->
            requireCat(db, cat)
            val values =
                ContentValues().apply {
                    put("title", cleanTitle)
                    put("category", category)
                    put("due", due)
                    put("days", days)
                    put("months", months)
                }
            val task = existing?.id ?: id()
            if (existing == null) {
                values.put("id", task)
                values.put("cat_id", cat)
                values.put("state", "planned")
                db.insertOrThrow("tasks", null, values)
            } else {
                values.put("version", existing.version + 1)
                updatePlanned(db, existing, values)
            }
            event(
                db,
                cat,
                task,
                if (existing == null) "created" else "edited",
                (if (existing == null) "Cuidado criado: " else "Cuidado editado: ") +
                    cleanTitle +
                    " · ${CareRules.displayDue(due)} · recorrência: $days dias / $months meses",
                JSONObject()
                    .put("event", if (existing == null) "task_created" else "task_edited")
                    .put("title", cleanTitle)
                    .put("due", due)
                    .put("days", days)
                    .put("months", months),
            )
        }
    }

    private fun updatePlanned(db: SQLiteDatabase, task: Task, values: ContentValues) {
        careRequire(
            db.update(
                "tasks",
                values,
                "id=? AND cat_id=? AND state='planned' AND version=?",
                arrayOf(task.id, task.catId, task.version.toString()),
            ) == 1,
            CareIssue.STALE,
        )
    }

    fun transition(task: Task, action: String, newDue: String) {
        careRequire(action in listOf("completed", "cancelled", "deferred"), CareIssue.ACTION)
        if (action == "deferred") CareRules.defer(task.due, newDue, Instant.now())
        val next =
            if (action == "completed") CareRules.successor(task.due, task.days, task.months) else ""
        transaction { db ->
            val values =
                ContentValues().apply {
                    put("version", task.version + 1)
                    if (action == "deferred") put("due", newDue) else put("state", action)
                }
            updatePlanned(db, task, values)
            val label =
                when (action) {
                    "completed" -> "Concluído: "
                    "cancelled" -> "Cancelado: "
                    else -> "Adiado: "
                }
            event(
                db,
                task.catId,
                task.id,
                action,
                label +
                    task.title +
                    if (action == "deferred")
                        " · ${CareRules.displayDue(task.due)} → ${CareRules.displayDue(newDue)}"
                    else "",
                JSONObject()
                    .put("event", "task_$action")
                    .put("title", task.title)
                    .put("from_due", task.due)
                    .put("due", if (action == "deferred") newDue else task.due),
            )
            if (next.isNotEmpty()) {
                val successor = id()
                db.insertOrThrow(
                    "tasks",
                    null,
                    ContentValues().apply {
                        put("id", successor)
                        put("cat_id", task.catId)
                        put("title", task.title)
                        put("category", task.category)
                        put("due", next)
                        put("state", "planned")
                        put("days", task.days)
                        put("months", task.months)
                    },
                )
                event(
                    db,
                    task.catId,
                    successor,
                    "created",
                    "Próximo cuidado: ${task.title} · ${CareRules.displayDue(next)}",
                    JSONObject()
                        .put("event", "task_next")
                        .put("title", task.title)
                        .put("due", next)
                        .put("days", task.days)
                        .put("months", task.months),
                )
            }
        }
    }

    fun record(cat: String, type: String, description: String) {
        careRequire(type in RecordTypes, CareIssue.TYPE)
        val text = CareRules.required(description, CareIssue.DESCRIPTION)
        transaction { db ->
            requireCat(db, cat)
            event(db, cat, null, type, text)
        }
    }

    private fun readHistory(c: Cursor) =
        History(
            c.getString(0),
            c.getString(1),
            c.getString(2),
            c.getString(3),
            c.getString(4),
            c.getString(5),
        )

    fun history(cat: String): List<History> =
        query(
            "SELECT type,description,at,id,cat_id,details FROM history WHERE cat_id=? ORDER BY at DESC,id DESC",
            arrayOf(cat),
            ::readHistory,
        )

    fun historyEntry(id: String): History? =
        query(
                "SELECT type,description,at,id,cat_id,details FROM history WHERE id=?",
                arrayOf(id),
                ::readHistory,
            )
            .firstOrNull()

    fun comments(observation: String): List<Comment> =
        query(
            "SELECT description,at FROM observation_comments WHERE observation_id=? ORDER BY at,id",
            arrayOf(observation),
        ) {
            Comment(it.getString(0), it.getString(1))
        }

    fun comment(observation: String, description: String) {
        val clean = CareRules.required(description, CareIssue.COMMENT)
        transaction { db ->
            careRequire(historyEntry(observation)?.type == "observation", CareIssue.OBSERVATION)
            db.insertOrThrow(
                "observation_comments",
                null,
                ContentValues().apply {
                    put("id", id())
                    put("observation_id", observation)
                    put("description", clean)
                    put("at", timestamp())
                },
            )
        }
    }

    fun delete(cat: String?) {
        transaction { db ->
            if (cat == null) db.delete("cats", null, null)
            else careRequire(db.delete("cats", "id=?", arrayOf(cat)) == 1, CareIssue.CAT_MISSING)
        }
    }

    /** One consistent snapshot, including all history, typed records and observation comments. */
    fun export(cat: String?): String {
        val db = readableDatabase
        db.beginTransaction()
        try {
            if (cat != null) requireCat(db, cat)
            val root =
                JSONObject()
                    .put("format", "cat-care-offline")
                    .put("version", 2)
                    .put("exported_at", Instant.now().toString())
                    .put("scope", if (cat == null) "all_local_data" else "cat")
            for (table in listOf("cats", "tasks", "history", "observation_comments")) {
                val selection =
                    when (table) {
                        "cats" -> "id=?"
                        "observation_comments" ->
                            "observation_id IN (SELECT id FROM history WHERE cat_id=?)"
                        else -> "cat_id=?"
                    }
                val rows = JSONArray()
                db.query(
                        table,
                        null,
                        if (cat == null) null else selection,
                        cat?.let { arrayOf(it) },
                        null,
                        null,
                        "id",
                    )
                    .use { c ->
                        while (c.moveToNext()) {
                            val row = JSONObject()
                            for (i in 0 until c.columnCount) row.put(
                                c.getColumnName(i),
                                when {
                                    c.isNull(i) -> JSONObject.NULL
                                    c.getType(i) == Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                    else -> c.getString(i)
                                },
                            )
                            rows.put(row)
                        }
                    }
                root.put(table, rows)
            }
            db.setTransactionSuccessful()
            return root.toString(2)
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private val HISTORY_TIME = DateTimeFormatterBuilder().appendInstant(9).toFormatter()
    }
}
