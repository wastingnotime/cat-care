package care.cat.offline;

import android.app.Instrumentation;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Uses disposable app data, like the connected-device acceptance workflow. */
public class CatCardsTest extends ActivityInstrumentationTestCase2<MainActivity> {
  private MainActivity activity;
  private CareStore store;
  private String mimi, luna;

  public CatCardsTest() {
    super(MainActivity.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getInstrumentation().getTargetContext().deleteDatabase("cat-care.db");
    getInstrumentation()
        .getTargetContext()
        .getSharedPreferences("MainActivity", 0)
        .edit()
        .clear()
        .commit();
    store = new CareStore(getInstrumentation().getTargetContext());
    mimi = store.saveCat(null, "Mimi", "", "", "calico");
    luna = store.saveCat(null, "Luna", "", "", "siamese");
    store.saveTask(mimi, null, "Água da Mimi", "Outro", "", 0, 0);
    store.saveTask(luna, null, "Ração da Luna", "Outro", "", 0, 0);
    for (int i = 0; i < 10; i++) store.saveTask(mimi, null, "Rotina " + i, "Outro", "", 0, 0);
    store.record(mimi, "observation", "Mimi brincou");
    store.record(luna, "measurement", "Luna pesa 4 kg");
    activity = getActivity();
    getInstrumentation().waitForIdleSync();
  }

  @Override
  protected void tearDown() throws Exception {
    store.close();
    super.tearDown();
  }

  private View find(View view, String label) {
    if ((view instanceof TextView && label.contentEquals(((TextView) view).getText()))
        || label.contentEquals(
            view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private View find(String label) {
    return find(activity.getWindow().getDecorView(), label);
  }

  private void click(String label) {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              View view = find(label);
              assertNotNull("Missing: " + label, view);
              assertTrue("Not clickable: " + label, view.performClick());
            });
    getInstrumentation().waitForIdleSync();
  }

  private AccessibilityNodeInfo dialogRoot() {
    getInstrumentation().waitForIdleSync();
    try {
      getInstrumentation().getUiAutomation().waitForIdle(100, 5000);
    } catch (java.util.concurrent.TimeoutException error) {
      throw new AssertionError(error);
    }
    long deadline = android.os.SystemClock.uptimeMillis() + 3000;
    AccessibilityNodeInfo root;
    do {
      root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
      if (root != null) return root;
      android.os.SystemClock.sleep(50);
    } while (android.os.SystemClock.uptimeMillis() < deadline);
    throw new AssertionError("No active dialog window");
  }

  private AccessibilityNodeInfo dialogItem(String text) {
    for (int attempt = 0; attempt < 10; attempt++) {
      AccessibilityNodeInfo root = dialogRoot();
      for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
        if (text.contentEquals(node.getText() == null ? "" : node.getText())
            || text.contentEquals(
                node.getContentDescription() == null ? "" : node.getContentDescription())) {
          if (node.isVisibleToUser()) return node;
        }
      }
      AccessibilityNodeInfo scroll = scrollNode(root);
      if (scroll == null || !scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        break;
    }
    throw new AssertionError("Missing dialog item: " + text);
  }

  private AccessibilityNodeInfo scrollNode(AccessibilityNodeInfo node) {
    if (node.isScrollable()) return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child != null) {
        AccessibilityNodeInfo result = scrollNode(child);
        if (result != null) return result;
      }
    }
    return null;
  }

  private void dialogClick(String text) {
    AccessibilityNodeInfo node = dialogItem(text);
    while (!node.isClickable() && node.getParent() != null) node = node.getParent();
    assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    dialogRoot();
  }

  private void editNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> fields) {
    if ("android.widget.EditText".contentEquals(node.getClassName())) fields.add(node);
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo child = node.getChild(i);
      if (child != null) editNodes(child, fields);
    }
  }

  private void enterFirstField(String value) {
    getInstrumentation().waitForIdleSync();
    List<AccessibilityNodeInfo> fields = new ArrayList<>();
    editNodes(dialogRoot(), fields);
    assertFalse(fields.isEmpty());
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    assertTrue(fields.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
    getInstrumentation().waitForIdleSync();
  }

  public void testCardsShowAvatarCountsAndOnlyPendingBadge() {
    click("Gatos");
    assertNotNull(find("Editar perfil de Mimi"));
    assertNotNull(find("Editar perfil de Luna"));
    assertNotNull(find("Pelagem Tricolor"));
    assertNotNull(find("Pelagem Siamês"));
    assertNotNull(find("1 observação"));
    assertNotNull(find("0 observações"));
    assertNotNull(find("1 pendente"));
    assertNull(find("Registrar cuidado de Mimi"));
    assertNull(find("Cuidados de Mimi"));
    for (CareStore.Task task : store.tasks(luna)) store.transition(task, "completed", "");
    click("Hoje");
    click("Gatos");
    View lunaCard = (View) find("Editar perfil de Luna").getParent();
    assertNotNull(find(lunaCard, "1 responsabilidade"));
    assertNull(find(lunaCard, "1 pendente"));
  }

  public void testCreateEditAndRestoreCoatDraft() {
    click("Gatos");
    click("+ Adicionar gato");
    enterFirstField("Nina");
    dialogClick("Preto e branco, selecionar pelagem");
    dialogClick("Salvar");
    assertNotNull(find("Editar perfil de Nina"));
    String nina = store.cats().stream().filter(cat -> cat.name.equals("Nina")).findFirst().get().id;
    assertEquals(
        "tuxedo", store.cats().stream().filter(cat -> cat.id.equals(nina)).findFirst().get().coat);
    click("Editar perfil de Nina");
    enterFirstField("Nina Nova");
    dialogClick("Tricolor, selecionar pelagem");
    Instrumentation.ActivityMonitor monitor =
        getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
    getInstrumentation().runOnMainSync(() -> activity.recreate());
    MainActivity recreated = (MainActivity) monitor.waitForActivityWithTimeout(5000);
    getInstrumentation().removeMonitor(monitor);
    assertNotNull(recreated);
    activity = recreated;
    setActivity(recreated);
    assertTrue(dialogItem("Tricolor, selecionada").isChecked());
    dialogClick("Salvar");
    CareStore.Cat edited =
        store.cats().stream().filter(cat -> cat.id.equals(nina)).findFirst().get();
    assertEquals("Nina Nova", edited.name);
    assertEquals("calico", edited.coat);
    assertEquals(3, store.cats().size());
    assertNotNull(find("Editar perfil de Nina Nova"));
  }
}
