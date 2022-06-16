import { defineProperty, postDebugMessage } from '../utils'

function blockCookies (debug) {
    // disable setting cookies
    defineProperty(globalThis.document, 'cookie', {
        configurable: false,
        set: function (value) {
            if (debug) {
                postDebugMessage('jscookie', {
                    action: 'block',
                    reason: 'tracker frame',
                    documentUrl: globalThis.document.location.href,
                    scriptOrigins: [],
                    value: value
                })
            }
        },
        get: () => {
            if (debug) {
                postDebugMessage('jscookie', {
                    action: 'block',
                    reason: 'tracker frame',
                    documentUrl: globalThis.document.location.href,
                    scriptOrigins: [],
                    value: 'getter'
                })
            }
            return ''
        }
    })
}

export function init (args) {
    args.cookie.debug = args.debug
    if (globalThis.top !== globalThis && args.cookie.isTrackerFrame && args.cookie.shouldBlock && args.cookie.isThirdParty) {
        // overrides expiry policy with blocking - only in subframes
        blockCookies(args.debug)
    }
}
