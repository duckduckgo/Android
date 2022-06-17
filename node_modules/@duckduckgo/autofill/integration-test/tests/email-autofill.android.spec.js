import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer,
    withAndroidContext
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {emailAutofillPage, loginPage} from '../helpers/pages.js'
import {createAndroidMocks} from '../helpers/mocks.android.js'

/**
 *  Tests for email autofill on android tooltipHandler
 */
const test = withAndroidContext(base)

test.describe('android', () => {
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

        const {personalAddress} = constants.fields.email

        // page abstraction
        const emailPage = emailAutofillPage(page, server)
        await emailPage.navigate()

        // android specific mocks
        await createAndroidMocks()
            .withPersonalEmail(personalAddress)
            .withPrivateEmail(personalAddress)
            .withAvailableInputTypes({
                credentials: true,
                email: true
            })
            .applyTo(page)

        // create + inject the script
        await createAutofillScript()
            .platform('android')
            .applyTo(page)

        // if this works, the tooltipHandler must have loaded and added the field decorations
        await emailPage.clickIntoInput()

        // Because of the mock above, assume an email was selected and ensure it's autofilled
        await emailPage.assertEmailValue(personalAddress)
    })
    test('autofill a login form', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        const {personalAddress} = constants.fields.email
        const password = '123456'

        const login = loginPage(page, server)
        await login.navigate()

        // android specific mocks
        await createAndroidMocks()
            .withCredentials({
                id: '01',
                username: personalAddress,
                password
            })
            .withAvailableInputTypes({
                credentials: true
            })
            .applyTo(page)

        // create + inject the script
        await createAutofillScript()
            .platform('android')
            .applyTo(page)

        await login.clickIntoUsernameInput()
        await login.assertAndroidSentJsonString()
        await login.assertFirstCredential(personalAddress, password)
    })
})
