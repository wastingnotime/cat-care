package care.cat.offline

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: CareStore

    @Before
    fun setup() {
        context.deleteDatabase(DB)
        store = CareStore(context, DB)
    }

    @After
    fun cleanup() {
        store.close()
        context.deleteDatabase(DB)
    }

    private fun cat(name: String) = store.saveCat(null, name, "", "", "gray")

    private fun reopen() {
        store.close()
        store = CareStore(context, DB)
    }

    private fun reject(action: () -> Unit) =
        assertThrows(IllegalArgumentException::class.java) { action() }

    @Test
    fun coatPersistsOnCreateEditReopenAndExport() {
        val mimi = store.saveCat(null, "Mimi", "2020-01-01", "2020-02-01", "calico")
        val luna = cat("Luna")
        store.saveCat(mimi, "Mimi", "2020-01-01", "2020-02-01", "tuxedo")
        reopen()
        store.cats().forEach { assertEquals(if (it.id == mimi) "tuxedo" else "gray", it.coat) }
        val exported = JSONObject(store.export(mimi)).getJSONArray("cats").getJSONObject(0)
        assertEquals("tuxedo", exported.getString("coat"))
        assertEquals("2020-01-01", exported.getString("birth"))
        val before = store.history(mimi).size
        reject { store.saveCat(mimi, "Mimi", "", "", "unknown-coat") }
        assertEquals(before, store.history(mimi).size)
        assertEquals(1, store.history(luna).size)
    }

    @Test
    fun cardSummarySeparatesTotalsPendingAndObservations() {
        val mimi = cat("Mimi")
        val luna = cat("Luna")
        store.saveTask(mimi, null, "Past", "Outro", "2020-01-01T09:00Z", 0, 0)
        store.saveTask(mimi, null, "Undated", "Outro", "", 0, 0)
        store.saveTask(mimi, null, "Future", "Outro", "2090-01-01T09:00Z", 0, 0)
        store.saveTask(mimi, null, "Done", "Outro", "", 0, 0)
        store.saveTask(mimi, null, "Cancelled", "Outro", "", 0, 0)
        store.tasks(mimi).forEach {
            if (it.title == "Done") store.transition(it, "completed", "")
            if (it.title == "Cancelled") store.transition(it, "cancelled", "")
        }
        store.record(mimi, "observation", "Appetite")
        store.record(mimi, "measurement", "4 kg")
        store.record(luna, "observation", "Playful")
        val now = Instant.parse("2026-10-23T12:00:00Z")
        assertEquals(CareStore.CatSummary(5, 1, 3, 1), store.summary(mimi, now))
        assertEquals(CareStore.CatSummary(0, 1, 0, 0), store.summary(luna, now))
    }

    @Test
    fun authorizedVersionOneResetCreatesCoatSchema() {
        store.close()
        context.deleteDatabase(DB)
        context.openOrCreateDatabase(DB, 0, null).use { old ->
            old.execSQL(
                "CREATE TABLE cats(id TEXT PRIMARY KEY, name TEXT, birth TEXT, adoption TEXT)"
            )
            old.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, cat_id TEXT REFERENCES cats(id))")
            old.execSQL(
                "CREATE TABLE history(id TEXT PRIMARY KEY, cat_id TEXT REFERENCES cats(id))"
            )
            old.execSQL("INSERT INTO cats VALUES('old-cat','Mimi','','')")
            old.execSQL("INSERT INTO tasks VALUES('old-task','old-cat')")
            old.execSQL("INSERT INTO history VALUES('old-event','old-cat')")
            old.version = 1
        }
        store = CareStore(context, DB)
        assertTrue(store.cats().isEmpty())
        assertEquals(5, store.readableDatabase.version)
        store.saveCat(null, "Luna", "", "", "siamese")
        assertEquals("siamese", store.cats().first().coat)
    }

    @Test
    fun commentsPersistScopeAndCascadeWithoutIncreasingObservations() {
        val mimi = cat("Mimi")
        val luna = cat("Luna")
        store.record(mimi, "observation", "Comeu menos")
        val observation = store.history(mimi).first().id
        store.comment(observation, "  Comeu depois  ")
        store.comment(observation, "Brincou à tarde")
        store.record(luna, "observation", "Dormiu")
        val other = store.history(luna).first().id
        store.comment(other, "Acordou")
        reopen()
        assertEquals(
            listOf("Comeu depois", "Brincou à tarde"),
            store.comments(observation).map { it.description },
        )
        assertEquals(1, store.summary(mimi, Instant.now()).observations)
        val exported = JSONObject(store.export(mimi))
        assertEquals(3, exported.getInt("version"))
        assertEquals(2, exported.getJSONArray("observation_comments").length())
        assertEquals(
            3,
            JSONObject(store.export(null)).getJSONArray("observation_comments").length(),
        )
        store.delete(mimi)
        assertTrue(store.comments(observation).isEmpty())
        assertEquals(1, store.comments(other).size)
        store.delete(null)
        assertTrue(store.comments(other).isEmpty())
    }

    @Test
    fun commentsAcceptAnyHistoryParentAndRejectBlankOrMissing() {
        val mimi = cat("Mimi")
        val profile = store.history(mimi).first().id
        store.record(mimi, "observation", "Brincou")
        val observation = store.history(mimi).first().id
        store.comment(profile, "Reply")
        assertEquals("Reply", store.comments(profile).single().description)
        reject { store.comment("missing", "Reply") }
        reject { store.comment(observation, "  ") }
        assertTrue(store.comments(observation).isEmpty())
    }

    @Test
    fun versionTwoMigrationPreservesProfilesTasksAndHistory() {
        val mimi = cat("Mimi")
        store.saveTask(mimi, null, "Água", "Outro", "", 0, 0)
        store.record(mimi, "observation", "Brincou")
        val observation = store.history(mimi).first().id
        store.writableDatabase.execSQL("DROP TABLE observation_comments")
        store.writableDatabase.version = 2
        reopen()
        assertEquals(1, store.cats().size)
        assertEquals(1, store.tasks(mimi).size)
        assertEquals(3, store.history(mimi).size)
        store.comment(observation, "Ainda brincando")
        assertEquals(1, store.comments(observation).size)
        assertEquals(5, store.readableDatabase.version)
    }

    @Test
    fun schemaFourMigratesUtcWithoutChangingInstantsAndKeepsRecurrenceZone() {
        val cat = cat("Mimi")
        store.saveTask(
            cat,
            null,
            "Daily",
            "Outro",
            "2026-03-07T09:00-05:00[America/New_York]",
            1,
            0,
        )
        val task = store.tasks(cat).single()
        val entry = store.history(cat).first { it.type == "created" }
        store.writableDatabase.execSQL(
            "UPDATE tasks SET due=?,zone='UTC' WHERE id=?",
            arrayOf("2026-03-07T09:00-05:00[America/New_York]", task.id),
        )
        store.writableDatabase.execSQL(
            "UPDATE history SET details=? WHERE id=?",
            arrayOf(
                JSONObject(entry.details!!)
                    .put("due", "2026-03-07T09:00-05:00[America/New_York]")
                    .toString(),
                entry.id,
            ),
        )
        store.writableDatabase.execSQL(
            "CREATE TABLE legacy_tasks AS SELECT id,cat_id,title,category,due,state,days,months,version FROM tasks"
        )
        store.writableDatabase.execSQL("DROP TABLE tasks")
        store.writableDatabase.execSQL("ALTER TABLE legacy_tasks RENAME TO tasks")
        store.writableDatabase.version = 4
        reopen()
        val migrated = store.tasks(cat).single()
        assertEquals("2026-03-07T14:00:00Z", migrated.due)
        assertEquals("America/New_York", migrated.zone)
        assertEquals(
            "2026-03-07T14:00:00Z",
            JSONObject(store.historyEntry(entry.id)!!.details!!).getString("due"),
        )
        store.transition(migrated, "completed", "")
        assertEquals("2026-03-08T13:00:00Z", store.tasks(cat).first().due)
        val export = JSONObject(store.export(cat))
        assertTrue(
            (0 until export.getJSONArray("tasks").length()).all {
                export.getJSONArray("tasks").getJSONObject(it).getString("due").endsWith("Z")
            }
        )
    }

    @Test
    fun schemaThreeUpgradePreservesLegacyRowsAndAddsLocalizedDetails() {
        store.close()
        context.deleteDatabase(DB)
        context.openOrCreateDatabase(DB, 0, null).use { db ->
            db.execSQL(
                "CREATE TABLE cats(id TEXT PRIMARY KEY, name TEXT NOT NULL, birth TEXT NOT NULL, adoption TEXT NOT NULL, coat TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE tasks(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE, title TEXT, category TEXT, due TEXT, state TEXT, days INTEGER, months INTEGER, version INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE history(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE, task_id TEXT, type TEXT NOT NULL, description TEXT NOT NULL, at TEXT NOT NULL, is_diagnosis INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE TABLE observation_comments(id TEXT PRIMARY KEY, observation_id TEXT NOT NULL REFERENCES history(id) ON DELETE CASCADE, description TEXT NOT NULL, at TEXT NOT NULL)"
            )
            db.execSQL("INSERT INTO cats VALUES('mimi','Mimi','','','calico')")
            db.execSQL(
                "INSERT INTO history VALUES('old-note','mimi',NULL,'observation','Nota original','2026-10-23T12:00:00Z',0)"
            )
            db.execSQL(
                "INSERT INTO observation_comments VALUES('old-comment','old-note','Comentário original','2026-10-23T12:01:00Z')"
            )
            db.version = 3
        }
        store = CareStore(context, DB)
        assertEquals("Nota original", store.history("mimi").single().description)
        assertNull(store.history("mimi").single().details)
        assertEquals("Comentário original", store.comments("old-note").single().description)
        store.saveTask(
            "mimi",
            null,
            "Water · every day",
            "Outro",
            "2026-10-24T09:30-03:00[America/Sao_Paulo]",
            1,
            0,
        )
        val details = JSONObject(store.history("mimi").first { it.type == "created" }.details!!)
        assertEquals("Water · every day", details.getString("title"))
        assertEquals(1, details.getInt("days"))
        assertEquals(5, store.readableDatabase.version)
        val export = JSONObject(store.export("mimi"))
        assertTrue(
            (0 until export.getJSONArray("history").length()).any {
                export.getJSONArray("history").getJSONObject(it).getString("id") == "old-note"
            }
        )
    }

    @Test
    fun profilesPersistAndRemainIsolated() {
        val mimi = cat("Mimi")
        val luna = cat("Luna")
        store.saveCat(mimi, "Mimi", "2020-01-01", "2020-02-01", "gray")
        val before = store.history(mimi).size
        reject { store.saveCat(mimi, "Mimi", "2020-02-01", "2020-01-01", "gray") }
        assertEquals(before, store.history(mimi).size)
        reopen()
        assertEquals("2020-02-01", store.cats().first { it.id == mimi }.adoption)
        assertEquals("", store.cats().first { it.id == luna }.adoption)
    }

    @Test
    fun completionIsAtomicAndRetryDoesNotDuplicate() {
        val mimi = cat("Mimi")
        val luna = cat("Luna")
        store.saveTask(
            mimi,
            null,
            "Consulta",
            "Veterinário",
            "2027-01-31T09:00-03:00[America/Sao_Paulo]",
            0,
            1,
        )
        val task = store.tasks(mimi).first()
        store.transition(task, "completed", "")
        val before = store.history(mimi).size
        reject { store.transition(task, "completed", "") }
        assertEquals(2, store.tasks(mimi).size)
        assertEquals(before, store.history(mimi).size)
        assertTrue(store.tasks(luna).isEmpty())
        reopen()
        assertEquals(2, store.cats().size)
        assertEquals(2, store.tasks(mimi).size)
        assertTrue(store.tasks(mimi).first().due.contains("2027-02-28T12:00"))
    }

    @Test
    fun invalidCreateAndStaleEditLeaveNoPartialHistory() {
        val mimi = cat("Mimi")
        var before = store.history(mimi).size
        reject { store.saveTask(mimi, null, "Teste", "Outro", "", -1, 0) }
        assertTrue(store.tasks(mimi).isEmpty())
        assertEquals(before, store.history(mimi).size)
        store.saveTask(mimi, null, "Teste", "Outro", "", 0, 0)
        val original = store.tasks(mimi).first()
        store.saveTask(mimi, original, "Atualizado", "Outro", "", 0, 0)
        before = store.history(mimi).size
        reject { store.saveTask(mimi, original, "Obsoleto", "Outro", "", 0, 0) }
        assertEquals("Atualizado", store.tasks(mimi).first().title)
        assertEquals(before, store.history(mimi).size)
    }

    @Test
    fun storageFailureRollsBackCompletionAndSuccessorTogether() {
        val mimi = cat("Mimi")
        store.saveTask(mimi, null, "Consulta", "Veterinário", "2027-01-31T09:00Z", 0, 1)
        val task = store.tasks(mimi).first()
        val before = store.history(mimi).size
        store.writableDatabase.execSQL(
            "CREATE TRIGGER fail_successor_history BEFORE INSERT ON history WHEN NEW.type='created' BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END"
        )
        assertThrows(SQLiteException::class.java) { store.transition(task, "completed", "") }
        assertEquals(1, store.tasks(mimi).size)
        assertEquals("planned", store.tasks(mimi).first().state)
        assertEquals(before, store.history(mimi).size)
        store.writableDatabase.execSQL("DROP TRIGGER fail_successor_history")
        store.transition(task, "completed", "")
        assertEquals(2, store.tasks(mimi).size)
    }

    @Test
    fun scopedExportAndCascadingDeletion() {
        val mimi = cat("Mimi")
        val luna = cat("Luna")
        store.record(mimi, "measurement", "Peso 4,2 kg")
        store.record(mimi, "observation", "Comeu menos")
        store.record(luna, "feeding", "Ração")
        store.saveTask(mimi, null, "Água", "Outro", "", 0, 0)
        val exported = JSONObject(store.export(mimi))
        val history = exported.getJSONArray("history")
        assertEquals(1, exported.getJSONArray("cats").length())
        assertEquals(mimi, exported.getJSONArray("cats").getJSONObject(0).getString("id"))
        for (i in 0 until history.length()) {
            assertEquals(mimi, history.getJSONObject(i).getString("cat_id"))
            assertEquals(0, history.getJSONObject(i).getInt("is_diagnosis"))
        }
        assertEquals(2, JSONObject(store.export(null)).getJSONArray("cats").length())
        store.delete(mimi)
        assertEquals(1, store.cats().size)
        assertTrue(store.tasks(mimi).isEmpty())
        assertTrue(store.history(mimi).isEmpty())
        assertEquals(2, store.history(luna).size)
        store.delete(null)
        reopen()
        assertTrue(store.cats().isEmpty())
        assertEquals(0, JSONObject(store.export(null)).getJSONArray("history").length())
    }

    @Test
    fun deferAndCancelKeepRecordsIndependent() {
        val mimi = cat("Mimi")
        store.saveTask(mimi, null, "Consulta", "Veterinário", "2090-01-01T09:00Z", 0, 0)
        val original = store.tasks(mimi).first()
        val before = store.history(mimi).size
        reject { store.transition(original, "deferred", "2090-01-01T09:00Z") }
        assertEquals(before, store.history(mimi).size)
        store.transition(original, "deferred", "2090-01-02T09:00Z")
        store.record(mimi, "observation", "Observação")
        val deferred = store.tasks(mimi).first()
        assertEquals("planned", deferred.state)
        store.transition(deferred, "cancelled", "")
        assertEquals("cancelled", store.tasks(mimi).first().state)
    }

    companion object {
        private const val DB = "care-test.db"
    }
}
