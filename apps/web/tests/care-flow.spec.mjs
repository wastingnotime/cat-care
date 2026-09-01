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
  await expect(page.getByText("Completed Annual exam")).toBeVisible();
});
