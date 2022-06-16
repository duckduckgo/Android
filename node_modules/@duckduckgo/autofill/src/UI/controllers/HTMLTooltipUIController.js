import {getInputConfigFromType} from '../../Form/inputTypeConfig'
import DataHTMLTooltip from '../DataHTMLTooltip'
import EmailHTMLTooltip from '../EmailHTMLTooltip'
import {defaultOptions} from '../HTMLTooltip'
import {UIController} from './UIController'

/**
 * @typedef HTMLTooltipControllerOptions
 * @property {"modern" | "legacy"} tooltipKind - A choice between the newer Autofill UI vs the older one used in the extension
 * @property {import("../../DeviceInterface/InterfacePrototype").default} device - The device interface that's currently running
 * @property {(e: PointerEvent) => void} [onPointerDown] - An optional callback that will be executed for every pointerdown event
 * regardless of whether this Controller has an open tooltip, or not
 */

/**
 * This encapsulates all the logic relating to showing/hiding the HTML Tooltip
 *
 * Note: This could be displayed in the current webpage (for example, in the extension)
 * or within a webview overlay (like on macOS & upcoming in windows)
 */
export class HTMLTooltipUIController extends UIController {
    /** @type {import("../HTMLTooltip.js").HTMLTooltip | null} */
    _activeTooltip = null

    /** @type {HTMLTooltipControllerOptions} */
    _options;

    /** @type {import('../HTMLTooltip.js').HTMLTooltipOptions} */
    _htmlTooltipOptions;

    /** @type {import("../../DeviceInterface/InterfacePrototype").default | null} */
    _device = null;

    /**
     * Store any cleanups that may have been registered
     * @type {CleanupFn[]}
     */
    _listenerCleanups = []

    /**
     * @param {HTMLTooltipControllerOptions} options
     * @param {import('../HTMLTooltip.js').HTMLTooltipOptions} htmlTooltipOptions
     */
    constructor (options, htmlTooltipOptions = defaultOptions) {
        super()
        this._options = options
        this._htmlTooltipOptions = htmlTooltipOptions
        window.addEventListener('pointerdown', this, true)
    }

    /**
     * @param {import('./UIController').AttachArgs} args
     */
    attach (args) {
        if (this.getActiveTooltip()) {
            return
        }
        const { topContextData, getPosition, input, form } = args
        const tooltip = this.createTooltip(getPosition, topContextData)
        this.setActiveTooltip(tooltip)
        form.showingTooltip(input)
    }

    /**
     * Actually create the HTML Tooltip
     * @param {PosFn} getPosition
     * @param {TopContextData} topContextData
     * @return {import("../HTMLTooltip").HTMLTooltip}
     */
    createTooltip (getPosition, topContextData) {
        this._attachListeners()
        const config = getInputConfigFromType(topContextData.inputType)

        /**
         * @type {import('../HTMLTooltip').HTMLTooltipOptions}
         */
        const tooltipOptions = {
            ...this._htmlTooltipOptions,
            remove: () => this.removeTooltip()
        }

        if (this._options.tooltipKind === 'legacy') {
            return new EmailHTMLTooltip(config, topContextData.inputType, getPosition, tooltipOptions)
                .render(this._options.device)
        }

        // collect the data for each item to display
        const data = this._dataForAutofill(config, topContextData.inputType, topContextData)

        // convert the data into tool tip item renderers
        const asRenderers = data.map(d => config.tooltipItem(d))

        // construct the autofill
        return new DataHTMLTooltip(config, topContextData.inputType, getPosition, tooltipOptions)
            .render(config, asRenderers, {
                onSelect: (id) => {
                    this._onSelect(config, data, id)
                }
            })
    }

    _attachListeners () {
        window.addEventListener('input', this)
        window.addEventListener('keydown', this)
    }

    _removeListeners () {
        window.removeEventListener('input', this)
        window.removeEventListener('keydown', this)
    }

    handleEvent (event) {
        switch (event.type) {
        case 'keydown':
            if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
                this.removeTooltip()
            }
            break
        case 'input':
            this.removeTooltip()
            break
        case 'pointerdown': {
            this._pointerDownListener(event)
            break
        }
        }
    }

    // Global listener for event delegation
    _pointerDownListener (e) {
        if (!e.isTrusted) return

        // @ts-ignore
        if (e.target.nodeName === 'DDG-AUTOFILL') {
            e.preventDefault()
            e.stopImmediatePropagation()

            const activeTooltip = this.getActiveTooltip()
            if (!activeTooltip) {
                console.warn('Could not get activeTooltip')
            } else {
                activeTooltip.dispatchClick()
            }
        } else {
            this.removeTooltip().catch(e => {
                console.error('error removing tooltip', e)
            })
        }

        this._options.onPointerDown?.(e)
    }

    async removeTooltip (_via) {
        this._htmlTooltipOptions.remove()
        if (this._activeTooltip) {
            this._removeListeners()
            this._activeTooltip.remove()
            this._activeTooltip = null
        }
    }

    /**
     * @returns {import("../HTMLTooltip.js").HTMLTooltip|null}
     */
    getActiveTooltip () {
        return this._activeTooltip
    }

    /**
     * @param {import("../HTMLTooltip.js").HTMLTooltip} value
     */
    setActiveTooltip (value) {
        this._activeTooltip = value
    }

    /**
     * Collect the data that's needed to populate the Autofill UI.
     *
     * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
     *
     * @param {InputTypeConfigs} config - This is the selected `InputTypeConfig` based on the type of field
     * @param {import('../../Form/matching').SupportedTypes} inputType - The input type for the current field
     * @param {TopContextData} topContextData
     */
    _dataForAutofill (config, inputType, topContextData) {
        return this._options.device.dataForAutofill(config, inputType, topContextData)
    }

    /**
     * When a field is selected, call the `onSelect` method from the device.
     *
     * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
     *
     * @param {InputTypeConfigs} config
     * @param {(CreditCardObject | IdentityObject | CredentialsObject)[]} data
     * @param {string | number} id
     */
    _onSelect (config, data, id) {
        return this._options.device.onSelect(config, data, id)
    }

    isActive () {
        return Boolean(this.getActiveTooltip())
    }
}
