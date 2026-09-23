import { expect, test } from "@playwright/test";

async function logOn(page, mode="owner") {
  await page.goto("/login");
  if (mode === "veterinarian") await page.getByRole("button", { name: "Use dual-role veterinarian demo" }).click();
  await page.getByRole("button", { name: "Log on" }).click();
  await page.waitForURL(mode === "veterinarian" ? "/triage" : "/");
}

test.beforeEach(async ({ page }) => {
  await logOn(page);
  const reset = await page.request.post("/api/dev/reset-seed");
  expect(reset.ok()).toBeTruthy();
});

test("owner creates and completes a responsibility", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "How are your cats doing?" })).toBeVisible();

  await page.getByRole("button", { name: "Add observation" }).click();
  await expect(page.getByRole("textbox", { name: "What did you notice?" })).toBeFocused();
  await page.getByRole("button", { name: "Record direct care" }).click();
  await expect(page.getByRole("textbox", { name: "What did you notice?" })).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Description" })).toBeFocused();

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await expect(page.getByRole("textbox", { name: "Description" })).toHaveCount(0);
  await expect(page.locator(".responsibility-form .quick-cat-picker")).toBeVisible();
  await page.getByRole("button", { name: "Add observation" }).click();
  await expect(page.locator(".responsibility-form")).toHaveCount(0);
  await page.getByRole("button", { name: "Add responsibility" }).click();
  await expect(page.locator(".quick-action-form")).toHaveCount(0);
  await page.getByLabel("What needs to happen?").fill("Annual exam");
  await page.getByLabel("Category").selectOption("veterinary");
  await page.getByRole("button", { name: "Save responsibility" }).click();

  const responsibility = page.locator("article.responsibility", { hasText: "Annual exam" });
  await expect(responsibility).toBeVisible();
  await expect(page.getByText("Set a due date for Annual exam.")).toBeVisible();

  await responsibility.getByRole("button", { name: "Mark Annual exam complete" }).click();
  await expect(responsibility).toHaveClass(/completed/);
  await expect(page.getByText("Nothing important is pending.")).toBeVisible();
  const completion = page.locator(".history-card", { hasText: "Annual exam" }).first();
  await expect(completion.getByText("responsibility completed")).toBeVisible();
  await expect(page.locator(".history-card", { hasText: "Annual exam" })).toHaveCount(1);
});

test("undated care can be scheduled and finished care leaves Today after the day", async ({ page }) => {
  await page.clock.install({ time: new Date() });
  await page.goto("/");

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await page.getByLabel("What needs to happen?").fill("Schedule me");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const scheduled = page.locator("article.responsibility", { hasText: "Schedule me" });
  await scheduled.getByRole("button", { name: "Set due date" }).click();
  await scheduled.getByLabel("Due date for Schedule me").fill(new Date(Date.now() + 4 * 86400000).toISOString().slice(0, 16));
  await scheduled.getByRole("button", { name: "Save due date" }).click();
  await expect(scheduled.getByRole("button", { name: "Set due date" })).toHaveCount(0);
  await expect(scheduled).not.toContainText("unknown");

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await page.getByLabel("What needs to happen?").fill("Complete me");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const completed = page.locator("article.responsibility", { hasText: "Complete me" });
  await completed.getByRole("button", { name: "Mark Complete me complete" }).click();
  await expect(completed).toHaveClass(/completed/);

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await page.getByLabel("What needs to happen?").fill("Cancel me");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const cancelled = page.locator("article.responsibility", { hasText: "Cancel me" });
  await cancelled.getByRole("button", { name: "Cancel", exact: true }).click();
  const rows = page.locator(".responsibility-list article.responsibility");
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toContainText("Schedule me");
  await expect(cancelled).toHaveClass(/cancelled/);

  await page.clock.fastForward(86400000);
  await expect(completed).toHaveCount(0);
  await expect(cancelled).toHaveCount(0);
  await expect(scheduled).toBeVisible();
});

test("owner records observations and veterinarian reviews provisional triage", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await page.getByLabel("What needs to happen?").fill("Dental cleaning");
  await page.getByLabel("Category").selectOption("preventive");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const responsibility = page.locator("article.responsibility", { hasText: "Dental cleaning" });
  await responsibility.getByRole("button", { name: "Notify" }).click();
  await expect(page.getByText(/responsibility state was not changed/)).toBeVisible();

  await page.getByRole("button", { name: "Add observation" }).click();
  await page.getByLabel("What did you notice?").fill("Eating less than usual");
  await page.getByRole("button", { name: "Add to history" }).click();
  const note = page.locator(".history-card", { hasText: "Eating less than usual" });
  await note.getByRole("button", { name: "Request triage" }).click();
  await expect(page.getByText(/veterinarian review is still required/)).toBeVisible();
  await expect(page.locator(".hero .status-card[data-kind='review_pending']")).toContainText("Mimi");
  await expect(note.getByRole("button", { name: "Request triage" })).toHaveCount(0);
  await expect(note.getByText("Triage was requested.")).toBeVisible();
  const triageEvent = page.locator(".history-card", { hasText: "observation: Eating less than usual" });
  await expect(triageEvent.getByText("triage requested", { exact: true })).toBeVisible();
  await expect(triageEvent.getByText(/triage assessed: The observation merits timely professional review/)).toBeVisible();

  await page.goto("/account");
  await page.getByLabel("Veterinarian email").fill("vet@cat.care");
  await page.getByRole("button", { name: "Send invitation" }).click();
  await expect(page.getByText("Dr. Silva · pending")).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page, "veterinarian");
  await expect(page.getByText("No assessments waiting.")).toBeVisible();
  await page.getByRole("button", { name: "Accept invitation" }).click();
  await expect(page.getByText("Alex · accepted")).toBeVisible();
  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page);
  await page.goto("/cats/cat-1");
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Assigned veterinarian").selectOption("vet-local");
  await expect(page.getByRole("status")).toHaveText("Cat veterinarian updated.");
  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page, "veterinarian");
  const assessment = page.locator("article.triage-card", { hasText: "needs attention" });
  await expect(page.locator(".site-header select")).toHaveCount(0);
  await expect(assessment.getByText("Mimi", { exact: true })).toBeVisible();
  await expect(assessment.getByText("Eating less than usual", { exact: true })).toBeVisible();
  await assessment.getByRole("button", { name: "Ask owner" }).click();
  await assessment.getByLabel("Question for the owner").fill("Please share appetite and energy changes.");
  await assessment.getByRole("button", { name: "Send question" }).click();
  await expect(assessment.getByText("Please share appetite and energy changes.")).toBeVisible();
  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page);
  const ownerTriage = page.locator(".history-card", { hasText: "observation: Eating less than usual" });
  await ownerTriage.getByRole("button", { name: "Comment" }).click();
  await ownerTriage.getByPlaceholder("Write a comment…").fill("She ate a little this morning.");
  await ownerTriage.getByRole("button", { name: "Post" }).click();
  await expect(ownerTriage.getByText("She ate a little this morning.")).toBeVisible();
  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page, "veterinarian");
  await expect(assessment.getByText("She ate a little this morning.")).toBeVisible();
  await assessment.getByRole("button", { name: "Mark urgent" }).click();
  await expect(assessment.getByText(/modified · urgent/)).toBeVisible();
  await assessment.getByRole("button", { name: "Add follow-up responsibility" }).click();
  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page);
  await expect(page.locator("article.responsibility", { hasText: "Veterinarian follow-up" })).toBeVisible();
});

test("primary navigation separates cats and data stewardship", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("link", { name: "Today", exact: true })).toHaveAttribute("aria-current", "page");
  await expect(page.getByRole("link", { name: "Triage", exact: true })).toHaveCount(0);

  await page.goto("/triage");
  await expect(page.getByRole("heading", { name: "Clinical review is restricted." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Veterinarian review" })).toHaveCount(0);

  await page.goto("/cats");
  await expect(page.getByRole("heading", { name: "One home, every cat." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Cats", exact: true })).toHaveAttribute("aria-current", "page");
  await page.locator(".cat-card", { hasText: "Mimi" }).locator(".cat-card-identity").click();
  await expect(page.getByRole("heading", { name: /About Mimi/ })).toBeVisible();
  await expect(page).toHaveURL(/\/cats\/cat-1$/);
  await page.getByRole("radio", { name: "Orange tabby" }).check();
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("status")).toHaveText("Profile saved.");
  await page.getByRole("link", { name: "All cats" }).click();
  await expect(page.locator(".cat-card", { hasText: "Mimi" }).getByRole("img", { name: /orange-tabby cat/ })).toBeVisible();

  await page.getByRole("link", { name: "Account & data", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Data stewardship" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Download export" })).toBeVisible();
});

test("owner handles multiple cats without switching dashboard context", async ({ page }) => {
  await page.goto("/cats");
  await page.getByPlaceholder("Cat name").fill("Nina");
  await page.getByRole("button", { name: "Add cat" }).click();
  await page.waitForURL(/\/cats\/cat-\d+$/);
  await expect(page.getByRole("heading", { name: "About Nina" })).toBeVisible();
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "How are your cats doing?" })).toBeVisible();
  await expect(page.locator(".hero .status-card")).toHaveCount(1);
  await expect(page.locator(".hero .status-label")).toHaveText("All calm");
  await expect(page.locator(".site-header select")).toHaveCount(0);

  await page.getByRole("button", { name: "Add observation" }).click();
  await expect(page.getByRole("radio", { name: /Nina/ })).toBeChecked();
  await page.locator(".quick-cat-picker label", { hasText: "Mimi" }).click();
  await page.getByLabel("What did you notice?").fill("Mimi slept by the window");
  await page.getByRole("button", { name: "Add to history" }).click();
  await expect(page.locator(".history-card", { hasText: "Mimi slept by the window" }).getByText("Mimi", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await expect(page.locator(".responsibility-form").getByRole("radio", { name: /Mimi/ })).toBeChecked();
  await page.locator(".responsibility-form .quick-cat-picker label", { hasText: "Nina" }).click();
  await page.getByLabel("What needs to happen?").fill("Brush coat");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const responsibility = page.locator("article.responsibility", { hasText: "Brush coat" });
  await expect(responsibility.getByText("Nina", { exact: true })).toBeVisible();
});

test("Today groups matching issues and keeps different levels visible", async ({ page }) => {
  await page.goto("/cats");
  await page.getByPlaceholder("Cat name").fill("Pip");
  await page.getByRole("button", { name: "Add cat" }).click();
  await page.waitForURL(/\/cats\/cat-\d+$/);

  await page.goto("/");
  const addResponsibility = async (cat, title, dueAt) => {
    await page.getByRole("button", { name: "Add responsibility" }).click();
    await page.locator(".responsibility-form .quick-cat-picker label", { hasText: cat }).click();
    await page.getByLabel("What needs to happen?").fill(title);
    await page.getByLabel("Due date").fill(dueAt);
    await page.getByRole("button", { name: "Save responsibility" }).click();
  };
  const overdue = new Date(Date.now() - 2 * 86400000).toISOString().slice(0, 16);
  const soon = new Date(Date.now() + 86400000).toISOString().slice(0, 16);
  await addResponsibility("Mimi", "Mimi exam", overdue);
  await addResponsibility("Luna", "Luna vaccine", overdue);
  await addResponsibility("Pip", "Pip checkup", soon);

  const attention = page.locator(".hero .status-card[data-kind='overdue']");
  const comingUp = page.locator(".hero .status-card[data-kind='due_soon']");
  await expect(page.locator(".hero .status-card")).toHaveCount(2);
  await expect(attention.getByText("Mimi")).toBeVisible();
  await expect(attention.getByText("Luna")).toBeHidden();
  await expect(comingUp.getByText("Pip")).toBeVisible();
  await attention.locator("summary").click();
  await expect(attention.getByText("Luna")).toBeVisible();

  await page.goto("/cats");
  await page.locator(".cat-card", { hasText: "Pip" }).getByRole("link", { name: /View Today/ }).click();
  await expect(page).toHaveURL(/\/$/);
});
