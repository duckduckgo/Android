import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer, withWindowsContext
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {loginPage} from '../helpers/pages.js'
import {createWindowsMocks} from '../helpers/mocks.windows.js'

/**
 *  Tests for various auto-fill scenarios on macos
 */
const test = withWindowsContext(base)

test.describe('windows', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test.skip('autofill a login form', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        const {personalAddress} = constants.fields.email
        const password = '123456'

        const login = loginPage(page, server)
        await login.navigate()

        await createWindowsMocks()
            .withCredentials({
                id: '01',
                username: personalAddress,
                password
            })
            .withAvailableInputTypes({
                credentials: true
            })
            .applyTo(page)

        // Load the autofill.js script with replacements
        await createAutofillScript()
            .platform('windows')
            .applyTo(page)

        await login.selectFirstCredential(personalAddress)

        await page.waitForSelector(`button:has-text("${personalAddress}")`)
        // todo(Shane): This test is just as far as windows got.
        // await login.assertFirstCredential(personalAddress, password)
    })
    // test.describe('prompting to save data', () => {
    //     test('Prompting to save from a signup form', async ({page}) => {
    //         // enable in-terminal exceptions
    //         await forwardConsoleMessages(page)
    //
    //         const {personalAddress} = constants.fields.email
    //
    //         const credentials = {
    //             username: personalAddress,
    //             password: '123456'
    //         }
    //
    //         await createWebkitMocks()
    //             .applyTo(page)
    //
    //         // Load the autofill.js script with replacements
    //         await createAutofillScript()
    //             .replaceAll(macosContentScopeReplacements())
    //             .platform('macos')
    //             .applyTo(page)
    //
    //         const signup = signupPage(page, server)
    //         await signup.navigate()
    //         await signup.enterCredentials(credentials)
    //         await signup.assertWasPromptedToSave(credentials)
    //     })
    //     test.describe('Prompting to save from a login form', () => {
    //         test('username+password (should prompt)', async ({page}) => {
    //             // enable in-terminal exceptions
    //             await forwardConsoleMessages(page)
    //
    //             const credentials = {
    //                 username: 'dax@wearejh.com',
    //                 password: '123456'
    //             }
    //
    //             await createWebkitMocks().applyTo(page)
    //             await defaultMacosScript(page)
    //
    //             const login = loginPage(page, server)
    //             await login.navigate()
    //             await login.submitLoginForm(credentials)
    //             await login.assertWasPromptedToSave(credentials)
    //         })
    //         test('password only (should prompt)', async ({page}) => {
    //             // enable in-terminal exceptions
    //             await forwardConsoleMessages(page)
    //             await createWebkitMocks().applyTo(page)
    //             await defaultMacosScript(page)
    //
    //             const login = loginPage(page, server)
    //
    //             const credentials = { password: '123456' }
    //             await login.navigate()
    //             await login.submitPasswordOnlyForm(credentials)
    //             await login.assertWasPromptedToSave(credentials)
    //         })
    //         test('username only (should NOT prompt)', async ({page}) => {
    //             // enable in-terminal exceptions
    //             await forwardConsoleMessages(page)
    //
    //             const credentials = { username: '123456' }
    //
    //             await createWebkitMocks().applyTo(page)
    //             await defaultMacosScript(page)
    //
    //             const login = loginPage(page, server)
    //             await login.navigate()
    //             await login.submitUsernameOnlyForm(credentials.username)
    //             await login.assertWasNotPromptedToSave()
    //         })
    //     })
    // })
})
