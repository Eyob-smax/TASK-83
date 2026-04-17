import { expect, test } from "@playwright/test";

test.describe("Auth to Event List flow", () => {
  test("user can register, login, and reach event list", async ({ page }) => {
    const unique = Date.now();
    const username = `e2e_user_${unique}`;
    const password = "password123";

    // Prime CSRF/session cookies through the frontend proxy before POST requests.
    await page.request.get("/api/auth/me");

    await page.goto("/account");
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Display Name").fill("E2E User");
    await page.getByLabel("Contact Info").fill(`e2e+${unique}@example.test`);
    await page.getByLabel("Password").fill(password);
    await page.getByLabel("Confirm Password").fill(password);
    await page.getByRole("button", { name: "Create Account" }).click();

    await expect(page.getByRole("status")).toContainText("Account Created");
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Password").fill(password);
    await page.getByRole("button", { name: "Sign In" }).click();

    await expect(page).toHaveURL(/\/$/);
    await expect(
      page.getByRole("heading", { name: "Event Sessions" }),
    ).toBeVisible();
  });
});
