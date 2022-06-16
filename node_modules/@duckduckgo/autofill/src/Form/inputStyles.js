import { getInputConfig } from './inputTypeConfig.js'

/**
 * Returns the css-ready base64 encoding of the icon for the given input
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @param {'base' | 'filled'} type
 * @return {string}
 */
const getIcon = (input, form, type = 'base') => {
    const config = getInputConfig(input)
    if (type === 'base') {
        return config.getIconBase(input, form)
    }
    if (type === 'filled') {
        return config.getIconFilled(input, form)
    }
    return ''
}

/**
 * Returns an object with styles to be applied inline
 * @param {HTMLInputElement} input
 * @param {String} icon
 * @return {Object<string, string>}
 */
const getBasicStyles = (input, icon) => ({
    // Height must be > 0 to account for fields initially hidden
    'background-size': `auto ${input.offsetHeight <= 30 && input.offsetHeight > 0 ? '100%' : '26px'}`,
    'background-position': 'center right',
    'background-repeat': 'no-repeat',
    'background-origin': 'content-box',
    'background-image': `url(${icon})`,
    'transition': 'background 0s'
})

/**
 * Get inline styles for the injected icon, base state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */
const getIconStylesBase = (input, form) => {
    const icon = getIcon(input, form)

    if (!icon) return {}

    return getBasicStyles(input, icon)
}

/**
 * Get inline styles for the injected icon, autofilled state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */
const getIconStylesAutofilled = (input, form) => {
    const icon = getIcon(input, form, 'filled')

    const iconStyle = icon ? getBasicStyles(input, icon) : {}

    return {
        ...iconStyle,
        'background-color': '#F8F498',
        'color': '#333333'
    }
}

export {getIconStylesBase, getIconStylesAutofilled}
