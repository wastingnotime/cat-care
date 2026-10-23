package care.cat.offline;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
  private static final int GREEN = 0xff285345,
      INK = 0xff243c32,
      MUTED = 0xff5c6b60,
      PAPER = 0xfff8f7f2,
      SAGE = 0xffe9eee4,
      LINE = 0xffdfe5da,
      RUST = 0xff954c38;
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
  private String draftCoat, revealCat;
  private final Map<CatCoat, LinearLayout> coatOptions = new LinkedHashMap<>();
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
      draft.putString("coat", draftCoat);
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
    if ("cat".equals(kind)) selectCoat(draft.getString("coat", "gray"));
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
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(3), 1f);
    t.setPadding(0, dp(4), 0, dp(4));
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    parent.addView(t);
    return t;
  }

  private void heading(LinearLayout parent, String value) {
    TextView t = text(parent, value, 28, INK, true);
    t.setLetterSpacing(-0.025f);
    t.setPadding(0, dp(10), 0, dp(8));
    if (Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(true);
  }

  private RippleDrawable surface(int color, int radius, boolean border) {
    GradientDrawable shape = background(color, radius);
    if (border) shape.setStroke(dp(1), LINE);
    return new RippleDrawable(
        ColorStateList.valueOf(0x243f6954), shape, background(Color.WHITE, radius));
  }

  private void styleButton(Button b, boolean primary, boolean danger) {
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(primary ? Color.WHITE : danger ? RUST : GREEN);
    b.setBackgroundTintList(null);
    b.setBackground(
        surface(primary ? GREEN : danger ? 0xfffbefea : Color.WHITE, 16, !primary && !danger));
    b.setStateListAnimator(null);
    b.setElevation(0);
    b.setMinHeight(dp(50));
    b.setMinimumHeight(dp(50));
    b.setPadding(dp(16), dp(12), dp(16), dp(12));
  }

  private Button button(LinearLayout parent, String value, Runnable action) {
    Button b = new Button(this);
    b.setText(value);
    styleButton(b, false, false);
    b.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(8), 0, dp(2));
    parent.addView(b, lp);
    return b;
  }

  private Button primaryButton(LinearLayout parent, String value, Runnable action) {
    Button b = button(parent, value, action);
    styleButton(b, true, false);
    return b;
  }

  private Button dangerButton(LinearLayout parent, String value, Runnable action) {
    Button b = button(parent, value, action);
    styleButton(b, false, true);
    return b;
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(dp(20), dp(18), dp(20), dp(18));
    GradientDrawable shape = background(Color.WHITE, 24);
    shape.setStroke(dp(1), LINE);
    c.setBackground(shape);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(12), 0, dp(4));
    parent.addView(c, lp);
    return c;
  }

  private void pill(LinearLayout parent, String label, int color, int fill) {
    TextView t = text(parent, label, 12, color, true);
    t.setBackground(background(fill, 20));
    t.setPadding(dp(11), dp(6), dp(11), dp(6));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.setMargins(0, dp(8), 0, dp(8));
    t.setLayoutParams(lp);
  }

  private void illustration(LinearLayout parent, int height) {
    parent.addView(new CatIllustration(this), new LinearLayout.LayoutParams(-1, dp(height)));
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
    LinearLayout brand = new LinearLayout(this);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    brand.setPadding(dp(24), dp(12), dp(24), dp(10));
    page.addView(brand);
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_nav_cats);
    logo.setColorFilter(GREEN);
    logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    brand.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(26)));
    TextView wordmark = text(brand, "cat care", 20, GREEN, true);
    LinearLayout.LayoutParams wordmarkParams = new LinearLayout.LayoutParams(0, -2, 1);
    wordmarkParams.setMargins(dp(8), 0, 0, 0);
    wordmark.setLayoutParams(wordmarkParams);
    TextView offline = text(brand, "NO SEU RITMO", 10, MUTED, true);
    offline.setLetterSpacing(0.12f);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content = column();
    content.setPadding(dp(24), dp(8), dp(24), dp(28));
    scroll.addView(content);
    if (screen.equals("today")) today();
    else if (screen.equals("cats")) catList();
    else if (screen.equals("history")) history();
    else data();
    LinearLayout nav = new LinearLayout(this);
    nav.setPadding(dp(16), dp(10), dp(16), dp(8));
    nav.setBackgroundColor(Color.WHITE);
    page.addView(nav);
    String[] labels = {"Hoje", "Gatos", "Histórico", "Dados"};
    String[] routes = {"today", "cats", "history", "data"};
    int[] icons = {
      R.drawable.ic_nav_today,
      R.drawable.ic_nav_cats,
      R.drawable.ic_nav_history,
      R.drawable.ic_nav_data
    };
    for (int i = 0; i < labels.length; i++) {
      String route = routes[i];
      boolean active = screen.equals(route);
      LinearLayout tab = column();
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(dp(3), dp(7), dp(3), dp(7));
      tab.setMinimumHeight(dp(60));
      tab.setBackground(surface(active ? SAGE : Color.WHITE, 20, false));
      tab.setFocusable(true);
      tab.setClickable(true);
      tab.setSelected(active);
      tab.setAccessibilityDelegate(
          new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              info.setClassName(Button.class.getName());
            }
          });
      tab.setContentDescription(labels[i] + (active ? ", aba selecionada" : ""));
      ImageView icon = new ImageView(this);
      icon.setImageResource(icons[i]);
      icon.setColorFilter(active ? GREEN : MUTED);
      icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      tab.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
      TextView label = text(tab, labels[i], 12, active ? GREEN : MUTED, active);
      label.setGravity(Gravity.CENTER);
      label.setSingleLine(true);
      label.setEllipsize(TextUtils.TruncateAt.END);
      label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      tab.setOnClickListener(
          v -> {
            screen = route;
            render();
          });
      float weight =
          getResources().getConfiguration().fontScale > 1.2f
              ? label.getPaint().measureText(labels[i]) + dp(20)
              : 1;
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, weight);
      lp.setMargins(dp(3), 0, dp(3), 0);
      nav.addView(tab, lp);
    }
  }

  private void empty() {
    LinearLayout c = card(content);
    c.setBackground(background(SAGE, 28));
    illustration(c, 172);
    TextView title = text(c, "Uma vida juntos.\nUm cuidado de cada vez.", 25, INK, true);
    title.setLetterSpacing(-0.025f);
    text(
        c,
        "Organize a rotina, acompanhe pequenas mudanças e guarde a história do seu gato.",
        16,
        MUTED,
        false);
    primaryButton(c, "Adicionar meu primeiro gato", () -> catForm(null));
    TextView foot = text(c, "Só você e seus gatos. Até sem internet.", 12, MUTED, false);
    foot.setGravity(Gravity.CENTER);
    foot.setPadding(0, dp(14), 0, dp(2));
  }

  private void selectCat() {
    if (cats.isEmpty()) return;
    TextView label = text(content, "CUIDANDO DE", 11, MUTED, true);
    label.setLetterSpacing(0.1f);
    label.setPadding(0, dp(12), 0, dp(8));
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(this);
    scroll.addView(row);
    content.addView(scroll);
    for (CareStore.Cat cat : cats.values()) {
      boolean active = cat.id.equals(selected);
      Button chip = new Button(this);
      chip.setText(cat.name);
      styleButton(chip, active, false);
      chip.setMinHeight(dp(48));
      chip.setMinimumHeight(dp(48));
      chip.setMinWidth(dp(80));
      chip.setBackground(surface(active ? GREEN : Color.WHITE, 24, !active));
      chip.setSelected(active);
      chip.setContentDescription(cat.name + (active ? ", gato selecionado" : ", selecionar gato"));
      chip.setOnClickListener(
          v -> {
            selected = cat.id;
            render();
          });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
      lp.setMargins(0, 0, dp(8), dp(4));
      row.addView(chip, lp);
      if (active) scroll.post(() -> scroll.smoothScrollTo(chip.getLeft(), 0));
    }
  }

  private void today() {
    text(
        content,
        LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", new Locale("pt", "BR"))),
        13,
        MUTED,
        false);
    heading(content, "Hoje, com carinho.");
    if (cats.isEmpty()) {
      empty();
      return;
    }
    List<CareStore.Task> planned = new ArrayList<>();
    for (CareStore.Task task : store.tasks(null))
      if (task.state.equals("planned")) planned.add(task);
    pill(
        content,
        planned.isEmpty()
            ? "Tudo em dia por aqui"
            : (planned.size() == 1
                    ? "1 cuidado planejado"
                    : planned.size() + " cuidados planejados")
                + " · todos os gatos",
        GREEN,
        SAGE);
    selectCat();
    LinearLayout actions = new LinearLayout(this);
    boolean stacked =
        getResources().getConfiguration().fontScale > 1.2f
            || getResources().getConfiguration().screenWidthDp < 360;
    actions.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    content.addView(actions);
    Button plan = primaryButton(actions, "+ Planejar cuidado", () -> taskForm(null));
    plan.setContentDescription("Planejar cuidado para " + cats.get(selected).name);
    Button record = button(actions, "Registrar", this::recordForm);
    record.setContentDescription("Registrar cuidado ou observação de " + cats.get(selected).name);
    if (!stacked) {
      LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(0, -2, 1.3f);
      planParams.setMargins(0, dp(8), dp(8), dp(2));
      plan.setLayoutParams(planParams);
      LinearLayout.LayoutParams recordParams = new LinearLayout.LayoutParams(0, -2, 1);
      recordParams.setMargins(0, dp(8), 0, dp(2));
      record.setLayoutParams(recordParams);
    }
    TextView section = text(content, "Próximos cuidados", 20, INK, true);
    section.setPadding(0, dp(20), 0, dp(2));
    if (Build.VERSION.SDK_INT >= 28) section.setAccessibilityHeading(true);
    text(content, "Prazos de rotina, sem indicar urgência clínica.", 12, MUTED, false);
    for (CareStore.Task task : planned) taskCard(task);
    if (planned.isEmpty())
      text(content, "Nenhum cuidado pendente. Aproveite a companhia.", 15, MUTED, false);
  }

  private void taskCard(CareStore.Task task) {
    LinearLayout c = card(content);
    CareStore.Cat cat = cats.get(task.catId);
    text(c, cat.name + " · " + task.category, 14, GREEN, true);
    TextView title = text(c, task.title, 21, INK, true);
    if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
    String status = CareRules.status(task.state, task.due, Instant.now());
    boolean late = status.equals("Prazo passado");
    pill(c, status, late ? RUST : GREEN, late ? 0xfff8e9e1 : SAGE);
    if (!task.due.isEmpty()) text(c, CareRules.displayDue(task.due), 13, MUTED, false);
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

  private String countLabel(int count, String singular, String plural) {
    return count + " " + (count == 1 ? singular : plural);
  }

  private void catList() {
    heading(content, "Seus gatos");
    text(content, "Cada história tem seu próprio espaço.", 16, MUTED, false);
    if (cats.isEmpty()) {
      empty();
      return;
    }
    primaryButton(content, "+ Adicionar gato", () -> catForm(null));
    Instant now = Instant.now();
    for (CareStore.Cat cat : cats.values()) {
      CareStore.CatSummary summary = store.summary(cat.id, now);
      LinearLayout c = card(content);
      LinearLayout identity = new LinearLayout(this);
      identity.setGravity(Gravity.CENTER_VERTICAL);
      c.addView(identity);
      CatAvatar avatar = new CatAvatar(this, cat.coat);
      identity.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));
      TextView name = text(identity, cat.name, 24, INK, true);
      if (Build.VERSION.SDK_INT >= 28) name.setAccessibilityHeading(true);
      LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
      nameParams.setMargins(dp(16), 0, 0, 0);
      name.setLayoutParams(nameParams);
      if (summary.pending > 0) {
        String status = countLabel(summary.pending, "pendente", "pendentes");
        if (summary.overdue > 0)
          status += " · " + countLabel(summary.overdue, "atrasado", "atrasados");
        pill(
            c, status, summary.overdue > 0 ? RUST : GREEN, summary.overdue > 0 ? 0xfff8e9e1 : SAGE);
      }
      text(
          c,
          countLabel(summary.responsibilities, "responsabilidade", "responsabilidades"),
          16,
          INK,
          false);
      text(c, countLabel(summary.observations, "observação", "observações"), 16, INK, false);
      button(c, "Editar perfil de " + cat.name, () -> catForm(cat));
      if (cat.id.equals(revealCat)) {
        revealCat = null;
        c.post(
            () ->
                c.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, c.getWidth(), dp(120)), true));
      }
    }
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
    heading(content, "Histórias de cuidado");
    text(content, "Os pequenos gestos também ficam na memória.", 15, MUTED, false);
    if (cats.isEmpty()) {
      empty();
      return;
    }
    selectCat();
    primaryButton(content, "Registrar cuidado ou observação", this::recordForm);
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
    heading(content, "Seus dados");
    pill(content, "Salvos neste aparelho", GREEN, SAGE);
    text(content, "Você escolhe o que guardar e o que apagar.", 16, MUTED, false);
    LinearLayout info = card(content);
    text(info, "Guarde uma cópia", 20, INK, true);
    text(
        info,
        "Exporte um arquivo para consulta. A importação e a restauração ainda não estão"
            + " disponíveis.",
        15,
        MUTED,
        false);
    button(
        info,
        "Como os dados são guardados",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Seus dados, neste aparelho")
                .setMessage(
                    "Funciona sem internet e sem conta. Não há sincronização nem lembretes"
                        + " automáticos nesta versão.\n\n"
                        + "A exportação cria um arquivo JSON para consulta. Esta versão não importa"
                        + " nem restaura arquivos. Desinstalar, limpar os dados ou perder o"
                        + " aparelho pode apagar seus registros. O backup e a transferência"
                        + " automáticos estão desativados para os dados do app; alguns fabricantes"
                        + " podem aplicar regras próprias.\n\n"
                        + "Arquivos exportados ficam no destino escolhido e não são apagados pelo"
                        + " app. Se escolher um provedor de nuvem, ele pode exigir internet. Use"
                        + " uma pasta local para exportar offline.")
                .setPositiveButton("Entendi", null)
                .show());
    if (cats.isEmpty()) {
      text(content, "Nenhum dado de gato salvo.", 16, MUTED, false);
      return;
    }
    selectCat();
    CareStore.Cat cat = cats.get(selected);
    primaryButton(content, "Exportar " + cat.name, () -> previewExport(cat.id));
    button(content, "Exportar todos os gatos", () -> previewExport(null));
    TextView deletion = text(content, "Excluir dados", 18, INK, true);
    deletion.setPadding(0, dp(24), 0, dp(4));
    text(content, "A exclusão é permanente neste app.", 13, MUTED, false);
    dangerButton(
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
    dangerButton(
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
    draftCoat = null;
    coatOptions.clear();
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
    edit.setMinHeight(dp(52));
    edit.setTextColor(INK);
    edit.setPadding(dp(14), dp(12), dp(14), dp(12));
    edit.setBackground(surface(PAPER, 12, true));
    LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
    fieldParams.setMargins(0, dp(4), 0, dp(14));
    form.addView(edit, fieldParams);
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
          draftCoat = null;
          coatOptions.clear();
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
    if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(background(Color.WHITE, 28));
    d.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
    d.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
  }

  private void catForm(CareStore.Cat cat) {
    LinearLayout f = form("cat", cat == null ? null : cat.id);
    EditText name =
        field(
            f,
            "Nome",
            cat == null ? "" : cat.name,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    EditText birth = dateField(f, "Nascimento (opcional)", cat == null ? "" : cat.birth);
    EditText adoption = dateField(f, "Adoção (opcional)", cat == null ? "" : cat.adoption);
    coatPicker(f, cat == null ? "gray" : cat.coat);
    dialog(
        cat == null ? "Adicionar gato" : "Editar " + cat.name,
        f,
        () -> {
          String id =
              store.saveCat(
                  cat == null ? null : cat.id,
                  value(name),
                  CareRules.date(value(birth)),
                  CareRules.date(value(adoption)),
                  draftCoat);
          revealCat = id;
          if (cat == null) selected = id;
          screen = "cats";
        });
  }

  private EditText dateField(LinearLayout form, String label, String isoDate) {
    EditText field = field(form, label, CareRules.displayDate(isoDate), InputType.TYPE_NULL);
    field.setHint("Escolher data");
    field.setKeyListener(null);
    field.setFocusable(true);
    field.setShowSoftInputOnFocus(false);
    field.setCursorVisible(false);
    field.setClickable(true);
    field.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
          }
        });
    field.setOnClickListener(
        view -> {
          LocalDate date =
              value(field).isEmpty()
                  ? LocalDate.now()
                  : LocalDate.parse(value(field), CareRules.DATE);
          DatePickerDialog picker =
              new DatePickerDialog(
                  this,
                  (v, year, month, day) ->
                      field.setText(LocalDate.of(year, month + 1, day).format(CareRules.DATE)),
                  date.getYear(),
                  date.getMonthValue() - 1,
                  date.getDayOfMonth());
          picker.setTitle(label);
          picker.setButton(
              DialogInterface.BUTTON_NEUTRAL, "Limpar data", (d, which) -> field.setText(""));
          picker.show();
        });
    return field;
  }

  private void coatPicker(LinearLayout form, String initial) {
    text(form, "Pelagem mais parecida", 18, INK, true);
    text(form, "Escolha o rostinho que mais parece com seu gato.", 14, MUTED, false);
    int columns = getResources().getConfiguration().fontScale > 1.2f ? 1 : 2;
    LinearLayout row = null;
    CatCoat[] coats = CatCoat.values();
    for (int i = 0; i < coats.length; i++) {
      CatCoat coat = coats[i];
      if (i % columns == 0) {
        row = new LinearLayout(this);
        form.addView(row);
      }
      LinearLayout option = column();
      option.setGravity(Gravity.CENTER);
      option.setPadding(dp(8), dp(10), dp(8), dp(10));
      option.setFocusable(true);
      option.setClickable(true);
      option.setOnClickListener(v -> selectCoat(coat.id));
      option.setAccessibilityDelegate(
          new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              info.setClassName(RadioButton.class.getName());
              info.setCheckable(true);
              info.setChecked(coat.id.equals(draftCoat));
            }
          });
      CatAvatar avatar = new CatAvatar(this, coat.id);
      avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      option.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
      TextView label = text(option, coat.label, 14, GREEN, true);
      label.setGravity(Gravity.CENTER);
      label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
      lp.setMargins(dp(3), dp(4), dp(3), dp(4));
      row.addView(option, lp);
      coatOptions.put(coat, option);
    }
    selectCoat(initial);
  }

  private void selectCoat(String id) {
    draftCoat = id;
    for (Map.Entry<CatCoat, LinearLayout> entry : coatOptions.entrySet()) {
      CatCoat coat = entry.getKey();
      LinearLayout option = entry.getValue();
      boolean active = coat.id.equals(id);
      option.setSelected(active);
      option.setBackground(surface(active ? SAGE : Color.WHITE, 16, true));
      option.setContentDescription(
          coat.label + (active ? ", selecionada" : ", selecionar pelagem"));
      ((TextView) option.getChildAt(1)).setText((active ? "✓ " : "") + coat.label);
    }
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
