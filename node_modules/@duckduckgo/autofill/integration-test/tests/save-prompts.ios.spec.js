import {
    forwardConsoleMessages,
    setupServer,
    withIOSContext, withIOSFeatureToggles
} from '../helpers/harness.js'
import {test as base} from '@playwright/test'
import {loginPage, loginPageWithPoorForm, signupPage} from '../helpers/pages.js'
import {createWebkitMocks} from '../helpers/mocks.webkit.js'
import {constants} from '../helpers/mocks.js'

/**
 *  Tests for email autofill on ios tooltipHandler
 */
const test = withIOSContext(base)

test.describe('iOS Save prompts', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test.describe('and saving credentials disabled ❌', () => {
        test('should not prompt to save', async ({page}) => {
            await forwardConsoleMessages(page)
            await createWebkitMocks().applyTo(page)

            /** @type {Partial<import('../../src/deviceApiCalls/__generated__/validators-ts.js').AutofillFeatureToggles>} */
            const toggles = {
                credentials_saving: false
            }

            await withIOSFeatureToggles(page, toggles)

            const login = loginPage(page, server)
            await login.navigate()

            const credentials = {
                username: 'dax@wearejh.com',
                password: '123456'
            }
            await login.submitLoginForm(credentials)
            await login.shouldNotPromptToSave()

            /**
             * NOTE: This is here as a sanity check because this test is a negative check
             * and there are lots of ways this test could pass, but be broken.
             *
             * For example if our script fails to load, then this test would normally pass,
             * but by having this mock check here, it confirms that scripts ran, messages
             * were sent etc.
             */
            await login.assertMockCallOccurred('getAvailableInputTypes')
        })
    })
    test.describe('When saving credentials is enabled ✅', () => {
        test('Prompting to save from a signup form', async ({page}) => {
            // enable in-terminal exceptions
            await forwardConsoleMessages(page)

            const {personalAddress} = constants.fields.email

            const credentials = {
                username: personalAddress,
                password: '123456'
            }

            await createWebkitMocks().applyTo(page)

            await withIOSFeatureToggles(page, {
                emailProtection: true,
                inputType_credentials: true,
                credentials_saving: true,
                inputType_identities: false,
                inputType_creditCards: false,
                password_generation: false
            })

            const signup = signupPage(page, server)
            await signup.navigate()
            await signup.enterCredentials(credentials)
            await signup.assertWasPromptedToSave(credentials, 'ios')
        })
        test.describe('Prompting to save from a login form', () => {
            /**
             * @param {import("playwright").Page} page
             */
            async function setup (page) {
                await forwardConsoleMessages(page)
                await createWebkitMocks().applyTo(page)
                await withIOSFeatureToggles(page, {
                    credentials_saving: true
                })
                const login = loginPage(page, server)
                await login.navigate()
                return login
            }

            test('username+password (should prompt)', async ({page}) => {
                const login = await setup(page)

                const credentials = {
                    username: 'dax@wearejh.com',
                    password: '123456'
                }
                await login.submitLoginForm(credentials)
                await login.assertWasPromptedToSave(credentials)
            })
            test('password only (should prompt)', async ({page}) => {
                const login = await setup(page)

                const credentials = {password: '123456'}
                await login.submitPasswordOnlyForm(credentials)
                await login.assertWasPromptedToSave(credentials)
            })

            test('username only (should NOT prompt)', async ({page}) => {
                const login = await setup(page)

                const credentials = {username: '123456'}
                await login.submitUsernameOnlyForm(credentials.username)
                await login.shouldNotPromptToSave()
            })
        })

        test.describe('Prompting to save from a poor login form (using Enter and click on a button outside the form)', () => {
            const credentials = {
                username: 'dax@wearejh.com',
                password: '123456'
            }
            /**
             * @param {import("playwright").Page} page
             */
            async function setup (page) {
                await forwardConsoleMessages(page)
                await createWebkitMocks().applyTo(page)
                await withIOSFeatureToggles(page, {
                    credentials_saving: true
                })
                const login = loginPageWithPoorForm(page, server)
                await login.navigate()

                await page.type('#password', credentials.password)
                await page.type('#email', credentials.username)

                // Check that we haven't detected any submission at this point
                await login.shouldNotPromptToSave()

                return login
            }

            test('submit by clicking on the out-of-form button', async ({page}) => {
                const login = await setup(page)

                await page.click('"Log in"')
                await login.assertWasPromptedToSave(credentials)
            })
            test('should not prompt if the out-of-form button does not match the form type', async ({page}) => {
                const login = await setup(page)

                await page.click('"Sign up"')
                await login.shouldNotPromptToSave()
            })
            test('should prompt when hitting enter while an input is focused', async ({page}) => {
                const login = await setup(page)

                await page.press('#email', 'Tab')
                await login.shouldNotPromptToSave()

                await page.press('#password', 'Enter')
                await login.assertWasPromptedToSave(credentials)
            })
        })
    })
})
