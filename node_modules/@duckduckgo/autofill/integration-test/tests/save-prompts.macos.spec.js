import {createAutofillScript, defaultMacosScript, forwardConsoleMessages, setupServer} from '../helpers/harness.js'
import {constants} from '../helpers/mocks.js'
import {createWebkitMocks, macosContentScopeReplacements} from '../helpers/mocks.webkit.js'
import {loginPage, signupPage} from '../helpers/pages.js'
import {test as base} from '@playwright/test'

/**
 *  Tests for various auto-fill scenarios on macos
 */
const test = base.extend({})

test.describe('macos', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test.describe('prompting to save data', () => {
        test('Prompting to save from a signup form', async ({page}) => {
            // enable in-terminal exceptions
            await forwardConsoleMessages(page)

            const {personalAddress} = constants.fields.email

            const credentials = {
                username: personalAddress,
                password: '123456'
            }

            await createWebkitMocks()
                .applyTo(page)

            // Load the autofill.js script with replacements
            await createAutofillScript()
                .replaceAll(macosContentScopeReplacements())
                .platform('macos')
                .applyTo(page)

            const signup = signupPage(page, server)
            await signup.navigate()
            await signup.enterCredentials(credentials)
            await signup.assertWasPromptedToSave(credentials, 'macos')
        })
        test.describe('Prompting to save from a login form', () => {
            test('username+password (should prompt)', async ({page}) => {
                // enable in-terminal exceptions
                await forwardConsoleMessages(page)

                const credentials = {
                    username: 'dax@wearejh.com',
                    password: '123456'
                }

                await createWebkitMocks().applyTo(page)
                await defaultMacosScript(page)

                const login = loginPage(page, server)
                await login.navigate()
                await login.submitLoginForm(credentials)
                await login.assertWasPromptedToSave(credentials)
            })
            test('password only (should prompt)', async ({page}) => {
                // enable in-terminal exceptions
                await forwardConsoleMessages(page)
                await createWebkitMocks().applyTo(page)
                await defaultMacosScript(page)

                const login = loginPage(page, server)

                const credentials = { password: '123456' }
                await login.navigate()
                await login.submitPasswordOnlyForm(credentials)
                await login.assertWasPromptedToSave(credentials)
            })
            test('username only (should NOT prompt)', async ({page}) => {
                // enable in-terminal exceptions
                await forwardConsoleMessages(page)

                const credentials = { username: '123456' }

                await createWebkitMocks().applyTo(page)
                await defaultMacosScript(page)

                const login = loginPage(page, server)
                await login.navigate()
                await login.submitUsernameOnlyForm(credentials.username)
                await login.shouldNotPromptToSave()
            })
        })
    })
})
