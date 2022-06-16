
import { defineProperty } from '../utils'

/**
 * Fixes incorrect sizing value for outerHeight and outerWidth
 */
function windowSizingFix () {
    window.outerHeight = window.innerHeight
    window.outerWidth = window.innerWidth
}

/**
 * Add missing navigator.credentials API
 */
function navigatorCredentialsFix () {
    try {
        const value = {
            get () {
                return Promise.reject(new Error())
            }
        }
        defineProperty(Navigator.prototype, 'credentials', {
            value,
            configurable: true,
            enumerable: true
        })
    } catch {
        // Ignore exceptions that could be caused by conflicting with other extensions
    }
}

export function init () {
    windowSizingFix()
    navigatorCredentialsFix()
}
