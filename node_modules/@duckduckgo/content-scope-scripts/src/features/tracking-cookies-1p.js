import { Cookie } from '../cookie.js'
import { defineProperty, postDebugMessage } from '../utils'

let loadedPolicyResolve
// Listen for a message from the content script which will configure the policy for this context
const trackerHosts = new Set()

/**
 * Apply an expiry policy to cookies set via document.cookie.
 */
function applyCookieExpiryPolicy () {
    const document = globalThis.document
    const Error = globalThis.Error
    const cookieSetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').set
    const cookieGetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').get
    const lineTest = /(\()?(http[^)]+):[0-9]+:[0-9]+(\))?/

    const loadPolicy = new Promise((resolve) => {
        loadedPolicyResolve = resolve
    })
    // Create the then callback now - this ensures that Promise.prototype.then changes won't break
    // this call.
    const loadPolicyThen = loadPolicy.then.bind(loadPolicy)
    defineProperty(document, 'cookie', {
        configurable: true,
        set: (value) => {
            // call the native document.cookie implementation. This will set the cookie immediately
            // if the value is valid. We will override this set later if the policy dictates that
            // the expiry should be changed.
            cookieSetter.apply(document, [value])
            try {
                // determine the origins of the scripts in the stack
                const stack = new Error().stack.split('\n')
                const scriptOrigins = stack.reduce((origins, line) => {
                    const res = line.match(lineTest)
                    if (res && res[2]) {
                        origins.add(new URL(res[2]).hostname)
                    }
                    return origins
                }, new Set())

                // wait for config before doing same-site tests
                loadPolicyThen(({ shouldBlock, tabRegisteredDomain, policy, isTrackerFrame, debug }) => {
                    if (!tabRegisteredDomain || !shouldBlock) {
                        // no site domain for this site to test against, abort
                        debug && postDebugMessage('jscookie', {
                            action: 'ignore',
                            reason: 'disabled',
                            documentUrl: document.location.href,
                            scriptOrigins: [...scriptOrigins],
                            value
                        })
                        return
                    }
                    const sameSiteScript = [...scriptOrigins].every((host) => host === tabRegisteredDomain || host.endsWith(`.${tabRegisteredDomain}`))
                    if (sameSiteScript) {
                        // cookies set by scripts loaded on the same site as the site are not modified
                        debug && postDebugMessage('jscookie', {
                            action: 'ignore',
                            reason: 'sameSite',
                            documentUrl: document.location.href,
                            scriptOrigins: [...scriptOrigins],
                            value
                        })
                        return
                    }
                    const trackerScript = [...scriptOrigins].some((host) => trackerHosts.has(host))
                    if (!trackerScript && !isTrackerFrame) {
                        debug && postDebugMessage('jscookie', {
                            action: 'ignore',
                            reason: 'non-tracker',
                            documentUrl: document.location.href,
                            scriptOrigins: [...scriptOrigins],
                            value
                        })
                        return
                    }
                    // extract cookie expiry from cookie string
                    const cookie = new Cookie(value)
                    // apply cookie policy
                    if (cookie.getExpiry() > policy.threshold) {
                        // check if the cookie still exists
                        if (document.cookie.split(';').findIndex(kv => kv.trim().startsWith(cookie.parts[0].trim())) !== -1) {
                            cookie.maxAge = policy.maxAge
                            debug && postDebugMessage('jscookie', {
                                action: 'restrict',
                                reason: 'tracker',
                                documentUrl: document.location.href,
                                scriptOrigins: [...scriptOrigins],
                                value
                            })
                            cookieSetter.apply(document, [cookie.toString()])
                        } else {
                            debug && postDebugMessage('jscookie', {
                                action: 'ignored',
                                reason: 'dissappeared',
                                scriptOrigins: [...scriptOrigins],
                                value
                            })
                        }
                    } else {
                        debug && postDebugMessage('jscookie', {
                            action: 'ignored',
                            reason: 'expiry',
                            scriptOrigins: [...scriptOrigins],
                            value
                        })
                    }
                })
            } catch (e) {
                // suppress error in cookie override to avoid breakage
                console.warn('Error in cookie override', e)
            }
        },
        get: cookieGetter
    })
}

// Set up 1st party cookie blocker
export function load (args) {
    trackerHosts.clear()

    // The cookie expiry policy is injected into every frame immediately so that no cookie will
    // be missed.
    applyCookieExpiryPolicy()
}

export function init (args) {
    args.cookie.debug = args.debug
    loadedPolicyResolve(args.cookie)
}

export function update (args) {
    if (args.trackerDefinition) {
        trackerHosts.add(args.hostname)
    }
}
