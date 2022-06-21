import {Password} from './lib/apple.password.js'
import {ParserError} from './lib/rules-parser.js'
import {constants} from './lib/constants.js'

/**
 * @typedef {{
 *   domain?: string | null | undefined;
 *   input?: string | null | undefined;
 *   rules?: RulesFormat | null | undefined;
 *   onError?: ((error: unknown) => void) | null | undefined;
 * }} GenerateOptions
 */

/**
 * Generate a random password based on the following attempts
 *
 * 1) using `options.input` if provided -> falling back to default ruleset
 * 2) using `options.domain` if provided -> falling back to default ruleset
 * 3) using default ruleset
 *
 * Note: This API is designed to never throw - if you want to observe errors
 * during development, you can provide an `onError` callback
 *
 * @param {GenerateOptions} [options]
 */
function generate (options = {}) {
    try {
        if (typeof options?.input === 'string') {
            return Password.generateOrThrow(options.input)
        }
        if (typeof options?.domain === 'string') {
            if (options?.rules) {
                const rules = _selectPasswordRules(options.domain, options.rules)
                if (rules) {
                    return Password.generateOrThrow(rules)
                }
            }
        }
    } catch (e) {
        // if an 'onError' callback was provided, forward all errors
        if (options?.onError && typeof options?.onError === 'function') {
            options.onError(e)
        } else {
            // otherwise, only console.error unknown errors (which could be implementation bugs)
            const isKnownError = e instanceof ParserError || e instanceof HostnameInputError
            if (!isKnownError) {
                console.error(e)
            }
        }
    }

    // At this point, we have to trust the generation will not throw
    // as it is NOT using any user/page-provided data
    return Password.generateDefault()
}

// An extension type to differentiate between known errors
class HostnameInputError extends Error {}

/**
 * @typedef {Record<string, {"password-rules": string}>} RulesFormat
 */

/**
 * @private
 * @param {string} inputHostname
 * @param {RulesFormat} rules
 * @returns {string | undefined}
 * @throws {HostnameInputError}
 */
function _selectPasswordRules (inputHostname, rules) {
    const hostname = _safeHostname(inputHostname)
    // direct match
    if (rules[hostname]) {
        return rules[hostname]['password-rules']
    }

    // otherwise, start chopping off subdomains and re-joining to compare
    const pieces = hostname.split('.')
    while (pieces.length > 1) {
        pieces.shift()
        const joined = pieces.join('.')
        if (rules[joined]) {
            return rules[joined]['password-rules']
        }
    }
    return undefined
}

/**
 * @private
 * @param {string} inputHostname;
 * @throws {HostnameInputError}
 * @returns {string}
 */
function _safeHostname (inputHostname) {
    if (inputHostname.startsWith('http:') || inputHostname.startsWith('https:')) {
        throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a scheme')
    }
    if (inputHostname.includes(':')) {
        throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a :port')
    }
    try {
        const asUrl = new URL('https://' + inputHostname)
        return asUrl.hostname
    } catch (e) {
        throw new HostnameInputError(`could not instantiate a URL from that hostname ${inputHostname}`)
    }
}

export {
    generate,
    _selectPasswordRules,
    HostnameInputError,
    ParserError,
    constants
}
