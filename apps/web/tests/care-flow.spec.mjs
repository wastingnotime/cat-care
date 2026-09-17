import { expect, test } from "@playwright/test";

async function logOn(page, mode="owner") {
  await page.goto("/login");
  if (mode === "veterinarian") await page.getByRole("button", { name: "Use veterinarian demo" }).click();
  await page.getByRole("button", { name: "Log on" }).click();
  await page.waitForURL(mode === "veterinarian" ? "/triage" : "/");
}

test.beforeEach(async ({ page }) => {
  await logOn(page);
});

test("owner creates and completes a responsibility", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /How is Mimi doing/ })).toBeVisible();

  await page.getByRole("button", { name: "Add observation" }).click();
  await expect(page.getByRole("textbox", { name: "What did you notice?" })).toBeFocused();
  await page.getByRole("button", { name: "Record direct care" }).click();
  await expect(page.getByRole("textbox", { name: "Description" })).toBeFocused();

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await page.getByLabel("What needs to happen?").fill("Annual exam");
  await page.getByLabel("Category").selectOption("veterinary");
  await page.getByRole("button", { name: "Save responsibility" }).click();

  const responsibility = page.locator("article.responsibility", { hasText: "Annual exam" });
  await expect(responsibility).toBeVisible();
  await expect(page.getByText("Some future care information is unknown.")).toBeVisible();

  await responsibility.getByRole("button", { name: "Mark Annual exam complete" }).click();
  await expect(responsibility).toHaveClass(/completed/);
  await expect(page.getByText("Nothing important is pending.")).toBeVisible();
  const completion = page.locator(".history-card", { hasText: "Annual exam" }).first();
  await expect(completion.getByText("responsibility completed")).toBeVisible();
  await expect(page.locator(".history-card", { hasText: "Annual exam" })).toHaveCount(1);
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
  await expect(note.getByRole("button", { name: "Request triage" })).toHaveCount(0);
  await expect(note.getByText("Triage was requested.")).toBeVisible();
  const triageEvent = page.locator(".history-card", { hasText: "observation: Eating less than usual" });
  await expect(triageEvent.getByText("triage requested", { exact: true })).toBeVisible();
  await expect(triageEvent.getByText(/triage assessed: The observation merits timely professional review/)).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await page.waitForURL("/login");
  await logOn(page, "veterinarian");
  const assessment = page.locator("article.triage-card", { hasText: "needs attention" });
  await expect(page.locator(".site-header select")).toHaveCount(0);
  await expect(assessment.getByText("Mimi", { exact: true })).toBeVisible();
  await expect(assessment.getByText("Eating less than usual", { exact: true })).toBeVisible();
  await assessment.getByRole("button", { name: "Ask owner" }).click();
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
  await page.locator(".cat-card", { hasText: "Mimi" }).click();
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
  await page.waitForURL(/\/cats\/cat-2$/);
  await expect(page.getByRole("heading", { name: "About Nina" })).toBeVisible();
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "How are your cats doing?" })).toBeVisible();
  await expect(page.locator(".status-label", { hasText: "Mimi" })).toBeVisible();
  await expect(page.locator(".status-label", { hasText: "Nina" })).toBeVisible();
  await expect(page.locator(".site-header select")).toHaveCount(0);

  await page.getByRole("button", { name: "Add observation" }).click();
  await expect(page.getByLabel("Cat", { exact: true })).toHaveValue(/cat-2/);
  await page.getByLabel("Cat", { exact: true }).selectOption({ label: "Mimi" });
  await page.getByLabel("What did you notice?").fill("Mimi slept by the window");
  await page.getByRole("button", { name: "Add to history" }).click();
  await expect(page.locator(".history-card", { hasText: "Mimi slept by the window" }).getByText("Observation · Mimi")).toBeVisible();

  await page.getByRole("button", { name: "Add responsibility" }).click();
  await expect(page.getByLabel("Cat", { exact: true })).toHaveValue(/cat-1/);
  await page.getByLabel("Cat", { exact: true }).selectOption({ label: "Nina" });
  await page.getByLabel("What needs to happen?").fill("Brush coat");
  await page.getByRole("button", { name: "Save responsibility" }).click();
  const responsibility = page.locator("article.responsibility", { hasText: "Brush coat" });
  await expect(responsibility.getByText("Nina", { exact: true })).toBeVisible();
});
