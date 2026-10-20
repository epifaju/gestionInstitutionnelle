import { expect, test, type Page } from "@playwright/test";

const hasCreds = (role: string) =>
  !!(process.env[`E2E_${role}_EMAIL`] && process.env[`E2E_${role}_PASSWORD`]);

async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail|correo|email/i).fill(email);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: /connexion|login|entrar|sign in/i }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 30_000 });
}

test.describe("Smoke RBAC / parcours critiques", () => {
  test("login page is reachable", async ({ page }) => {
    const res = await page.goto("/login");
    expect(res?.ok()).toBeTruthy();
    await expect(page.locator("#email")).toBeVisible();
  });

  test("unauthenticated /dashboard redirects to login", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });

  test("FINANCIER — inventaire lecture", async ({ page }) => {
    test.skip(!hasCreds("FINANCIER"), "Set E2E_FINANCIER_EMAIL / E2E_FINANCIER_PASSWORD");
    await login(page, process.env.E2E_FINANCIER_EMAIL!, process.env.E2E_FINANCIER_PASSWORD!);
    await page.goto("/inventaire");
    await expect(page).toHaveURL(/\/inventaire/);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    // Pas de création en lecture seule
    await expect(page.getByRole("button", { name: /\+ .*bien|\+ .*asset|\+ .*bem/i })).toHaveCount(0);
  });

  test("RH — page congés", async ({ page }) => {
    test.skip(!hasCreds("RH"), "Set E2E_RH_EMAIL / E2E_RH_PASSWORD");
    await login(page, process.env.E2E_RH_EMAIL!, process.env.E2E_RH_PASSWORD!);
    await page.goto("/rh/conges");
    await expect(page).toHaveURL(/\/rh\/conges/);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("LOGISTIQUE — inventaire + export visible", async ({ page }) => {
    test.skip(!hasCreds("LOGISTIQUE"), "Set E2E_LOGISTIQUE_EMAIL / E2E_LOGISTIQUE_PASSWORD");
    await login(page, process.env.E2E_LOGISTIQUE_EMAIL!, process.env.E2E_LOGISTIQUE_PASSWORD!);
    await page.goto("/inventaire");
    await expect(page).toHaveURL(/\/inventaire/);
    await expect(page.getByRole("button", { name: /export csv|exportar csv/i })).toBeVisible();
  });

  test("EMPLOYE — pas de dashboard KPI", async ({ page }) => {
    test.skip(!hasCreds("EMPLOYE"), "Set E2E_EMPLOYE_EMAIL / E2E_EMPLOYE_PASSWORD");
    await login(page, process.env.E2E_EMPLOYE_EMAIL!, process.env.E2E_EMPLOYE_PASSWORD!);
    await page.goto("/dashboard");
    // middleware renvoie hors KPI (settings only ou home employé)
    await expect(page).not.toHaveURL(/\/dashboard$/);
  });
});
