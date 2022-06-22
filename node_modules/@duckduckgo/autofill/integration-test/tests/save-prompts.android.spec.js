import {
    createAutofillScript,
    forwardConsoleMessages,
    setupServer, withAndroidContext
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {loginPage, signupPage} from '../helpers/pages.js'
import {androidStringReplacements, createAndroidMocks} from '../helpers/mocks.android.js'
import {constants} from '../helpers/mocks.js'

/**
 *  Tests for email autofill on ios tooltipHandler
 */
const test = withAndroidContext(base)

test.describe('Android Save prompts', () => {
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

            await createAndroidMocks().applyTo(page)

            await createAutofillScript()
                .replaceAll(androidStringReplacements({
                    featureToggles: {
                        credentials_saving: true
                    }
                }))
                .platform('android')
                .applyTo(page)

            await signup.enterCredentials(credentials)
            await signup.assertWasPromptedToSave(credentials, 'android')
        })
        test.describe('Prompting to save from a login form', () => {
            /** @param {import("playwright").Page} page */
            async function setup (page) {
                await forwardConsoleMessages(page)
                const login = loginPage(page, server)
                await login.navigate()

                await createAndroidMocks()
                    .applyTo(page)
                await createAutofillScript()
                    .replaceAll(androidStringReplacements({
                        featureToggles: {
                            credentials_saving: true
                        }
                    }))
                    .platform('android')
                    .applyTo(page)
                return { login }
            }
            test('with username+password (should prompt)', async ({page}) => {
                const { login } = await setup(page)
                const credentials = {
                    username: 'dax@wearejh.com',
                    password: '123456'
                }
                await login.submitLoginForm(credentials)
                await login.assertWasPromptedToSave(credentials, 'android')
            })
            test('with password only (should prompt)', async ({page}) => {
                const { login } = await setup(page)
                const credentials = { password: '123456' }
                await login.submitPasswordOnlyForm(credentials)
                await login.assertWasPromptedToSave(credentials, 'android')
            })
            test('with username only (should NOT prompt)', async ({page}) => {
                const { login } = await setup(page)
                const credentials = { username: '123456' }
                await login.submitUsernameOnlyForm(credentials.username)
                await login.promptWasNotShown()
            })
        })
    })
    test.describe('When saving credentials is disabled ❌', () => {
        test('should not prompt to save', async ({page}) => {
            await forwardConsoleMessages(page)

            const login = loginPage(page, server)
            await login.navigate()

            /** @type {Partial<import('../../src/deviceApiCalls/__generated__/validators-ts').AutofillFeatureToggles>} */
            const toggles = {
                credentials_saving: false
            }

            await createAndroidMocks()
                .applyTo(page)

            // create + inject the script
            await createAutofillScript()
                .replaceAll(androidStringReplacements({
                    featureToggles: toggles
                }))
                .platform('android')
                .applyTo(page)

            const credentials = {
                username: 'dax@wearejh.com',
                password: '123456'
            }

            await login.submitLoginForm(credentials)
            await login.promptWasNotShown()
        })
    })
})
