import {AppleDeviceInterface} from './AppleDeviceInterface'
import {CSS_STYLES} from '../UI/styles/styles'
import {HTMLTooltipUIController} from '../UI/controllers/HTMLTooltipUIController'
import {createNotification} from '../../packages/device-api'

/**
 * This subclass is designed to separate code that *only* runs inside the
 * Overlay into a single place.
 *
 * It will only run inside the macOS overlay, therefor all code here
 * can be viewed as *not* executing within a regular page context.
 */
class AppleOverlayDeviceInterface extends AppleDeviceInterface {
    /**
     * Mark top frame as not stripping credential data
     * @type {boolean}
     */
    stripCredentials = false;

    /**
     * Because we're running inside the Overlay, we always create the HTML
     * Tooltip controller.
     *
     * @override
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController () {
        /** @type {import('../UI/controllers/HTMLTooltipUIController').HTMLTooltipControllerOptions} */
        const controllerOptions = {
            tooltipKind: /** @type {const} */ ('modern'),
            device: this
        }
        /** @type {import('../UI/HTMLTooltip').HTMLTooltipOptions} */
        const tooltipOptions = {
            wrapperClass: 'top-autofill',
            tooltipPositionClass: () => '.wrapper { transform: none; }',
            css: `<style>${CSS_STYLES}</style>`,
            setSize: (details) => this._setSize(details),
            testMode: this.isTestMode(),
            remove: () => {
                /** noop - the overlay does not close itself */
            }
        }
        return new HTMLTooltipUIController(controllerOptions, tooltipOptions)
    }

    /**
     * Since we're running inside the Overlay we can limit what happens here to
     * be only things that are needed to power the HTML Tooltip
     *
     * @override
     * @returns {Promise<void>}
     */
    async setupAutofill () {
        await this._getAutofillInitData()
        const signedIn = await this._checkDeviceSignedIn()

        if (signedIn) {
            await this.getAddresses()
        }

        this._setupTopFrame()
        this._listenForCustomMouseEvent()
    }

    _setupTopFrame () {
        const topContextData = this.getTopContextData()
        if (!topContextData) throw new Error('unreachable, topContextData should be available')

        // Provide dummy values, they're not used
        const getPosition = () => {
            return {
                x: 0,
                y: 0,
                height: 50,
                width: 50
            }
        }

        // Create the tooltip, and set it as active
        const tooltip = this.uiController.createTooltip?.(getPosition, topContextData)
        if (tooltip) {
            this.uiController.setActiveTooltip?.(tooltip)
        }
    }

    /**
     * The native side will send a custom event 'mouseMove' to indicate
     * that the HTMLTooltip should fake an element being focussed.
     *
     * Note: There's no cleanup required here since the Overlay has a fresh
     * page load every time it's opened.
     */
    _listenForCustomMouseEvent () {
        window.addEventListener('mouseMove', (event) => {
            const activeTooltip = this.uiController.getActiveTooltip?.()
            activeTooltip?.focus(event.detail.x, event.detail.y)
        })
    }

    /**
     * This is overridden in the Overlay, so that instead of trying to fill a form
     * with the selected credentials, we instead send a message to the native
     * side. Once received, the native side will store that selection so that a
     * subsequence call from main webpage can retrieve it via polling.
     *
     * @override
     * @param detailIn
     * @param configType
     * @returns {Promise<void>}
     */
    async selectedDetail (detailIn, configType) {
        let detailsEntries = Object.entries(detailIn).map(([key, value]) => {
            return [key, String(value)]
        })
        const data = Object.fromEntries(detailsEntries)
        await this.deviceApi.notify(createNotification('selectedDetail', { data, configType }))
    }

    /**
     * When the HTMLTooltip calls 'setSize', we forward that message to the native layer
     * so that the window that contains the Autofill UI can be set correctly.
     *
     * This is an overlay-only scenario - normally 'setSize' isn't needed (like in the extension)
     * because the HTML element will grow as needed.
     *
     * @param {{height: number, width: number}} details
     */
    async _setSize (details) {
        await this.deviceApi.notify(createNotification('setSize', details))
    }
}

export { AppleOverlayDeviceInterface }
