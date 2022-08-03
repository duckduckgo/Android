import { PlaywrightTestConfig, devices } from '@playwright/test';

const proxy = process.env.PROXY_SERVER ? { server: process.env.PROXY_SERVER } : undefined

const config: PlaywrightTestConfig = {
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  testDir: 'tests',
  use: {
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        proxy,
        screenshot: 'only-on-failure',
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Safari/605.1.15',
      },
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        proxy,
      },
    },
    {
      name: 'chrome',
      use: {
        ...devices['Desktop Chrome'],
        proxy,
      },
    }
  ],
};
export default config;