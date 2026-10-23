package care.cat.offline;

import android.test.AndroidTestCase;
import java.util.*;
import org.json.*;

/** Runs against Android's real SQLite implementation, including close/reopen. */
public class CareStoreTest extends AndroidTestCase {
  private static final String DB = "care-test.db";
  private CareStore store;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getContext().deleteDatabase(DB);
    store = new CareStore(getContext(), DB);
  }

  @Override
  protected void tearDown() throws Exception {
    store.close();
    getContext().deleteDatabase(DB);
    super.tearDown();
  }

  private String cat(String name) {
    store.saveCat(null, name, "", "");
    for (CareStore.Cat cat : store.cats()) if (cat.name.equals(name)) return cat.id;
    throw new AssertionError();
  }

  public void testProfilesPersistAndRemainIsolated() {
    String mimi = cat("Mimi"), luna = cat("Luna");
    store.saveCat(mimi, "Mimi", "2020-01-01", "2020-02-01");
    int before = store.history(mimi).size();
    try {
      store.saveCat(mimi, "Mimi", "2020-02-01", "2020-01-01");
      fail("Invalid profile dates accepted");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(before, store.history(mimi).size());
    store.close();
    store = new CareStore(getContext(), DB);
    for (CareStore.Cat profile : store.cats()) {
      if (profile.id.equals(mimi)) assertEquals("2020-02-01", profile.adoption);
      if (profile.id.equals(luna)) assertEquals("", profile.adoption);
    }
  }

  public void testCompletionIsAtomicAndRetryDoesNotDuplicate() {
    String mimi = cat("Mimi"), luna = cat("Luna");
    store.saveTask(
        mimi, null, "Consulta", "Veterinário", "2027-01-31T09:00-03:00[America/Sao_Paulo]", 0, 1);
    CareStore.Task task = store.tasks(mimi).get(0);
    store.transition(task, "completed", "");
    int history = store.history(mimi).size();
    try {
      store.transition(task, "completed", "");
      fail("Duplicate completion accepted");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(2, store.tasks(mimi).size());
    assertEquals(history, store.history(mimi).size());
    assertEquals(0, store.tasks(luna).size());
    store.close();
    store = new CareStore(getContext(), DB);
    assertEquals(2, store.cats().size());
    assertEquals(2, store.tasks(mimi).size());
    assertTrue(store.tasks(mimi).get(0).due.contains("2027-02-28T09:00"));
  }

  public void testInvalidCreateAndStaleEditLeaveNoPartialHistory() {
    String mimi = cat("Mimi");
    int before = store.history(mimi).size();
    try {
      store.saveTask(mimi, null, "Teste", "Outro", "", -1, 0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(0, store.tasks(mimi).size());
    assertEquals(before, store.history(mimi).size());
    store.saveTask(mimi, null, "Teste", "Outro", "", 0, 0);
    CareStore.Task original = store.tasks(mimi).get(0);
    store.saveTask(mimi, original, "Atualizado", "Outro", "", 0, 0);
    before = store.history(mimi).size();
    try {
      store.saveTask(mimi, original, "Obsoleto", "Outro", "", 0, 0);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals("Atualizado", store.tasks(mimi).get(0).title);
    assertEquals(before, store.history(mimi).size());
  }

  public void testStorageFailureRollsBackCompletionAndSuccessorTogether() {
    String mimi = cat("Mimi");
    store.saveTask(mimi, null, "Consulta", "Veterinário", "2027-01-31T09:00Z", 0, 1);
    CareStore.Task task = store.tasks(mimi).get(0);
    int history = store.history(mimi).size();
    store
        .getWritableDatabase()
        .execSQL(
            "CREATE TRIGGER fail_successor_history BEFORE INSERT ON history WHEN NEW.type='created'"
                + " BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END");
    try {
      store.transition(task, "completed", "");
      fail("Expected SQLite write failure");
    } catch (android.database.sqlite.SQLiteException expected) {
    }
    assertEquals(1, store.tasks(mimi).size());
    assertEquals("planned", store.tasks(mimi).get(0).state);
    assertEquals(history, store.history(mimi).size());
    store.getWritableDatabase().execSQL("DROP TRIGGER fail_successor_history");
    store.transition(task, "completed", "");
    assertEquals(2, store.tasks(mimi).size());
  }

  public void testScopedExportAndCascadingDeletion() throws Exception {
    String mimi = cat("Mimi"), luna = cat("Luna");
    store.record(mimi, "measurement", "Peso 4,2 kg");
    store.record(mimi, "observation", "Comeu menos");
    store.record(luna, "feeding", "Ração");
    store.saveTask(mimi, null, "Água", "Outro", "", 0, 0);
    JSONObject export = new JSONObject(store.export(mimi));
    assertEquals(1, export.getJSONArray("cats").length());
    assertEquals(mimi, export.getJSONArray("cats").getJSONObject(0).getString("id"));
    JSONArray history = export.getJSONArray("history");
    for (int i = 0; i < history.length(); i++) {
      assertEquals(mimi, history.getJSONObject(i).getString("cat_id"));
      assertEquals(0, history.getJSONObject(i).getInt("is_diagnosis"));
    }
    assertEquals(2, new JSONObject(store.export(null)).getJSONArray("cats").length());
    store.delete(mimi);
    assertEquals(1, store.cats().size());
    assertEquals(0, store.tasks(mimi).size());
    assertEquals(0, store.history(mimi).size());
    assertEquals(2, store.history(luna).size());
    store.delete(null);
    store.close();
    store = new CareStore(getContext(), DB);
    assertEquals(0, store.cats().size());
    assertEquals(0, new JSONObject(store.export(null)).getJSONArray("history").length());
  }

  public void testDeferAndCancelKeepRecordsIndependent() {
    String mimi = cat("Mimi");
    store.saveTask(mimi, null, "Consulta", "Veterinário", "2090-01-01T09:00Z", 0, 0);
    CareStore.Task original = store.tasks(mimi).get(0);
    int before = store.history(mimi).size();
    try {
      store.transition(original, "deferred", "2090-01-01T09:00Z");
      fail();
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(before, store.history(mimi).size());
    store.transition(original, "deferred", "2090-01-02T09:00Z");
    store.record(mimi, "observation", "Observação");
    CareStore.Task deferred = store.tasks(mimi).get(0);
    assertEquals("planned", deferred.state);
    store.transition(deferred, "cancelled", "");
    assertEquals("cancelled", store.tasks(mimi).get(0).state);
  }
}
