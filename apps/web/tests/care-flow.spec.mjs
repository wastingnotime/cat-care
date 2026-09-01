import { expect, test } from "@playwright/test";

test("owner creates and completes a responsibility", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /How is Mimi doing/ })).toBeVisible();

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
  const completion = page.locator(".timeline li", { hasText: "Annual exam" }).first();
  await expect(completion.getByText("responsibility completed")).toBeVisible();
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

  await page.getByLabel("What did you notice?").fill("Eating less than usual");
  await page.getByRole("button", { name: "Record observation" }).click();
  const note = page.locator("li", { hasText: "Eating less than usual" });
  await note.getByRole("button", { name: "Request triage" }).click();
  await expect(page.getByText(/veterinarian review is still required/)).toBeVisible();

  const assessment = page.locator("article.triage-card", { hasText: "needs attention" });
  await assessment.getByRole("button", { name: "Mark urgent" }).click();
  await expect(assessment.getByText(/modified · urgent/)).toBeVisible();
  await assessment.getByRole("button", { name: "Add follow-up responsibility" }).click();
  await expect(page.locator("article.responsibility", { hasText: "Veterinarian follow-up" })).toBeVisible();
});
