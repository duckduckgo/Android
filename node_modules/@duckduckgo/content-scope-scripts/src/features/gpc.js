import { defineProperty } from '../utils'

// Set Global Privacy Control property on DOM
export function init (args) {
    try {
        // If GPC on, set DOM property prototype to true if not already true
        if (args.globalPrivacyControlValue) {
            if (navigator.globalPrivacyControl) return
            defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                get: () => true,
                configurable: true,
                enumerable: true
            })
        } else {
            // If GPC off & unsupported by browser, set DOM property prototype to false
            // this may be overwritten by the user agent or other extensions
            if (typeof navigator.globalPrivacyControl !== 'undefined') return
            defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                get: () => false,
                configurable: true,
                enumerable: true
            })
        }
    } catch {
        // Ignore exceptions that could be caused by conflicting with other extensions
    }
}
