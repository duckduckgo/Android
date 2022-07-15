/* global page */
import { webkit } from 'playwright-webkit'
import { toMatchImageSnapshot } from 'jest-image-snapshot'
import { getContentHeight } from '../shared/js/browser/common.es6'

expect.extend({ toMatchImageSnapshot })

const defaultPageWidth = 350
const defaultPageHeight = 550
let browser
let context

function setupPage () {
    beforeAll(async () => {
        browser = await webkit.launch()
    })

    afterAll(async () => {
        await browser.close()
    })

    beforeEach(async () => {
        context = await browser.newContext()
        global.page = await context.newPage({
            viewport: {
                width: defaultPageWidth,
                height: defaultPageHeight
            }
        })
    })

    afterEach(async () => {
        await context.close()
    })

    // ---

    const goTo = (pageName) =>
        page.goto(`http://localhost:8080/html/${pageName}.html`)

    const wait = (timeout) =>
        new Promise((resolve) => setTimeout(resolve, timeout))

    const setupColorScheme = async (colorScheme) => {
        await page.emulateMedia({ colorScheme })
    }

    const setupPageData = async (arg) => {
        await context.addInitScript((data) => {
            window.__DDG_TEST_DATA = data
        }, arg)
    }

    const takeScreenshot = async () => {
        const height = await page.evaluate(getContentHeight)
        await page.setViewportSize({
            width: defaultPageWidth,
            height: height || defaultPageHeight
        })
        return await page.screenshot()
    }

    const clickTrackerListAction = async () => {
        await page.click('.site-info__li--trackers a')
        await wait(350) // Wait for animation
    }

    const clickConnectionAction = async () => {
        await page.click('.site-info__li--https-status a')
        await wait(350) // Wait for animation
    }

    const clickBrokenSiteAction = async () => {
        await page.click('.site-info__report-broken')
        await wait(350) // Wait for animation
    }

    return {
        goTo,
        wait,
        setupColorScheme,
        setupPageData,
        takeScreenshot,
        clickTrackerListAction,
        clickConnectionAction,
        clickBrokenSiteAction
    }
}

export default setupPage
