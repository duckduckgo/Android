import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer,
    withAndroidContext
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {emailAutofillPage, signupPage} from '../helpers/pages.js'
import {androidStringReplacements, createAndroidMocks} from '../helpers/mocks.android.js'

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
    test.describe('when signed in', () => {
        test('should autofill the selected email', async ({page}) => {
            // enable in-terminal exceptions
            await forwardConsoleMessages(page)
            const emailPage = emailAutofillPage(page, server)
            await emailPage.navigate()

            // android specific mocks, withPersonalEmail will ensure the signed-in check works
            const {personalAddress} = constants.fields.email
            await createAndroidMocks()
                .withPersonalEmail(personalAddress)
                .withPrivateEmail(personalAddress)
                .applyTo(page)

            // create + inject the script
            await createAutofillScript()
                .replaceAll(androidStringReplacements())
                .platform('android')
                .applyTo(page)

            // if this works, the tooltipHandler must have loaded and added the field decorations
            await emailPage.clickIntoInput()

            // Because of the mock above, assume an email was selected and ensure it's autofilled
            await emailPage.assertEmailValue(personalAddress)
        })
    })
    test.describe('when availableInputTypes are available', () => {
        test('should use availableInputTypes.email', async ({page}) => {
            await forwardConsoleMessages(page)
            const emailPage = emailAutofillPage(page, server)
            await emailPage.navigate()
            const {personalAddress} = constants.fields.email
            await createAndroidMocks()
                .withPersonalEmail(personalAddress)
                .withPrivateEmail(personalAddress)
                .applyTo(page)

            // create + inject the script
            await createAutofillScript()
                .replaceAll(androidStringReplacements({
                    availableInputTypes: {
                        email: true
                    }
                }))
                .platform('android')
                .applyTo(page)

            await emailPage.clickIntoInput()
        })
    })
    test.describe('when not signed in', () => {
        test('should not decorate with Dax icon', async ({page}) => {
            // enable in-terminal exceptions
            await forwardConsoleMessages(page)

            // page abstraction
            const signup = signupPage(page, server)
            await signup.navigate()

            // android specific mocks
            await createAndroidMocks()
                .applyTo(page)

            // create + inject the script
            await createAutofillScript()
                .replaceAll(androidStringReplacements({
                    availableInputTypes: {
                        email: false
                    }
                }))
                .platform('android')
                .applyTo(page)

            await signup.assertEmailHasNoDaxIcon()
        })
    })
})
