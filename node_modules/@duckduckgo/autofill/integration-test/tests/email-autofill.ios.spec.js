import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer,
    withIOSContext
} from '../helpers/harness.js'
import { test as base } from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {emailAutofillPage, loginPage} from '../helpers/pages.js'
import {createWebkitMocks, iosContentScopeReplacements} from '../helpers/mocks.webkit.js'

/**
 *  Tests for email autofill on ios tooltipHandler
 */
const test = withIOSContext(base)

test.describe('ios', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test('should autofill the selected email', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        await createWebkitMocks('ios')
            .withPersonalEmail('0')
            .withPrivateEmail('0')
            .withAvailableInputTypes({
                email: true
            })
            .applyTo(page)

        // Load the autofill.js script with replacements
        // on iOS it's the user-agent that's used as the platform check
        await createAutofillScript()
            .replaceAll(iosContentScopeReplacements())
            .platform('ios')
            .applyTo(page)

        const {privateAddress0} = constants.fields.email

        // page abstraction
        const emailPage = emailAutofillPage(page, server)
        await emailPage.navigate()

        // Click in the input, a native window will appear
        // and the mocks above will ensure the message is responded to -
        // - this simulates the user tapping an option in the native window
        await emailPage.clickIntoInput()

        // Because of the mock above, assume an email was selected and ensure it's auto-filled
        await emailPage.assertEmailValue(privateAddress0)
    })
    test('autofill a login form', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        const {personalAddress} = constants.fields.email
        const password = '123456'

        await createWebkitMocks()
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
            .replaceAll(iosContentScopeReplacements())
            .platform('ios')
            .applyTo(page)

        const login = loginPage(page, server)
        await login.navigate()
        await login.clickIntoUsernameInput()
        await login.assertFirstCredential(personalAddress, password)
    })
})
