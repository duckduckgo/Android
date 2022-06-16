// @ts-check
const { devices } = require('@playwright/test')

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * @see https://playwright.dev/docs/test-configuration
 * @type {import('@playwright/test').PlaywrightTestConfig}
 */
const config = {
    testDir: './integration-test/tests',
    /* Maximum time one test can run for. */
    timeout: 30 * 1000,
    expect: {
    /**
     * Maximum time expect() should wait for the condition to be met.
     * For example in `await expect(locator).toHaveText();`
     */
        timeout: 5000
    },
    /* Fail the build on CI if you accidentally left test.only in the source code. */
    forbidOnly: !!process.env.CI,
    /* Retry on CI only */
    retries: process.env.CI ? 2 : 0,
    /* Opt out of parallel tests on CI. */
    workers: process.env.CI ? 1 : undefined,
    // workers: 1,
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: process.env.CI ? 'github' : [ ['html', { open: 'never' }] ],
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
    /* Maximum time each action such as `click()` can take. Defaults to 0 (no limit). */
        actionTimeout: 0,
        /* Base URL to use in actions like `await page.goto('/')`. */
        // baseURL: 'http://localhost:3000',

        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry'
    },

    /* Configure projects for major browsers */
    projects: [
        {
            name: 'extension',
            testMatch: /.*extension.spec.js/,
            use: {
                ...devices['Desktop Chrome']
            }
        },
        {
            name: 'android',
            testMatch: /.*android.spec.js/,
            use: {
                ...devices['Pixel 5']
            }
        },
        // {
        //   name: 'firefox',
        //   use: {
        //     ...devices['Desktop Firefox'],
        //   },
        // },
        {
            name: 'webkit',
            testMatch: /.*(ios|macos).spec.js/,
            use: {
                ...devices['Desktop Safari']
            }
        }

        /* Test against mobile viewports. */
        // {
        //   name: 'Mobile Chrome',
        //   use: {
        //     ...devices['Pixel 5'],
        //   },
        // },
        // {
        //   name: 'Mobile Safari',
        //   use: {
        //     ...devices['iPhone 12'],
        //   },
        // },

    ]
}

module.exports = config
