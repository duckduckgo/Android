import {
    createAutofillScript,
    forwardConsoleMessages, performanceEntries,
    setupServer
} from '../helpers/harness.js'
import {test as base, expect} from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {emailAutofillPage, signupPage} from '../helpers/pages.js'
import {createWebkitMocks, macosContentScopeReplacements} from '../helpers/mocks.webkit.js'

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
    test('should autofill the selected email', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        await createWebkitMocks()
            .withPrivateEmail('0')
            .withPersonalEmail('shane-123')
            .applyTo(page)

        // Load the autofill.js script with replacements
        await createAutofillScript()
            .replaceAll(macosContentScopeReplacements())
            .platform('macos')
            .applyTo(page)

        const {personalAddress, privateAddress0} = constants.fields.email

        // page abstraction
        const emailPage = emailAutofillPage(page, server)
        await emailPage.navigate()

        // first click into the field
        await emailPage.clickIntoInput()

        // these are mac specific - different to the extension because they use different tooltips (currently)
        const personalAddressBtn = await page.locator(`button:has-text("${personalAddress} Blocks email trackers")`)
        const privateAddressBtn = await page.locator(`button:has-text("Generated Private Duck Address 0@duck.com")`)

        // select the first option
        await expect(personalAddressBtn).toBeVisible()
        await personalAddressBtn.click({force: true})

        // ensure autofill populates the field
        await emailPage.assertEmailValue(personalAddress)

        // ensure the popup DOES show a second time, even though Dax was not clicked (this is mac specific)
        await emailPage.clickIntoInput()
        await expect(personalAddressBtn).toBeVisible()

        // now select the second address this time...
        await privateAddressBtn.click({force: true})

        // ...and ensure the second value is the private address
        await emailPage.assertEmailValue(privateAddress0)
    })
    test.describe('auto filling a signup form', () => {
        async function applyScript (page) {
            await createAutofillScript()
                .replaceAll(macosContentScopeReplacements())
                .platform('macos')
                .applyTo(page)
        }

        const {personalAddress} = constants.fields.email
        let identity = {
            id: '01',
            title: 'Main identity',
            firstName: 'shane',
            emailAddress: personalAddress
        }
        test('with an identity only', async ({page}) => {
            await forwardConsoleMessages(page)
            const signup = signupPage(page, server)

            await createWebkitMocks()
                .withIdentity(identity)
                .applyTo(page)

            await applyScript(page)

            await signup.navigate()
            await signup.assertEmailHasNoDaxIcon()
            await signup.selectGeneratedPassword()
            await signup.selectFirstName('shane Main identity')
            await signup.assertEmailValue(identity.emailAddress)
        })
        test('with no input types', async ({page}) => {
            await forwardConsoleMessages(page)
            const signup = signupPage(page, server)
            await createWebkitMocks().applyTo(page)
            await applyScript(page)
            await signup.navigate()

            // should still allow password generation
            await signup.selectGeneratedPassword()
        })
    })
    test('autofill a newly added email form (mutation observer test)', async ({page}) => {
        // enable in-terminal exceptions
        await forwardConsoleMessages(page)

        const {personalAddress} = constants.fields.email

        await createWebkitMocks()
            .withPrivateEmail('0')
            .withPersonalEmail('shane-123')
            .withIdentity({
                id: '01',
                title: 'Main identity',
                firstName: 'shane',
                emailAddress: personalAddress
            })
            .applyTo(page)

        // Load the autofill.js script with replacements
        await createAutofillScript()
            .replaceAll(macosContentScopeReplacements())
            .platform('macos')
            .applyTo(page)

        const signup = signupPage(page, server)
        await signup.navigate()
        await signup.addNewForm()
        await signup.selectSecondEmailField(`${personalAddress} Main identity`)
        await signup.assertSecondEmailValue(personalAddress)
        await signup.assertFirstEmailEmpty()
    })
    test.describe('matching performance', () => {
        test('matching performance v1', async ({page}) => {
            await forwardConsoleMessages(page)
            await createWebkitMocks().applyTo(page)
            await createAutofillScript()
                .replaceAll(macosContentScopeReplacements())
                .platform('macos')
                .applyTo(page)

            await page.goto(server.urlForPath('src/Form/test-cases/usps_signup.html'))
            const r = await performanceEntries(page, 'scanner:init')
            for (let performanceEntry of r) {
                console.log(performanceEntry.duration)
            }
        })
    })
})
