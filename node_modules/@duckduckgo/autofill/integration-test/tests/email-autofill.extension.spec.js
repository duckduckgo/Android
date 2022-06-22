import {forwardConsoleMessages, setupServer, withChromeExtensionContext} from '../helpers/harness.js'
import { test as base, expect } from '@playwright/test'
import {constants} from '../helpers/mocks.js'
import {emailAutofillPage} from '../helpers/pages.js'

/**
 *  Tests for email autofill in chrome extension.
 *
 *  Special setup is needed to load the extension, see withChromeExtensionContext();
 */
const test = withChromeExtensionContext(base)

test.describe('chrome extension', () => {
    let server
    test.beforeAll(async () => {
        server = setupServer()
    })
    test.afterAll(async () => {
        server.close()
    })
    test('should autofill the selected email', async ({page}) => {
        const {personalAddress, privateAddress0} = constants.fields.email

        forwardConsoleMessages(page)

        // page abstraction
        const emailPage = emailAutofillPage(page, server)
        await emailPage.navigate()
        await emailPage.clickIntoInput()

        // buttons, unique to the extension
        const personalAddressBtn = await page.locator(`text=Use ${personalAddress} Blocks email trackers`)
        const privateAddressBtn = await page.locator(`text=Use a Private Address Blocks email trackers and hides your address`)

        // click first option
        await personalAddressBtn.click({timeout: 500})

        // ensure autofill populates the field
        await emailPage.assertEmailValue(personalAddress)

        // now ensure a second click into the input doesn't show the dropdown
        await emailPage.clickIntoInput()

        // ensure the popup does not show
        await expect(personalAddressBtn).not.toBeVisible()

        // now directly on Dax
        await emailPage.clickDirectlyOnDax()

        // and then click the second button this time
        await privateAddressBtn.click()

        // now ensure the second value is the private address
        await emailPage.assertEmailValue(privateAddress0)
    })
})
