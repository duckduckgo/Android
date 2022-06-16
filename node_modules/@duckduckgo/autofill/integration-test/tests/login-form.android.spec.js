import {constants} from '../helpers/mocks.js'
import {createAutofillScript, forwardConsoleMessages, setupServer, withAndroidContext} from '../helpers/harness.js'
import {loginPage} from '../helpers/pages.js'
import {androidStringReplacements, createAndroidMocks} from '../helpers/mocks.android.js'
import {test as base} from '@playwright/test'

/**
 * @typedef {import('../../src/deviceApiCalls/__generated__/validators-ts').GetAutofillDataResponse} GetAutofillDataResponse
 * @typedef {import('../../src/deviceApiCalls/__generated__/validators-ts').AutofillFeatureToggles} AutofillFeatureToggles
 * @typedef {import('../../src/deviceApiCalls/__generated__/validators-ts').AvailableInputTypes} AvailableInputTypes
 */

/**
 *  Tests for email autofill on android tooltipHandler
 */
const test = withAndroidContext(base)

/**
 * @param {import('playwright').Page} page
 * @param {ServerWrapper} server
 * @param {object} opts
 * @param {Partial<AutofillFeatureToggles>} opts.featureToggles
 * @param {Partial<AvailableInputTypes>} opts.availableInputTypes
 * @param {CredentialsMock} [opts.credentials]
 */
async function testLoginPage (page, server, opts) {
    // enable in-terminal exceptions
    await forwardConsoleMessages(page)

    const login = loginPage(page, server)
    await login.navigate()

    // android specific mocks
    const mocks = createAndroidMocks()

    if (opts.credentials) {
        mocks.withCredentials(opts.credentials)
    }

    await mocks.applyTo(page)

    // create + inject the script
    await createAutofillScript()
        .replaceAll(androidStringReplacements({
            featureToggles: opts.featureToggles,
            availableInputTypes: opts.availableInputTypes
        }))
        .platform('android')
        .applyTo(page)

    await login.clickIntoUsernameInput()
    return {login}
}

test.describe('Feature: auto-filling a login forms on Android', () => {
    const {personalAddress} = constants.fields.email
    const password = '123456'
    const credentials = {
        id: '01',
        username: personalAddress,
        password
    }
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test.describe('when `inputType_credentials` is true', () => {
        test.describe('and I have saved credentials', () => {
            test('I should be prompted to use my saved credentials', async ({page}) => {
                const {login} = await testLoginPage(page, server, {
                    featureToggles: {
                        inputType_credentials: true
                    },
                    availableInputTypes: {
                        credentials: true
                    },
                    credentials
                })
                await login.promptWasShown()
                await login.assertFirstCredential(personalAddress, password)
            })
        })
        test.describe('but I dont have saved credentials', () => {
            test('I should not be prompted', async ({page}) => {
                const {login} = await testLoginPage(page, server, {
                    featureToggles: {
                        inputType_credentials: true
                    },
                    availableInputTypes: {}
                })
                await login.promptWasNotShown()
            })
        })
    })
    test.describe('when `inputType_credentials` is false', () => {
        test('I should not be prompted at all', async ({page}) => {
            const {login} = await testLoginPage(page, server, {
                featureToggles: {
                    inputType_credentials: false
                },
                availableInputTypes: {
                    credentials: true
                },
                credentials
            })
            await login.promptWasNotShown()
        })
    })
})
