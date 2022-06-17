import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer, withWindowsContext
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {signupPage} from '../helpers/pages.js'
import {constants} from '../helpers/mocks.js'
import {createWindowsMocks} from '../helpers/mocks.windows.js'

/**
 *  Tests for email autofill on ios tooltipHandler
 */
const test = withWindowsContext(base)

test.describe('Save prompts on windows', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test.describe('When saving credentials is enabled ✅ (default)', () => {
        test('Prompting to save from a signup form', async ({page}) => {
            // enable in-terminal exceptions
            await forwardConsoleMessages(page)

            const {personalAddress} = constants.fields.email

            const credentials = {
                username: personalAddress,
                password: '123456'
            }

            const signup = signupPage(page, server)
            await signup.navigate()

            await createWindowsMocks().applyTo(page)

            await createAutofillScript()
                .platform('windows')
                .applyTo(page)

            await signup.enterCredentials(credentials)
            await signup.assertWasPromptedToSaveWindows(credentials)
        })
    //     test.describe('Prompting to save from a login form', () => {
    //         /** @param {import("playwright").Page} page */
    //         async function setup (page) {
    //             await forwardConsoleMessages(page)
    //             const login = loginPage(page, server)
    //             await login.navigate()
    //
    //             await createAndroidMocks().applyTo(page)
    //             await createAutofillScript()
    //                 .platform('android')
    //                 .applyTo(page)
    //             return { login }
    //         }
    //         test('with username+password (should prompt)', async ({page}) => {
    //             const { login } = await setup(page)
    //             const credentials = {
    //                 username: 'dax@wearejh.com',
    //                 password: '123456'
    //             }
    //             await login.submitLoginForm(credentials)
    //             await login.assertWasPromptedToSave(credentials, 'android')
    //         })
    //         test('with password only (should prompt)', async ({page}) => {
    //             const { login } = await setup(page)
    //             const credentials = { password: '123456' }
    //             await login.submitPasswordOnlyForm(credentials)
    //             await login.assertWasPromptedToSave(credentials, 'android')
    //         })
    //         test('with username only (should NOT prompt)', async ({page}) => {
    //             const { login } = await setup(page)
    //             const credentials = { username: '123456' }
    //             await login.submitUsernameOnlyForm(credentials.username)
    //             await login.assertWasNotPromptedToSave()
    //         })
    //     })
    // })
    // test.describe('When saving credentials is disabled ❌', () => {
    //     test('should not prompt to save', async ({page}) => {
    //         await forwardConsoleMessages(page)
    //
    //         const login = loginPage(page, server)
    //         await login.navigate()
    //
    //         /** @type {Partial<FeatureTogglesSettings>} */
    //         const toggles = {
    //             credentials_saving: false
    //         }
    //
    //         await createAndroidMocks()
    //             .withFeatureToggles(toggles)
    //             .applyTo(page)
    //
    //         // create + inject the script
    //         await createAutofillScript()
    //             .platform('android')
    //             .applyTo(page)
    //
    //         const credentials = {
    //             username: 'dax@wearejh.com',
    //             password: '123456'
    //         }
    //
    //         await login.submitLoginForm(credentials)
    //         await login.assertWasNotPromptedToSave()
    //
    //         /**
    //          * NOTE: This is here as a sanity check because this test is a negative check
    //          * and there are lots of ways this test could pass, but be broken.
    //          *
    //          * For example if our script fails to load, then this test would normally pass,
    //          * but by having this mock check here, it confirms that scripts ran, messages
    //          * were sent etc.
    //          */
    //         await login.assertTogglesWereMocked(toggles)
    //     })
    })
})
