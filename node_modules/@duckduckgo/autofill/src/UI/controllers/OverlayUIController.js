import {UIController} from './UIController'

/**
 * @typedef OverlayControllerOptions
 * @property {() => Promise<void>} remove - A callback that will be fired when the tooltip should be removed
 * @property {(details: ShowAutofillParentRequest) => Promise<void>} show - A callback that will be fired when the tooltip should be shown
 * @property {(e: PointerEvent) => void} [onPointerDown] - An optional callback for reacting to all `pointerdown` events.
 */

/**
 * @typedef ShowAutofillParentRequest - The argument that's sent to the native side
 * @property {boolean} wasFromClick - Whether the request originated from a click
 * @property {number} inputTop
 * @property {number} inputLeft
 * @property {number} inputHeight
 * @property {number} inputWidth
 * @property {string} serializedInputContext - Serialized JSON that will be picked up once the
 * 'overlay' requests its initial data
 */

/**
 * Use this `OverlayController` when you want to control an overlay, but don't have
 * your own UI to display.
 *
 * For example, on macOS this `OverlayController` would run in the main webpage
 * and would then signal to its native side when the overlay should show/close
 *
 * @example `show` and `remove` can be implemented to match your native side's messaging needs
 *
 * ```javascript
 * const controller = new OverlayController({
 *     remove: async () => this.closeAutofillParent(),
 *     show: async (details) => this.show(details),
 *     onPointerDown: (e) => this.onPointerDown(e)
 * })
 *
 * controller.attach(...)
 * ```
 */
export class OverlayUIController extends UIController {
    /** @type {"idle" | "parentShown"} */
    #state = 'idle';

    /** @type {import('../HTMLTooltip.js').HTMLTooltip | null} */
    _activeTooltip = null

    /**
     * @param {OverlayControllerOptions} options
     */
    constructor (options) {
        super(options)

        // We always register this 'pointerdown' event, regardless of
        // whether we have a tooltip currently open or not. This is to ensure
        // we can clear out any existing state before opening a new one.
        window.addEventListener('pointerdown', this, true)
    }

    /**
     * @param {import('./UIController').AttachArgs} args
     */
    attach (args) {
        const {getPosition, topContextData, click, input} = args
        let delay = 0
        if (!click && !this.elementIsInViewport(getPosition())) {
            input.scrollIntoView(true)
            delay = 500
        }
        setTimeout(() => {
            this.showTopTooltip(click, getPosition(), topContextData)
                .catch(e => {
                    console.error('error from showTopTooltip', e)
                })
        }, delay)
    }

    /**
     * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
     * @returns {boolean}
     */
    elementIsInViewport (inputDimensions) {
        if (inputDimensions.x < 0 ||
            inputDimensions.y < 0 ||
            inputDimensions.x + inputDimensions.width > document.documentElement.clientWidth ||
            inputDimensions.y + inputDimensions.height > document.documentElement.clientHeight) {
            return false
        }
        const viewport = document.documentElement
        if (inputDimensions.x + inputDimensions.width > viewport.clientWidth ||
            inputDimensions.y + inputDimensions.height > viewport.clientHeight) {
            return false
        }
        return true
    }

    /**
     * @param {{ x: number; y: number; } | null} click
     * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
     * @param {TopContextData} [data]
     */
    async showTopTooltip (click, inputDimensions, data) {
        let diffX = inputDimensions.x
        let diffY = inputDimensions.y
        if (click) {
            diffX -= click.x
            diffY -= click.y
        } else if (!this.elementIsInViewport(inputDimensions)) {
            // If the focus event is outside the viewport ignore, we've already tried to scroll to it
            return
        }

        /** @type {ShowAutofillParentRequest} */
        const details = {
            wasFromClick: Boolean(click),
            inputTop: Math.floor(diffY),
            inputLeft: Math.floor(diffX),
            inputHeight: Math.floor(inputDimensions.height),
            inputWidth: Math.floor(inputDimensions.width),
            serializedInputContext: JSON.stringify(data)
        }

        try {
            await this._options.show(details)
            this.#state = 'parentShown'
            this._attachListeners()
        } catch (e) {
            console.error('could not show parent', e)
        }
    }

    _attachListeners () {
        window.addEventListener('scroll', this)
        window.addEventListener('keydown', this)
        window.addEventListener('input', this)
    }

    _removeListeners () {
        window.removeEventListener('scroll', this)
        window.removeEventListener('keydown', this)
        window.removeEventListener('input', this)
    }

    handleEvent (event) {
        switch (event.type) {
        case 'scroll': {
            this.removeTooltip(event.type)
            break
        }
        case 'keydown': {
            if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
                this.removeTooltip(event.type)
            }
            break
        }
        case 'input': {
            this.removeTooltip(event.type)
            break
        }
        case 'pointerdown': {
            this.removeTooltip(event.type)
            this._options.onPointerDown?.(event)
            break
        }
        }
    }

    /**
     * @param {string} trigger
     * @returns {Promise<void>}
     */
    async removeTooltip (trigger) {
        // for none pointer events, check to see if the tooltip is open before trying to close it
        if (trigger !== 'pointerdown') {
            if (this.#state !== 'parentShown') {
                return
            }
        }
        this._options.remove()
            .catch(e => console.error('Could not close parent', e))
        this.#state = 'idle'
        this._removeListeners()
    }
}
