/**
 *  Tests for fingerprint defenses. Ensure that fingerprinting is actually being blocked.
 */

import { setup } from './helpers/harness.js'

const expectedFingerprintValues = {
    availTop: 0,
    availLeft: 0,
    wAvailTop: 0,
    wAvailLeft: 0,
    colorDepth: 24,
    pixelDepth: 24,
    productSub: '20030107',
    vendorSub: ''
}

const pagePath = '/index.html'
const tests = [
    { url: `localhost:8080${pagePath}` },
    { url: `127.0.0.1:8383${pagePath}` }
]

function testFPValues (values) {
    for (const [name, prop] of Object.entries(values)) {
        expect(prop).withContext(`${name}`).toEqual(expectedFingerprintValues[name])
    }
}

describe('Fingerprint Defense Tests', () => {
    let browser
    let teardown
    let setupServer
    beforeAll(async () => {
        ({ browser, teardown, setupServer } = await setup())

        setupServer('8080')
        setupServer('8383')
    })
    afterAll(async () => {
        await teardown()
    })

    for (const test of tests) {
        it(`${test.url} should include anti-fingerprinting code`, async () => {
            const page = await browser.newPage()

            try {
                await page.goto(`http://${test.url}`)
            } catch (e) {
                // timed out waiting for page to load, let's try running the test anyway
            }
            const values = await page.evaluate(() => {
                return {
                    availTop: screen.availTop,
                    availLeft: screen.availLeft,
                    wAvailTop: window.screen.availTop,
                    wAvailLeft: window.screen.availLeft,
                    colorDepth: screen.colorDepth,
                    pixelDepth: screen.pixelDepth,
                    productSub: navigator.productSub,
                    vendorSub: navigator.vendorSub
                }
            })
            testFPValues(values)

            await page.close()
        })
    }
})

describe('First Party Fingerprint Randomization', () => {
    let browser
    let teardown
    let setupServer
    beforeAll(async () => {
        ({ browser, setupServer, teardown } = await setup())

        setupServer('8080')
        setupServer('8383')
    })
    afterAll(async () => {
        await teardown()
    })

    async function runTest (test) {
        const page = await browser.newPage()

        try {
            await page.goto(`http://${test.url}`)
        } catch (e) {
            // timed out waiting for page to load, let's try running the test anyway
        }

        await page.addScriptTag({ path: 'node_modules/@fingerprintjs/fingerprintjs/dist/fp.js' })

        const fingerprint = await page.evaluate(() => {
            /* global FingerprintJS */
            return (async () => {
                const fp = await FingerprintJS.load()
                return fp.get()
            })()
        })

        await page.close()

        return {
            canvas: fingerprint.components.canvas.value,
            plugin: fingerprint.components.plugins.value
        }
    }

    for (const testCase of tests) {
        it('Fingerprints should not change amongst page loads', async () => {
            const result = await runTest(testCase)

            const result2 = await runTest(testCase)
            expect(result.canvas).toEqual(result2.canvas)
            expect(result.plugin).toEqual(result2.plugin)
        })
    }

    it('Fingerprints should not match across first parties', async () => {
        const canvas = new Set()
        const plugin = new Set()

        for (const testCase of tests) {
            const result = await runTest(testCase)

            // Add the fingerprints to a set, if the result doesn't match it won't be added
            canvas.add(JSON.stringify(result.canvas))
            plugin.add(JSON.stringify(result.plugin))
        }

        // Ensure that the number of test pages match the number in the set
        expect(canvas.size).toEqual(tests.length)
        expect(plugin.size).toEqual(1)
    })
})

describe('Verify injected script is not visible to the page', () => {
    let browser
    let teardown
    let setupServer
    beforeAll(async () => {
        ({ browser, setupServer, teardown } = await setup())

        setupServer('8080')
        setupServer('8383')
    })
    afterAll(async () => {
        await teardown()
    })

    tests.forEach(test => {
        it('Fingerprints should not match across first parties', async () => {
            const page = await browser.newPage(pagePath)

            try {
                await page.goto(`http://${test.url}`)
            } catch (e) {
                // timed out waiting for page to load, let's try running the test anyway
            }

            // give it another second just to be sure
            await page.waitForTimeout(1000)

            const sjclVal = await page.evaluate(() => {
                if ('sjcl' in window) {
                    return 'visible'
                } else {
                    return 'invisible'
                }
            })

            await page.close()

            expect(sjclVal).toEqual('invisible')
        })
    })
})
