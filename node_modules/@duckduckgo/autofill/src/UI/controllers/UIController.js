/**
 * @typedef AttachArgs The argument required to 'attach' a tooltip
 * @property {import("../../Form/Form").Form} form the Form that triggered this 'attach' call
 * @property {HTMLInputElement} input the input field that triggered this 'attach' call
 * @property {() => { x: number; y: number; height: number; width: number; }} getPosition A function that provides positioning information
 * @property {{x: number, y: number}|null} click The click positioning
 * @property {TopContextData} topContextData
 * @property {import("../../DeviceInterface/InterfacePrototype").default} device
 */

/**
 * This is the base interface that `UIControllers` should extend/implement
 */
export class UIController {
    /**
     * Implement this method to control what happen when Autofill
     * has enough information to 'attach' a tooltip.
     *
     * @param {AttachArgs} _args
     * @returns {void}
     */
    attach (_args) {
        throw new Error('must implement attach')
    }
    /**
     * Implement this if your tooltip can be created from positioning
     * + topContextData.
     *
     * For example, in an 'overlay' on macOS/Windows this is needed since
     * there's no page information to call 'attach' above.
     *
     * @param {PosFn} _pos
     * @param {TopContextData} _topContextData
     * @returns {any | null}
     */
    createTooltip (_pos, _topContextData) {

    }
    /**
     * @param {string} _via
     */
    removeTooltip (_via) {

    }

    /**
     * Set the currently open HTMLTooltip instance
     *
     * @param {import("../HTMLTooltip.js").HTMLTooltip} _tooltip
     */
    setActiveTooltip (_tooltip) {

    }

    /**
     * Get the currently open HTMLTooltip instance, if one exists
     *
     * @returns {import("../HTMLTooltip.js").HTMLTooltip | null}
     */
    getActiveTooltip () {
        return null
    }

    /**
     * Indicate whether the controller deems itself 'active'
     *
     * @returns {boolean}
     */
    isActive () {
        return false
    }
}
