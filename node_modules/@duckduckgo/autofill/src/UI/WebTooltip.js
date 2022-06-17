import {getInputConfigFromType} from '../Form/inputTypeConfig'
import DataWebTooltip from './DataWebTooltip'
import EmailWebTooltip from './EmailWebTooltip'

/**
 * @typedef {object} WebTooltipOptions
 * @property {"modern" | "legacy"} tooltipKind
 */

/**
 * @implements {TooltipInterface}
 * @implements {WebTooltipHandler}
 */
export class WebTooltip {
    /** @type {import("../UI/Tooltip.js").Tooltip | null} */
    _activeTooltip = null

    /** @type {WebTooltipOptions} */
    _options;

    /**
     * @deprecated do not access the tooltipHandler directly here
     * @type {import("../DeviceInterface/InterfacePrototype").default | null}
     */
    _device = null;

    _listenerFactories = []
    _listenerCleanups = []

    /**
     * @param {WebTooltipOptions} options
     */
    constructor (options) {
        this._options = options
        window.addEventListener('pointerdown', this, true)
    }

    attach (args) {
        if (this.getActiveTooltip()) {
            // todo: Is this is correct logic?
            return
        }
        this.#setDevice(args.device)
        const { topContextData, getPosition, input, form } = args
        this.setActiveTooltip(this.createTooltip(getPosition, topContextData))
        form.showingTooltip(input)
    }

    /**
     * TODO: Don't allow this to be called from outside since it's deprecated.
     * @param {PosFn} getPosition
     * @param {TopContextData} topContextData
     * @return {import("./Tooltip").Tooltip}
     */
    createTooltip (getPosition, topContextData) {
        this.#attachCloseListeners()
        const config = getInputConfigFromType(topContextData.inputType)

        if (this._options.tooltipKind === 'modern') {
            // collect the data for each item to display
            const data = this.#dataForAutofill(config, topContextData.inputType, topContextData)

            // convert the data into tool tip item renderers
            const asRenderers = data.map(d => config.tooltipItem(d))

            // construct the autofill
            return new DataWebTooltip(config, topContextData.inputType, getPosition, this, {testMode: this.#device.isTestMode()})
                .render(config, asRenderers, {
                    onSelect: (id) => {
                        this.#onSelect(config, data, id)
                    }
                })
        } else {
            return new EmailWebTooltip(config, topContextData.inputType, getPosition, this, {testMode: this.#device.isTestMode()})
                .render(this.#device)
        }
    }

    #attachCloseListeners () {
        window.addEventListener('input', this)
        window.addEventListener('keydown', this)
        this._listenerCleanups = []
        for (let listenerFactory of this._listenerFactories) {
            this._listenerCleanups.push(listenerFactory())
        }
    }

    #removeCloseListeners () {
        window.removeEventListener('input', this)
        window.removeEventListener('keydown', this)
        for (let listenerCleanup of this._listenerCleanups) {
            listenerCleanup()
        }
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
        // todo(Shane): Why was this 'click' needed?
        case 'click':
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

        // todo(Shane): Form submissions here
        // exit now if form saving was not enabled
        // todo(Shane): more runtime polymorphism here, + where does this live?
        // if (this.autofillSettings.featureToggles.credentials_saving) {
        //     // Check for clicks on submit buttons
        //     const matchingForm = [...this.scanner.forms.values()].find(
        //         (form) => {
        //             const btns = [...form.submitButtons]
        //             // @ts-ignore
        //             if (btns.includes(e.target)) return true
        //
        //             // @ts-ignore
        //             if (btns.find((btn) => btn.contains(e.target))) return true
        //         }
        //     )
        //
        //     matchingForm?.submitHandler()
        // }
    }

    async removeTooltip () {
        if (this._activeTooltip) {
            this.#removeCloseListeners()
            this._activeTooltip.remove()
            this._activeTooltip = null
            this.currentAttached = null
        }
    }

    /**
     * @returns {import("../UI/Tooltip.js").Tooltip|null}
     */
    getActiveTooltip () {
        return this._activeTooltip
    }

    /**
     * @param {import("../UI/Tooltip.js").Tooltip} value
     */
    setActiveTooltip (value) {
        this._activeTooltip = value
    }

    /**
     * @deprecated don't rely in device in this class
     * @returns {Device}
     */
    get #device () {
        if (!this._device) throw new Error('device was not assigned')
        return this._device
    }

    #setDevice (device) {
        this._device = device
    }

    /**
     * @param {InputTypeConfigs} config
     * @param {import('../Form/matching').SupportedTypes} inputType
     * @param {TopContextData} topContextData
     */
    #dataForAutofill (config, inputType, topContextData) {
        return this.#device.dataForAutofill(config, inputType, topContextData)
    }

    /**
     * @param {InputTypeConfigs} config
     * @param {(CreditCardObject | IdentityObject | CredentialsObject)[]} data
     * @param {string | number} id
     */
    #onSelect (config, data, id) {
        return this.#device.onSelect(config, data, id)
    }

    setSize (_cb) {
        this.#device.setSize(_cb)
    }

    setupSizeListener (_cb) {
        this.#device.setupSizeListener(_cb)
    }

    tooltipPositionClass (top, left) {
        return this.#device.tooltipPositionClass(top, left)
    }

    tooltipStyles () {
        return this.#device.tooltipStyles()
    }

    tooltipWrapperClass () {
        return this.#device.tooltipWrapperClass()
    }

    setDevice (device) {
        this.#setDevice(device)
    }

    addListener (cb) {
        this._listenerFactories.push(cb)
    }
}
