import InterfacePrototype from './InterfacePrototype'
import {CSS_STYLES} from '../UI/styles/styles'
import {SelectedDetailMessage} from '../messages/messages'

class WindowsInterface extends InterfacePrototype {
    async setupAutofill () {
        // todo(Shane): is this is the correct place to determine this?
        const initData = await this.getAutofillInitData()
        this.storeLocalData(initData)
        const cleanup = this.scanner.init()
        this.addLogoutListener(cleanup)
    }
    tooltipStyles () {
        return `<style>${CSS_STYLES}</style>`
    }
}

export { WindowsInterface }

/**
 * todo(Shane): Decide which data is/isn't needed when apple is inside overlay
 */
class WindowsOverlayDeviceInterface extends InterfacePrototype {
    stripCredentials = false;

    async setupAutofill () {
        const response = await this.getAutofillInitData()
        this.storeLocalData(response)
        await this._setupTopFrame()
    }

    async _setupTopFrame () {
        const topContextData = this.getTopContextData()
        if (!topContextData) throw new Error('unreachable, topContextData should be available')

        // Provide dummy values, they're not used
        // todo(Shane): Is this truly not used?
        const getPosition = () => {
            return {
                x: 0,
                y: 0,
                height: 50,
                width: 50
            }
        }

        // this is the apple specific part about faking the focus etc.
        // todo(Shane): The fact this 'addListener' could be undefined is a design problem
        this.tooltip.addListener?.(() => {
            const handler = (event) => {
                const tooltip = this.tooltip.getActiveTooltip?.()
                tooltip?.focus(event.detail.x, event.detail.y)
            }
            window.addEventListener('mouseMove', handler)
            return () => {
                window.removeEventListener('mouseMove', handler)
            }
        })
        const tooltip = this.tooltip.createTooltip?.(getPosition, topContextData)
        this.setActiveTooltip(tooltip)
    }

    async setSize (_cb) {
        // const details = cb()
        // todo(Shane): Upgrade to new runtime
        // await this.sender.send(createLegacyMessage('setSize', details))
    }

    async removeTooltip () {
        console.warn('no-op in overlay')
    }

    // Used to encode data to send back to the child autofill
    async selectedDetail (detailIn, configType) {
        let detailsEntries = Object.entries(detailIn).map(([key, value]) => {
            return [key, String(value)]
        })
        const data = Object.fromEntries(detailsEntries)
        await this.sender.send(new SelectedDetailMessage({data, configType}))
    }

    async getCurrentInputType () {
        const {inputType} = this.getTopContextData() || {}
        return inputType || 'unknown'
    }

    tooltipStyles () {
        return `<style>${CSS_STYLES}</style>`
    }

    tooltipWrapperClass () {
        return 'top-autofill'
    }

    tooltipPositionClass (_top, _left) {
        return '.wrapper {transform: none; }'
    }

    setupSizeListener (cb) {
        cb()
    }
}

export { WindowsOverlayDeviceInterface }
