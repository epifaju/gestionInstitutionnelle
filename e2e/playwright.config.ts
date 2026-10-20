import { defineConfig, devices } from "@playwright/test";

/**
 * Smoke E2E — nécessite une app joignable (docker-compose / local).
 * Variables : E2E_BASE_URL (défaut http://localhost), E2E_*_EMAIL / E2E_*_PASSWORD.
 */
const baseURL = process.env.E2E_BASE_URL || "http://localhost";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  timeout: 60_000,
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
