package care.cat.offline;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
  private static final int GREEN = 0xff276653,
      INK = 0xff243c33,
      MUTED = 0xff53675e,
      PAPER = 0xfff7f6f0;
  private static final int EXPORT = 41;
  private CareStore store;
  private LinearLayout page, content;
  private String screen = "today", selected, exportCat;
  private boolean exportingAll;
  private final Map<String, CareStore.Cat> cats = new LinkedHashMap<>();
  private final List<EditText> draftFields = new ArrayList<>();
  private String draftKind, draftTarget;
  private Spinner draftSpinner;
  private Bundle restoredDraft;
  private final String[] categories = {
    "Alimentação", "Higiene", "Medicação", "Veterinário", "Outro"
  };
  private final String[] types = {
    "Medição", "Alimentação", "Medicação", "Higiene", "Cuidado", "Observação (não é diagnóstico)"
  };
  private final String[] typeKeys = {
    "measurement", "feeding", "medication", "hygiene", "care", "observation"
  };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (Build.VERSION.SDK_INT >= 27)
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    else getWindow().setNavigationBarColor(GREEN);
    store = new CareStore(this);
    selected = getPreferences(0).getString("cat", null);
    if (state != null) {
      screen = state.getString("screen", "today");
      selected = state.getString("selected");
      exportCat = state.getString("exportCat");
      exportingAll = state.getBoolean("exportingAll");
      restoredDraft = state.getBundle("draft");
    }
    render();
    if (restoredDraft != null) restoreDraft();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("screen", screen);
    out.putString("selected", selected);
    out.putString("exportCat", exportCat);
    out.putBoolean("exportingAll", exportingAll);
    if (draftKind != null) {
      Bundle draft = new Bundle();
      draft.putString("kind", draftKind);
      draft.putString("target", draftTarget);
      ArrayList<String> values = new ArrayList<>();
      for (EditText field : draftFields) values.add(field.getText().toString());
      draft.putStringArrayList("values", values);
      if (draftSpinner != null) draft.putInt("choice", draftSpinner.getSelectedItemPosition());
      out.putBundle("draft", draft);
    }
  }

  private void restoreDraft() {
    Bundle draft = restoredDraft;
    restoredDraft = null;
    String kind = draft.getString("kind"), target = draft.getString("target");
    CareStore.Cat cat = target == null ? null : cats.get(target);
    if ("cat".equals(kind)) catForm(cat);
    else if ("record".equals(kind) && cats.containsKey(selected)) recordForm();
    else if ("task".equals(kind) && cats.containsKey(selected)) {
      CareStore.Task task = null;
      for (CareStore.Task candidate : store.tasks(null))
        if (candidate.id.equals(target)) task = candidate;
      if (target == null || task != null) taskForm(task);
    } else if ("defer".equals(kind) && cats.containsKey(selected)) {
      for (CareStore.Task task : store.tasks(null)) if (task.id.equals(target)) deferForm(task);
    }
    ArrayList<String> values = draft.getStringArrayList("values");
    if (values != null)
      for (int i = 0; i < Math.min(values.size(), draftFields.size()); i++)
        draftFields.get(i).setText(values.get(i));
    if (draftSpinner != null) draftSpinner.setSelection(draft.getInt("choice", 0));
  }

  private int dp(float n) {
    return (int) (getResources().getDisplayMetrics().density * n + .5f);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private GradientDrawable background(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    if (bold) t.setTypeface(null, Typeface.BOLD);
    parent.addView(t);
    return t;
  }

  private void heading(LinearLayout parent, String value) {
    TextView t = text(parent, value, 23, INK, true);
    if (Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
  }

  private Button button(LinearLayout parent, String value, Runnable action) {
    Button b = new Button(this);
    b.setText(value);
    b.setAllCaps(false);
    b.setTextColor(GREEN);
    b.setMinHeight(dp(48));
    b.setOnClickListener(v -> action.run());
    parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
    return b;
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    c.setBackground(background(Color.WHITE, 20));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(10), 0, dp(4));
    parent.addView(c, lp);
    return c;
  }

  private void error(Exception e) {
    String message =
        e instanceof IllegalArgumentException
            ? e.getMessage()
            : "Não foi possível acessar ou salvar os dados. Tente novamente. Nenhum sucesso foi"
                + " confirmado.";
    new AlertDialog.Builder(this)
        .setTitle("Confira os dados")
        .setMessage(message)
        .setPositiveButton("Entendi", null)
        .show();
  }

  private void change(Runnable work) {
    try {
      work.run();
      render();
    } catch (Exception e) {
      error(e);
    }
  }

  private void render() {
    try {
      renderContent();
    } catch (Exception e) {
      LinearLayout fallback = column();
      fallback.setPadding(dp(24), dp(32), dp(24), dp(24));
      setContentView(fallback);
      heading(fallback, "Seus dados precisam de atenção");
      text(
          fallback,
          "Não foi possível abrir os dados locais. Eles não foram redefinidos.",
          16,
          INK,
          false);
      button(fallback, "Tentar novamente", this::render);
    }
  }

  private void renderContent() {
    cats.clear();
    for (CareStore.Cat cat : store.cats()) cats.put(cat.id, cat);
    if (!cats.containsKey(selected))
      selected = cats.isEmpty() ? null : cats.keySet().iterator().next();
    getPreferences(0).edit().putString("cat", selected).apply();
    page = column();
    page.setBackgroundColor(PAPER);
    setContentView(page);
    page.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    page.requestApplyInsets();
    LinearLayout brand = column();
    brand.setPadding(dp(22), dp(10), dp(22), dp(6));
    page.addView(brand);
    text(brand, "CAT CARE  /  NO SEU RITMO", 13, GREEN, true);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content = column();
    content.setPadding(dp(22), dp(8), dp(22), dp(24));
    scroll.addView(content);
    if (screen.equals("today")) today();
    else if (screen.equals("cats")) catList();
    else if (screen.equals("history")) history();
    else data();
    LinearLayout nav = new LinearLayout(this);
    nav.setPadding(dp(4), dp(4), dp(4), dp(4));
    nav.setBackgroundColor(Color.WHITE);
    page.addView(nav);
    String[] labels = {"Hoje", "Gatos", "Histórico", "Dados"},
        routes = {"today", "cats", "history", "data"};
    for (int i = 0; i < labels.length; i++) {
      String route = routes[i];
      Button b = new Button(this);
      b.setText(labels[i]);
      b.setAllCaps(false);
      b.setTextSize(13);
      b.setMinWidth(0);
      b.setMinimumWidth(0);
      b.setPadding(0, 0, 0, 0);
      b.setTextColor(screen.equals(route) ? GREEN : MUTED);
      b.setTypeface(null, screen.equals(route) ? Typeface.BOLD : Typeface.NORMAL);
      b.setContentDescription(labels[i] + (screen.equals(route) ? ", aba selecionada" : ""));
      b.setOnClickListener(
          v -> {
            screen = route;
            render();
          });
      b.setMinHeight(dp(56));
      nav.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void empty() {
    LinearLayout c = card(content);
    heading(c, "Uma rotina mais tranquila");
    text(
        c,
        "Comece pelo perfil do seu gato. Os cuidados ficam salvos neste aparelho, mesmo sem"
            + " internet.",
        17,
        MUTED,
        false);
    button(c, "Adicionar meu primeiro gato", () -> catForm(null));
  }

  private void selectCat() {
    if (cats.isEmpty()) return;
    Spinner spinner = new Spinner(this);
    List<CareStore.Cat> list = new ArrayList<>(cats.values());
    List<String> names = new ArrayList<>();
    int position = 0;
    for (int i = 0; i < list.size(); i++) {
      names.add(list.get(i).name);
      if (list.get(i).id.equals(selected)) position = i;
    }
    text(content, "Gato selecionado", 14, MUTED, true);
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    spinner.setSelection(position);
    spinner.setContentDescription("Selecionar gato");
    spinner.setMinimumHeight(dp(56));
    content.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
    spinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
            String next = list.get(pos).id;
            if (!next.equals(selected)) {
              selected = next;
              render();
            }
          }
        });
  }

  private void today() {
    heading(content, "Pequenos cuidados, todos os dias.");
    text(
        content,
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", new Locale("pt", "BR"))),
        15,
        MUTED,
        false);
    if (cats.isEmpty()) {
      empty();
      return;
    }
    List<CareStore.Task> planned = new ArrayList<>();
    for (CareStore.Task task : store.tasks(null))
      if (task.state.equals("planned")) planned.add(task);
    text(
        content,
        planned.isEmpty()
            ? "Tudo em dia por aqui."
            : (planned.size() == 1
                    ? "1 cuidado planejado"
                    : planned.size() + " cuidados planejados")
                + " · todos os gatos",
        17,
        GREEN,
        true);
    text(
        content,
        "Os prazos ajudam a organizar a rotina. Não indicam urgência clínica.",
        14,
        MUTED,
        false);
    selectCat();
    button(content, "+ Planejar cuidado para " + cats.get(selected).name, () -> taskForm(null));
    button(content, "+ Registrar cuidado ou observação", this::recordForm);
    for (CareStore.Task task : planned) taskCard(task);
    if (planned.isEmpty())
      text(content, "Adicione o próximo cuidado quando quiser.", 16, MUTED, false);
  }

  private void taskCard(CareStore.Task task) {
    LinearLayout c = card(content);
    CareStore.Cat cat = cats.get(task.catId);
    text(c, cat.name + " · " + task.category, 14, GREEN, true);
    heading(c, task.title);
    text(c, CareRules.status(task.state, task.due, Instant.now()), 16, INK, true);
    text(c, CareRules.displayDue(task.due), 14, MUTED, false);
    if (task.days > 0 || task.months > 0)
      text(
          c,
          "Repete a cada "
              + (task.months > 0
                  ? task.months + (task.months == 1 ? " mês" : " meses")
                  : task.days + (task.days == 1 ? " dia" : " dias")),
          14,
          MUTED,
          false);
    if (task.state.equals("planned")) {
      button(
          c,
          "Ver ações · " + task.title,
          () ->
              new AlertDialog.Builder(this)
                  .setTitle(cat.name + " · " + task.title)
                  .setItems(
                      new String[] {"Concluir", "Editar", "Adiar", "Cancelar cuidado"},
                      (dialog, index) -> {
                        if (index == 0)
                          confirm(
                              "Concluir cuidado?",
                              cat.name
                                  + " · "
                                  + task.title
                                  + (task.days > 0 || task.months > 0
                                      ? "\nUm próximo cuidado será criado pela recorrência."
                                      : ""),
                              "Concluir",
                              () -> store.transition(task, "completed", ""));
                        if (index == 1) taskForm(task);
                        if (index == 2) deferForm(task);
                        if (index == 3)
                          confirm(
                              "Cancelar cuidado?",
                              cat.name + " · " + task.title + "\nO histórico será mantido.",
                              "Cancelar cuidado",
                              () -> store.transition(task, "cancelled", ""));
                      })
                  .setNegativeButton("Voltar", null)
                  .show());
    }
  }

  private void catList() {
    heading(content, "Seus gatos");
    text(content, "Cada história tem seu próprio espaço.", 16, MUTED, false);
    button(content, "+ Adicionar gato", () -> catForm(null));
    if (cats.isEmpty()) {
      empty();
      return;
    }
    selectCat();
    CareStore.Cat cat = cats.get(selected);
    LinearLayout c = card(content);
    heading(c, cat.name);
    text(
        c,
        "Nascimento: " + (cat.birth.isEmpty() ? "não informado" : CareRules.displayDate(cat.birth)),
        16,
        MUTED,
        false);
    text(
        c,
        "Adoção: "
            + (cat.adoption.isEmpty() ? "não informada" : CareRules.displayDate(cat.adoption)),
        16,
        MUTED,
        false);
    button(c, "Editar perfil de " + cat.name, () -> catForm(cat));
    button(c, "Registrar cuidado de " + cat.name, this::recordForm);
    button(c, "Planejar cuidado de " + cat.name, () -> taskForm(null));
    heading(content, "Cuidados de " + cat.name);
    List<CareStore.Task> tasks = store.tasks(selected);
    for (CareStore.Task task : tasks) taskCard(task);
    if (tasks.isEmpty()) text(content, "Nenhum cuidado planejado ainda.", 16, MUTED, false);
  }

  private String typeLabel(String type) {
    for (int i = 0; i < typeKeys.length; i++) if (typeKeys[i].equals(type)) return types[i];
    switch (type) {
      case "created":
        return "Planejamento";
      case "edited":
        return "Edição";
      case "completed":
        return "Conclusão";
      case "cancelled":
        return "Cancelamento";
      case "deferred":
        return "Adiamento";
      default:
        return "Perfil";
    }
  }

  private void history() {
    heading(content, "Uma história de cuidado");
    if (cats.isEmpty()) {
      empty();
      return;
    }
    selectCat();
    button(content, "+ Registrar cuidado ou observação", this::recordForm);
    for (CareStore.History item : store.history(selected)) {
      LinearLayout c = card(content);
      text(c, cats.get(selected).name + " · " + typeLabel(item.type), 15, GREEN, true);
      text(c, item.description, 17, INK, false);
      text(
          c,
          Instant.parse(item.at).atZone(ZoneId.systemDefault()).format(CareRules.INPUT),
          14,
          MUTED,
          false);
    }
  }

  private void data() {
    heading(content, "Seus dados, neste aparelho");
    text(
        content,
        "Funciona sem internet e sem conta. Não há sincronização nem lembretes automáticos nesta"
            + " versão.",
        16,
        MUTED,
        false);
    LinearLayout info = card(content);
    text(info, "Guarde uma cópia quando precisar", 18, INK, true);
    text(
        info,
        "A exportação cria um arquivo JSON para consulta. Esta versão não importa nem restaura"
            + " arquivos. Desinstalar, limpar os dados ou perder o aparelho pode apagar seus"
            + " registros. O backup e a transferência automáticos estão desativados para os dados"
            + " do app; alguns fabricantes podem aplicar regras próprias.",
        15,
        MUTED,
        false);
    text(
        info,
        "Arquivos exportados ficam no destino escolhido e não são apagados pelo app. Se escolher um"
            + " provedor de nuvem, ele pode exigir internet. Use uma pasta local para exportar"
            + " offline.",
        15,
        MUTED,
        false);
    if (cats.isEmpty()) {
      text(content, "Nenhum dado de gato salvo.", 16, MUTED, false);
      return;
    }
    selectCat();
    CareStore.Cat cat = cats.get(selected);
    button(content, "Exportar " + cat.name, () -> previewExport(cat.id));
    button(content, "Exportar todos os gatos", () -> previewExport(null));
    button(
        content,
        "Apagar dados de " + cat.name,
        () ->
            confirm(
                "Apagar " + cat.name + "?",
                "Perfil, cuidados, registros e histórico deste gato serão removidos deste app. Os"
                    + " outros gatos serão preservados. Arquivos já exportados não serão apagados.",
                "Apagar gato",
                () -> {
                  store.delete(cat.id);
                  toast("Dados de " + cat.name + " apagados deste app.");
                }));
    button(
        content,
        "Apagar todos os dados locais",
        () ->
            confirm(
                "Apagar todos os dados locais?",
                "Gatos: "
                    + names()
                    + ".\n"
                    + "Todos os perfis, cuidados, registros e históricos serão removidos deste app."
                    + " Não há recuperação pelo app. Arquivos já exportados não serão apagados.",
                "Apagar tudo",
                () -> {
                  store.delete(null);
                  selected = null;
                  getPreferences(0).edit().clear().apply();
                  toast("Todos os dados de cuidado foram apagados deste app.");
                }));
  }

  private String names() {
    List<String> names = new ArrayList<>();
    for (CareStore.Cat cat : cats.values()) names.add(cat.name);
    return String.join(", ", names);
  }

  private void previewExport(String cat) {
    new AlertDialog.Builder(this)
        .setTitle("Exportar dados locais?")
        .setMessage(
            "Gatos: "
                + (cat == null ? names() : cats.get(cat).name)
                + ".\n"
                + "Inclui perfis, cuidados, registros e histórico apenas deste escopo, em JSON. O"
                + " arquivo pode conter informações pessoais. Não há restauração pelo app nesta"
                + " versão.")
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Escolher destino",
            (d, w) -> {
              exportCat = cat;
              exportingAll = cat == null;
              Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
              intent.addCategory(Intent.CATEGORY_OPENABLE);
              intent.setType("application/json");
              intent.putExtra(Intent.EXTRA_TITLE, "cat-care-" + LocalDate.now() + ".json");
              try {
                startActivityForResult(intent, EXPORT);
              } catch (ActivityNotFoundException e) {
                error(
                    new IllegalArgumentException(
                        "Nenhum seletor de arquivos disponível neste aparelho."));
              }
            })
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != EXPORT || result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (!exportingAll && exportCat == null) {
      error(new IllegalArgumentException("Selecione novamente o escopo da exportação."));
      return;
    }
    String scope = exportingAll ? null : exportCat;
    exportCat = null;
    exportingAll = false;
    toast("Salvando exportação…");
    // A slow document provider must not block care interactions or activity recreation.
    new Thread(
            () -> {
              try (CareStore snapshot = new CareStore(getApplicationContext())) {
                byte[] bytes = snapshot.export(scope).getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                  if (output == null) throw new IOException("Destino indisponível");
                  output.write(bytes);
                  output.flush();
                }
                runOnUiThread(
                    () ->
                        Toast.makeText(
                                getApplicationContext(),
                                "Exportação salva no destino escolhido.",
                                Toast.LENGTH_LONG)
                            .show());
              } catch (Exception e) {
                runOnUiThread(
                    () -> {
                      String message =
                          "Não foi possível salvar o arquivo completo. Remova qualquer arquivo"
                              + " parcial no destino e tente novamente.";
                      if (isDestroyed() || isFinishing())
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                      else
                        new AlertDialog.Builder(this)
                            .setTitle("Exportação não concluída")
                            .setMessage(message)
                            .setPositiveButton("Entendi", null)
                            .show();
                    });
              }
            },
            "cat-care-export")
        .start();
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }

  private void confirm(String title, String message, String label, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Voltar", null)
        .setPositiveButton(label, (d, w) -> change(action))
        .show();
  }

  private LinearLayout form(String kind, String target) {
    draftFields.clear();
    draftKind = kind;
    draftTarget = target;
    draftSpinner = null;
    LinearLayout f = column();
    f.setPadding(dp(24), dp(6), dp(24), dp(12));
    return f;
  }

  private EditText field(LinearLayout form, String label, String value, int inputType) {
    TextView l = text(form, label, 14, INK, true);
    EditText edit = new EditText(this);
    edit.setId(View.generateViewId());
    l.setLabelFor(edit.getId());
    edit.setText(value);
    edit.setTextSize(16);
    edit.setInputType(inputType);
    edit.setMinHeight(dp(48));
    form.addView(edit, new LinearLayout.LayoutParams(-1, -2));
    draftFields.add(edit);
    return edit;
  }

  private Spinner choice(LinearLayout form, String label, String[] options, int selectedIndex) {
    TextView l = text(form, label, 14, INK, true);
    Spinner spinner = new Spinner(this);
    spinner.setId(View.generateViewId());
    l.setLabelFor(spinner.getId());
    spinner.setContentDescription(label);
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
    spinner.setSelection(selectedIndex);
    spinner.setMinimumHeight(dp(56));
    form.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
    draftSpinner = spinner;
    return spinner;
  }

  private String value(EditText field) {
    return field.getText().toString().trim();
  }

  private void dialog(String title, LinearLayout form, Runnable save) {
    ScrollView scroll = new ScrollView(this);
    scroll.addView(form);
    TextView validation = text(form, "", 15, 0xffa53528, false);
    validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("Voltar", null)
            .setPositiveButton("Salvar", null)
            .create();
    d.setOnDismissListener(
        ignored -> {
          draftKind = null;
          draftTarget = null;
          draftFields.clear();
          draftSpinner = null;
        });
    d.setOnShowListener(
        ignored ->
            d.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        save.run();
                        d.dismiss();
                        render();
                      } catch (Exception e) {
                        validation.setText(
                            e instanceof IllegalArgumentException
                                ? e.getMessage()
                                : "Não foi possível salvar. Tente novamente.");
                        validation.announceForAccessibility(validation.getText());
                        scroll.post(() -> scroll.smoothScrollTo(0, form.getHeight()));
                      }
                    }));
    d.show();
  }

  private void catForm(CareStore.Cat cat) {
    LinearLayout f = form("cat", cat == null ? null : cat.id);
    EditText name =
        field(
            f,
            "Nome",
            cat == null ? "" : cat.name,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    EditText birth =
        field(
            f,
            "Nascimento · dd/mm/aaaa (opcional)",
            cat == null ? "" : CareRules.displayDate(cat.birth),
            InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
    EditText adoption =
        field(
            f,
            "Adoção · dd/mm/aaaa (opcional)",
            cat == null ? "" : CareRules.displayDate(cat.adoption),
            InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
    dialog(
        cat == null ? "Adicionar gato" : "Editar " + cat.name,
        f,
        () ->
            store.saveCat(
                cat == null ? null : cat.id,
                value(name),
                CareRules.date(value(birth)),
                CareRules.date(value(adoption))));
  }

  private int number(EditText e) {
    if (value(e).isEmpty()) return 0;
    try {
      return Integer.parseInt(value(e));
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Informe um número inteiro válido para a recorrência.");
    }
  }

  private void taskForm(CareStore.Task task) {
    if (selected == null) return;
    final String cat = task == null ? selected : task.catId;
    LinearLayout f = form("task", task == null ? null : task.id);
    text(f, cats.get(cat).name, 16, GREEN, true);
    EditText title =
        field(
            f,
            "Título",
            task == null ? "" : task.title,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    int categoryIndex = 0;
    if (task != null)
      for (int i = 0; i < categories.length; i++)
        if (categories[i].equals(task.category)) categoryIndex = i;
    Spinner category = choice(f, "Categoria", categories, categoryIndex);
    ZoneId zone =
        task != null && !task.due.isEmpty()
            ? ZonedDateTime.parse(task.due).getZone()
            : ZoneId.systemDefault();
    text(
        f,
        "Horário em "
            + zone.getId()
            + ". Em uma hora repetida por mudança de fuso, vale a primeira ocorrência.",
        14,
        MUTED,
        false);
    EditText due =
        field(
            f,
            "Prazo · dd/mm/aaaa hh:mm (opcional)",
            task == null || task.due.isEmpty()
                ? ""
                : ZonedDateTime.parse(task.due).format(CareRules.INPUT),
            InputType.TYPE_CLASS_DATETIME);
    text(
        f,
        "Recorrência: preencha dias OU meses. Deixe ambos vazios para não repetir. Meses usam o"
            + " último dia disponível; o próximo parte dessa nova data.",
        14,
        MUTED,
        false);
    EditText days =
        field(
            f,
            "A cada quantos dias?",
            task == null || task.days == 0 ? "" : "" + task.days,
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
    EditText months =
        field(
            f,
            "A cada quantos meses?",
            task == null || task.months == 0 ? "" : "" + task.months,
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
    dialog(
        task == null ? "Planejar cuidado" : "Editar cuidado",
        f,
        () ->
            store.saveTask(
                cat,
                task,
                value(title),
                categories[category.getSelectedItemPosition()],
                CareRules.due(value(due), zone),
                number(days),
                number(months)));
  }

  private void deferForm(CareStore.Task task) {
    LinearLayout f = form("defer", task.id);
    text(f, cats.get(task.catId).name + " · " + task.title, 16, GREEN, true);
    ZoneId zone =
        task.due.isEmpty() ? ZoneId.systemDefault() : ZonedDateTime.parse(task.due).getZone();
    text(
        f,
        "Prazo anterior: " + CareRules.displayDue(task.due) + "\nNovo horário em " + zone.getId(),
        14,
        MUTED,
        false);
    EditText due = field(f, "Nova data · dd/mm/aaaa hh:mm", "", InputType.TYPE_CLASS_DATETIME);
    dialog(
        "Adiar cuidado",
        f,
        () -> store.transition(task, "deferred", CareRules.due(value(due), zone)));
  }

  private void recordForm() {
    if (selected == null) return;
    final String cat = selected;
    LinearLayout f = form("record", cat);
    text(f, cats.get(cat).name, 16, GREEN, true);
    Spinner type = choice(f, "Tipo de registro", types, 0);
    EditText description =
        field(
            f,
            "Descrição (inclua valor e unidade nas medições)",
            "",
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    description.setMinLines(3);
    text(
        f,
        "Registra o momento atual. Observações não são diagnósticos. Este registro não conclui"
            + " cuidados planejados.",
        14,
        MUTED,
        false);
    dialog(
        "Registrar cuidado",
        f,
        () -> store.record(cat, typeKeys[type.getSelectedItemPosition()], value(description)));
  }

  @Override
  protected void onDestroy() {
    if (store != null) store.close();
    super.onDestroy();
  }
}
