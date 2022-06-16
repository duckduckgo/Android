import { overrideProperty, defineProperty } from '../utils'

/**
 * normalize window dimensions, if more than one monitor is in play.
 *  X/Y values are set in the browser based on distance to the main monitor top or left, which
 * can mean second or more monitors have very large or negative values. This function maps a given
 * given coordinate value to the proper place on the main screen.
 */
function normalizeWindowDimension (value, targetDimension) {
    if (value > targetDimension) {
        return value % targetDimension
    }
    if (value < 0) {
        return targetDimension + value
    }
    return value
}

function setWindowPropertyValue (property, value) {
    // Here we don't update the prototype getter because the values are updated dynamically
    try {
        defineProperty(globalThis, property, {
            get: () => value,
            set: () => {},
            configurable: true
        })
    } catch (e) {}
}

const origPropertyValues = {}

/**
 * Fix window dimensions. The extension runs in a different JS context than the
 * page, so we can inject the correct screen values as the window is resized,
 * ensuring that no information is leaked as the dimensions change, but also that the
 * values change correctly for valid use cases.
 */
function setWindowDimensions () {
    try {
        const window = globalThis
        const top = globalThis.top

        const normalizedY = normalizeWindowDimension(window.screenY, window.screen.height)
        const normalizedX = normalizeWindowDimension(window.screenX, window.screen.width)
        if (normalizedY <= origPropertyValues.availTop) {
            setWindowPropertyValue('screenY', 0)
            setWindowPropertyValue('screenTop', 0)
        } else {
            setWindowPropertyValue('screenY', normalizedY)
            setWindowPropertyValue('screenTop', normalizedY)
        }

        if (top.window.outerHeight >= origPropertyValues.availHeight - 1) {
            setWindowPropertyValue('outerHeight', top.window.screen.height)
        } else {
            try {
                setWindowPropertyValue('outerHeight', top.window.outerHeight)
            } catch (e) {
                // top not accessible to certain iFrames, so ignore.
            }
        }

        if (normalizedX <= origPropertyValues.availLeft) {
            setWindowPropertyValue('screenX', 0)
            setWindowPropertyValue('screenLeft', 0)
        } else {
            setWindowPropertyValue('screenX', normalizedX)
            setWindowPropertyValue('screenLeft', normalizedX)
        }

        if (top.window.outerWidth >= origPropertyValues.availWidth - 1) {
            setWindowPropertyValue('outerWidth', top.window.screen.width)
        } else {
            try {
                setWindowPropertyValue('outerWidth', top.window.outerWidth)
            } catch (e) {
                // top not accessible to certain iFrames, so ignore.
            }
        }
    } catch (e) {
        // in a cross domain iFrame, top.window is not accessible.
    }
}

export function init (args) {
    const Screen = globalThis.Screen
    const screen = globalThis.screen

    origPropertyValues.availTop = overrideProperty('availTop', {
        object: Screen.prototype,
        origValue: screen.availTop,
        targetValue: 0
    })
    origPropertyValues.availLeft = overrideProperty('availLeft', {
        object: Screen.prototype,
        origValue: screen.availLeft,
        targetValue: 0
    })
    origPropertyValues.availWidth = overrideProperty('availWidth', {
        object: Screen.prototype,
        origValue: screen.availWidth,
        targetValue: screen.width
    })
    origPropertyValues.availHeight = overrideProperty('availHeight', {
        object: Screen.prototype,
        origValue: screen.availHeight,
        targetValue: screen.height
    })
    overrideProperty('colorDepth', {
        object: Screen.prototype,
        origValue: screen.colorDepth,
        targetValue: 24
    })
    overrideProperty('pixelDepth', {
        object: Screen.prototype,
        origValue: screen.pixelDepth,
        targetValue: 24
    })

    window.addEventListener('resize', function () {
        setWindowDimensions()
    })
    setWindowDimensions()
}
