import { getInputSubtype } from './Form/matching'

const SIGN_IN_MSG = { signMeIn: true }

// Send a message to the web app (only on DDG domains)
const notifyWebApp = (message) => {
    window.postMessage(message, window.origin)
}
/**
 * Sends a message and returns a Promise that resolves with the response
 * @param {{} | Function} msgOrFn - a fn to call or an object to send via postMessage
 * @param {String} expectedResponse - the name of the response
 * @returns {Promise<*>}
 */
const sendAndWaitForAnswer = (msgOrFn, expectedResponse) => {
    if (typeof msgOrFn === 'function') {
        msgOrFn()
    } else {
        window.postMessage(msgOrFn, window.origin)
    }

    return new Promise((resolve) => {
        const handler = e => {
            if (e.origin !== window.origin) return
            if (!e.data || (e.data && !(e.data[expectedResponse] || e.data.type === expectedResponse))) return

            resolve(e.data)
            window.removeEventListener('message', handler)
        }
        window.addEventListener('message', handler)
    })
}

/**
 * @param {GlobalConfig} globalConfig
 * @param [processConfig]
 * @return {boolean}
 */
const autofillEnabled = (globalConfig, processConfig) => {
    if (!globalConfig.contentScope) {
        // Return enabled for platforms that haven't implemented the config yet
        return true
    }

    const { contentScope, userUnprotectedDomains, userPreferences } = globalConfig

    // Check config on Apple platforms
    const processedConfig = processConfig(contentScope, userUnprotectedDomains, userPreferences)
    return isAutofillEnabledFromProcessedConfig(processedConfig)
}

const isAutofillEnabledFromProcessedConfig = (processedConfig) => {
    const site = processedConfig.site
    if (site.isBroken || !site.enabledFeatures.includes('autofill')) {
        return false
    }

    return true
}

// Access the original setter (needed to bypass React's implementation on mobile)
// @ts-ignore
const originalSet = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set

/**
 * Ensures the value is set properly and dispatches events to simulate real user action
 * @param {HTMLInputElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */
const setValueForInput = (el, val, config) => {
    // Avoid keyboard flashing on Android
    if (!config?.isAndroid) {
        el.focus()
    }

    // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it
    el.dispatchEvent(new Event('keydown', {bubbles: true}))

    originalSet?.call(el, val)

    const events = [
        new Event('input', {bubbles: true}),
        // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it
        new Event('keyup', {bubbles: true}),
        new Event('change', {bubbles: true})
    ]
    events.forEach((ev) => el.dispatchEvent(ev))
    // We call this again to make sure all forms are happy
    originalSet?.call(el, val)
    events.forEach((ev) => el.dispatchEvent(ev))
    el.blur()

    return true
}

/**
 * Fires events on a select element to simulate user interaction
 * @param {HTMLSelectElement} el
 */
const fireEventsOnSelect = (el) => {
    /** @type {Event[]} */
    const events = [
        new Event('mousedown', {bubbles: true}),
        new Event('mouseup', {bubbles: true}),
        new Event('click', {bubbles: true}),
        new Event('change', {bubbles: true})
    ]

    // Events fire on the select el, not option
    events.forEach((ev) => el.dispatchEvent(ev))
    events.forEach((ev) => el.dispatchEvent(ev))
    el.blur()
}

/**
 * Selects an option of a select element
 * We assume Select is only used for dates, i.e. in the credit card
 * @param {HTMLSelectElement} el
 * @param {string} val
 * @return {boolean}
 */
const setValueForSelect = (el, val) => {
    const subtype = getInputSubtype(el)
    const isMonth = subtype.includes('Month')
    const isZeroBasedNumber = isMonth &&
        el.options[0].value === '0' && el.options.length === 12

    // Loop first through all values because they tend to be more precise
    for (const option of el.options) {
        // If values for months are zero-based (Jan === 0), add one to match our data type
        let value = option.value
        if (isZeroBasedNumber) {
            value = `${Number(value) + 1}`
        }
        // TODO: try to match localised month names
        if (value === String(val)) {
            if (option.selected) return false
            option.selected = true
            fireEventsOnSelect(el)
            return true
        }
    }

    for (const option of el.options) {
        if (option.innerText === String(val)) {
            if (option.selected) return false
            option.selected = true
            fireEventsOnSelect(el)
            return true
        }
    }
    // If we didn't find a matching option return false
    return false
}

/**
 * Sets or selects a value to a form element
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */
const setValue = (el, val, config) => {
    if (el instanceof HTMLInputElement) return setValueForInput(el, val, config)
    if (el instanceof HTMLSelectElement) return setValueForSelect(el, val)

    return false
}

/**
 * Use IntersectionObserver v2 to make sure the element is visible when clicked
 * https://developers.google.com/web/updates/2019/02/intersectionobserver-v2
 */
const safeExecute = (el, fn) => {
    const intObs = new IntersectionObserver((changes) => {
        for (const change of changes) {
            // Feature detection
            if (typeof change.isVisible === 'undefined') {
                // The browser doesn't support Intersection Observer v2, falling back to v1 behavior.
                change.isVisible = true
            }
            if (change.isIntersecting && change.isVisible) {
                fn()
            }
        }
        intObs.disconnect()
    }, {trackVisibility: true, delay: 100})
    intObs.observe(el)
}

/**
 * Checks that an element is potentially viewable (even if off-screen)
 * @param {HTMLElement} el
 * @return {boolean}
 */
const isVisible = (el) =>
    el.clientWidth !== 0 &&
    el.clientHeight !== 0 &&
    (el.style.opacity !== '' ? parseFloat(el.style.opacity) > 0 : true)

/**
 * Gets the bounding box of the icon
 * @param {HTMLInputElement} input
 * @returns {{top: number, left: number, bottom: number, width: number, x: number, y: number, right: number, height: number}}
 */
const getDaxBoundingBox = (input) => {
    const {right: inputRight, top: inputTop, height: inputHeight} = input.getBoundingClientRect()
    const inputRightPadding = parseInt(getComputedStyle(input).paddingRight)
    const width = 30
    const height = 30
    const top = inputTop + (inputHeight - height) / 2
    const right = inputRight - inputRightPadding
    const left = right - width
    const bottom = top + height

    return {bottom, height, left, right, top, width, x: left, y: top}
}

/**
 * Check if a mouse event is within the icon
 * @param {MouseEvent} e
 * @param {HTMLInputElement} input
 * @returns {boolean}
 */
const isEventWithinDax = (e, input) => {
    const {left, right, top, bottom} = getDaxBoundingBox(input)
    const withinX = e.clientX >= left && e.clientX <= right
    const withinY = e.clientY >= top && e.clientY <= bottom

    return withinX && withinY
}

/**
 * Adds inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */
const addInlineStyles = (el, styles) => Object.entries(styles)
    .forEach(([property, val]) => el.style.setProperty(property, val, 'important'))

/**
 * Removes inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */
const removeInlineStyles = (el, styles) => Object.keys(styles)
    .forEach(property => el.style.removeProperty(property))

const ADDRESS_DOMAIN = '@duck.com'
/**
 * Given a username, returns the full email address
 * @param {string} address
 * @returns {string}
 */
const formatDuckAddress = (address) => address + ADDRESS_DOMAIN

/**
 * Escapes any occurrences of &, ", <, > or / with XML entities.
 * @param {string} str The string to escape.
 * @return {string} The escaped string.
 */
function escapeXML (str) {
    const replacements = { '&': '&amp;', '"': '&quot;', "'": '&apos;', '<': '&lt;', '>': '&gt;', '/': '&#x2F;' }
    return String(str).replace(/[&"'<>/]/g, m => replacements[m])
}

const SUBMIT_BUTTON_REGEX = /submit|send|confirm|save|continue|sign|log.?([io])n|buy|purchase|check.?out|subscribe|donate/i
const SUBMIT_BUTTON_UNLIKELY_REGEX = /facebook|twitter|google|apple|cancel|password|show|toggle|reveal|hide/i
/**
 * Determines if an element is likely to be a submit button
 * @param {HTMLElement} el A button, input, anchor or other element with role=button
 * @return {boolean}
 */
const isLikelyASubmitButton = (el) => {
    const text = el.textContent || ''
    const ariaLabel = el.getAttribute('aria-label') || ''
    const title = el.title || ''
    const value = (el instanceof HTMLInputElement ? el.value || '' : '')
    const contentExcludingLabel = text + ' ' + title + ' ' + value

    return (el.getAttribute('type') === 'submit' || // is explicitly set as "submit"
    /primary|submit/i.test(el.className) || // has high-signal submit classes
    SUBMIT_BUTTON_REGEX.test(contentExcludingLabel) || // has high-signal text
    el.offsetHeight * el.offsetWidth >= 10000) && // it's a large element, at least 250x40px
    !SUBMIT_BUTTON_UNLIKELY_REGEX.test(contentExcludingLabel + ' ' + ariaLabel)
}

/**
 * Check that a button matches the form type - login buttons on a login form, signup buttons on a signup form
 * @param {HTMLElement} el
 * @param {import('./Form/Form').Form} formObj
 */
const buttonMatchesFormType = (el, formObj) => {
    if (formObj.isLogin) {
        return !/sign.?up/i.test(el.textContent || '')
    } else if (formObj.isSignup) {
        return !/(log|sign).?([io])n/i.test(el.textContent || '')
    } else {
        return true
    }
}

export {
    notifyWebApp,
    sendAndWaitForAnswer,
    isAutofillEnabledFromProcessedConfig,
    autofillEnabled,
    setValue,
    safeExecute,
    isVisible,
    getDaxBoundingBox,
    isEventWithinDax,
    addInlineStyles,
    removeInlineStyles,
    SIGN_IN_MSG,
    ADDRESS_DOMAIN,
    formatDuckAddress,
    escapeXML,
    isLikelyASubmitButton,
    buttonMatchesFormType
}
