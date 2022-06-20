import HTMLTooltip, {defaultOptions} from './HTMLTooltip'
import {getInputConfigFromType} from '../Form/inputTypeConfig'

describe('HTMLTooltip', () => {
    let o1 = global.ResizeObserver
    let o2 = global.MutationObserver
    beforeEach(() => {
        // @ts-ignore
        global.ResizeObserver = class ResizeObserver {
            observe () {}
        }
        // @ts-ignore
        global.MutationObserver = class MutationObserver {
            observe () {}
        }
    })
    afterEach(() => {
        global.ResizeObserver = o1
        global.MutationObserver = o2
    })
    /**
     * This test ensures that `setupSizeListener` is not called if the platform
     * in question cannot use PerformanceObserver with {entryTypes: ['layout-shift', 'paint']}
     *
     * A bug occurred because those parameters are not valid for PerformanceObserver on macOS Catalina
     *  - so this test ensures that `setupSizeListener` is NOT called when default options are used.
     *
     * on macOS/Windows when operating in a top-frame scenario, those both **can** support the arguments
     * above so they will each provide their own callback for setSize.
     *
     * @link {https://app.asana.com/0/1177771139624306/1202412384393015/f}
     */
    it('works with default values', () => {
        const config = getInputConfigFromType('credentials')
        const tooltip = new HTMLTooltip(config, 'credentials.username', () => {}, defaultOptions)
        const spy = jest.spyOn(tooltip, 'setupSizeListener')
        tooltip.init()
        expect(spy).not.toHaveBeenCalled()
    })
})
