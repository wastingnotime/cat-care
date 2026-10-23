package care.cat.offline;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import org.json.*;

/** All mutations and their history commit together. No network adapter exists. */
public final class CareStore extends SQLiteOpenHelper {
  private static final DateTimeFormatter HISTORY_TIME =
      new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

  public static final class Cat {
    public final String id, name, birth, adoption, coat;

    Cat(Cursor c) {
      id = c.getString(0);
      name = c.getString(1);
      birth = c.getString(2);
      adoption = c.getString(3);
      coat = c.getString(4);
    }
  }

  public static final class Task {
    public final String id, catId, title, category, due, state;
    public final int days, months, version;

    Task(Cursor c) {
      id = c.getString(0);
      catId = c.getString(1);
      title = c.getString(2);
      category = c.getString(3);
      due = c.getString(4);
      state = c.getString(5);
      days = c.getInt(6);
      months = c.getInt(7);
      version = c.getInt(8);
    }
  }

  public static final class History {
    public final String type, description, at;

    History(Cursor c) {
      type = c.getString(0);
      description = c.getString(1);
      at = c.getString(2);
    }
  }

  private interface Mutation {
    void run(SQLiteDatabase db);
  }

  public CareStore(Context context) {
    this(context, "cat-care.db");
  }

  CareStore(Context context, String name) {
    super(context, name, null, 2);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE cats(id TEXT PRIMARY KEY, name TEXT NOT NULL, birth TEXT NOT NULL, adoption"
            + " TEXT NOT NULL, coat TEXT NOT NULL DEFAULT 'gray')");
    db.execSQL(
        "CREATE TABLE tasks(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE"
            + " CASCADE, title TEXT NOT NULL, category TEXT NOT NULL, due TEXT NOT NULL, state TEXT"
            + " NOT NULL CHECK(state IN ('planned','completed','cancelled')), days INTEGER NOT NULL"
            + " CHECK(days>=0), months INTEGER NOT NULL CHECK(months>=0), version INTEGER NOT NULL"
            + " DEFAULT 0, CHECK(days=0 OR months=0))");
    db.execSQL("CREATE INDEX task_cat ON tasks(cat_id)");
    db.execSQL(
        "CREATE TABLE history(id TEXT PRIMARY KEY, cat_id TEXT NOT NULL REFERENCES cats(id) ON"
            + " DELETE CASCADE, task_id TEXT, type TEXT NOT NULL, description TEXT NOT NULL, at"
            + " TEXT NOT NULL, is_diagnosis INTEGER NOT NULL DEFAULT 0 CHECK(is_diagnosis=0))");
    db.execSQL("CREATE INDEX history_cat ON history(cat_id,at)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Explicitly authorized development reset for the coat field. Future upgrades must migrate.
    if (oldVersion == 1 && newVersion == 2) {
      db.execSQL("DROP TABLE history");
      db.execSQL("DROP TABLE tasks");
      db.execSQL("DROP TABLE cats");
      onCreate(db);
      return;
    }
    throw new IllegalStateException("Versão de dados não suportada. Nenhum dado foi apagado.");
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private void transaction(Mutation change) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      change.run(db);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void event(SQLiteDatabase db, String cat, String task, String type, String text) {
    ContentValues v = new ContentValues();
    v.put("id", id());
    v.put("cat_id", cat);
    v.put("task_id", task);
    v.put("type", type);
    v.put("description", text);
    v.put("at", HISTORY_TIME.format(Instant.now()));
    db.insertOrThrow("history", null, v);
  }

  public List<Cat> cats() {
    List<Cat> rows = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,name,birth,adoption,coat FROM cats ORDER BY name COLLATE NOCASE,id",
                null)) {
      while (c.moveToNext()) rows.add(new Cat(c));
    }
    return rows;
  }

  private void requireCat(SQLiteDatabase db, String cat) {
    try (Cursor c = db.rawQuery("SELECT id FROM cats WHERE id=?", new String[] {cat})) {
      if (!c.moveToFirst())
        throw new IllegalArgumentException("Este gato não está mais disponível.");
    }
  }

  public String saveCat(String existing, String name, String birth, String adoption, String coat) {
    CareRules.profile(name, birth, adoption);
    CatCoat.fromId(coat);
    String cat = existing == null ? id() : existing;
    transaction(
        db -> {
          ContentValues v = new ContentValues();
          v.put("name", name.trim());
          v.put("birth", birth);
          v.put("adoption", adoption);
          v.put("coat", coat);
          if (existing == null) {
            v.put("id", cat);
            db.insertOrThrow("cats", null, v);
          } else if (db.update("cats", v, "id=?", new String[] {cat}) != 1)
            throw new IllegalArgumentException("Gato indisponível.");
          event(
              db,
              cat,
              null,
              "profile",
              existing == null
                  ? "Perfil criado: " + name.trim()
                  : "Perfil atualizado: " + name.trim());
        });
    return cat;
  }

  public static final class CatSummary {
    public int responsibilities, observations, pending, overdue;
  }

  public CatSummary summary(String cat, Instant now) {
    CatSummary summary = new CatSummary();
    for (Task task : tasks(cat)) {
      summary.responsibilities++;
      if (task.state.equals("planned")) {
        summary.pending++;
        if (!task.due.isEmpty() && ZonedDateTime.parse(task.due).toInstant().isBefore(now))
          summary.overdue++;
      }
    }
    for (History item : history(cat)) if (item.type.equals("observation")) summary.observations++;
    return summary;
  }

  public List<Task> tasks(String cat) {
    List<Task> rows = new ArrayList<>();
    String where = cat == null ? "" : " WHERE cat_id=?";
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,cat_id,title,category,due,state,days,months,version FROM tasks"
                    + where
                    + " ORDER BY id",
                cat == null ? null : new String[] {cat})) {
      while (c.moveToNext()) rows.add(new Task(c));
    }
    rows.sort(
        Comparator.comparing((Task t) -> !t.state.equals("planned"))
            .thenComparing(
                t -> t.due.isEmpty() ? Instant.MAX : ZonedDateTime.parse(t.due).toInstant())
            .thenComparing(t -> t.id));
    return rows;
  }

  public void saveTask(
      String cat, Task existing, String title, String category, String due, int days, int months) {
    String cleanTitle = CareRules.required(title, "o título");
    CareRules.required(category, "a categoria");
    CareRules.recurrence(days, months);
    if (!due.isEmpty()) ZonedDateTime.parse(due);
    CareRules.successor(due, days, months); // Validate before any write.
    if (existing != null && !existing.catId.equals(cat))
      throw new IllegalArgumentException("Gato incorreto.");
    transaction(
        db -> {
          requireCat(db, cat);
          ContentValues v = new ContentValues();
          v.put("title", cleanTitle);
          v.put("category", category);
          v.put("due", due);
          v.put("days", days);
          v.put("months", months);
          String task = existing == null ? id() : existing.id;
          if (existing == null) {
            v.put("id", task);
            v.put("cat_id", cat);
            v.put("state", "planned");
            db.insertOrThrow("tasks", null, v);
          } else {
            v.put("version", existing.version + 1);
            updatePlanned(db, existing, v);
          }
          event(
              db,
              cat,
              task,
              existing == null ? "created" : "edited",
              (existing == null ? "Cuidado criado: " : "Cuidado editado: ")
                  + cleanTitle
                  + " · "
                  + CareRules.displayDue(due)
                  + " · recorrência: "
                  + days
                  + " dias / "
                  + months
                  + " meses");
        });
  }

  private void updatePlanned(SQLiteDatabase db, Task task, ContentValues v) {
    if (db.update(
            "tasks",
            v,
            "id=? AND cat_id=? AND state='planned' AND version=?",
            new String[] {task.id, task.catId, "" + task.version})
        != 1)
      throw new IllegalArgumentException(
          "O cuidado mudou. Feche e abra novamente antes de tentar.");
  }

  public void transition(Task task, String action, String newDue) {
    if (!Arrays.asList("completed", "cancelled", "deferred").contains(action))
      throw new IllegalArgumentException("Ação inválida.");
    if (action.equals("deferred")) CareRules.defer(task.due, newDue, Instant.now());
    String next =
        action.equals("completed") ? CareRules.successor(task.due, task.days, task.months) : "";
    transaction(
        db -> {
          ContentValues v = new ContentValues();
          v.put("version", task.version + 1);
          if (action.equals("deferred")) v.put("due", newDue);
          else v.put("state", action);
          updatePlanned(db, task, v);
          String label =
              action.equals("completed")
                  ? "Concluído: "
                  : action.equals("cancelled") ? "Cancelado: " : "Adiado: ";
          event(
              db,
              task.catId,
              task.id,
              action,
              label
                  + task.title
                  + (action.equals("deferred")
                      ? " · "
                          + CareRules.displayDue(task.due)
                          + " → "
                          + CareRules.displayDue(newDue)
                      : ""));
          if (!next.isEmpty()) {
            String successor = id();
            ContentValues s = new ContentValues();
            s.put("id", successor);
            s.put("cat_id", task.catId);
            s.put("title", task.title);
            s.put("category", task.category);
            s.put("due", next);
            s.put("state", "planned");
            s.put("days", task.days);
            s.put("months", task.months);
            db.insertOrThrow("tasks", null, s);
            event(
                db,
                task.catId,
                successor,
                "created",
                "Próximo cuidado: " + task.title + " · " + CareRules.displayDue(next));
          }
        });
  }

  public void record(String cat, String type, String description) {
    if (!Arrays.asList("measurement", "feeding", "medication", "hygiene", "care", "observation")
        .contains(type)) throw new IllegalArgumentException("Tipo inválido.");
    String text = CareRules.required(description, "a descrição");
    transaction(
        db -> {
          requireCat(db, cat);
          event(db, cat, null, type, text);
        });
  }

  public List<History> history(String cat) {
    List<History> rows = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT type,description,at FROM history WHERE cat_id=? ORDER BY at DESC,id DESC",
                new String[] {cat})) {
      while (c.moveToNext()) rows.add(new History(c));
    }
    return rows;
  }

  public void delete(String cat) {
    transaction(
        db -> {
          if (cat == null) db.delete("cats", null, null);
          else if (db.delete("cats", "id=?", new String[] {cat}) != 1)
            throw new IllegalArgumentException("Gato indisponível.");
        });
  }

  /** Transaction provides one consistent snapshot, including all history and typed records. */
  public String export(String cat) {
    SQLiteDatabase db = getReadableDatabase();
    db.beginTransaction();
    try {
      if (cat != null) requireCat(db, cat);
      JSONObject root = new JSONObject();
      root.put("format", "cat-care-offline");
      root.put("version", 1);
      root.put("exported_at", Instant.now().toString());
      root.put("scope", cat == null ? "all_local_data" : "cat");
      for (String table : new String[] {"cats", "tasks", "history"}) {
        String key = table.equals("cats") ? "id" : "cat_id";
        JSONArray rows = new JSONArray();
        try (Cursor c =
            db.query(
                table,
                null,
                cat == null ? null : key + "=?",
                cat == null ? null : new String[] {cat},
                null,
                null,
                "id")) {
          while (c.moveToNext()) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < c.getColumnCount(); i++)
              row.put(
                  c.getColumnName(i),
                  c.isNull(i)
                      ? JSONObject.NULL
                      : c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
            rows.put(row);
          }
        }
        root.put(table, rows);
      }
      db.setTransactionSuccessful();
      return root.toString(2);
    } catch (JSONException e) {
      throw new IllegalStateException("Não foi possível gerar a exportação.", e);
    } finally {
      db.endTransaction();
    }
  }
}
