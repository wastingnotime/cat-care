import { defineConfig } from "@playwright/test";
import { existsSync } from "node:fs";

const systemChromium = process.env.CAT_CARE_CHROMIUM_PATH || "/usr/bin/chromium";

export default defineConfig({
  testDir: "./tests",
  timeout: 20_000,
  use: {
    baseURL: process.env.WNT_WEB_E2E_BASE_URL || "http://127.0.0.1:5173",
    browserName: process.env.WNT_WEB_E2E_BROWSER || "chromium",
    trace: process.env.WNT_WEB_E2E_TRACE || "retain-on-failure",
    launchOptions: existsSync(systemChromium) ? { executablePath: systemChromium } : {},
  },
  reporter: "line",
});
