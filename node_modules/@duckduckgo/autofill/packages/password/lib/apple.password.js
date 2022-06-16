/*
 *
 * NOTE:
 *
 * This file was created with inspiration from https://developer.apple.com/password-rules
 *
 * * The changes made by DuckDuckGo employees are:
 *
 * 1) removed all logic relating to 'more typeable passwords'
 * 2) reduced the number of password styles from 4 to only the 1 which suits our needs
 * 2) added JSDoc comments (for Typescript checking)
 *
 */
import * as parser from './rules-parser.js'
import {constants} from './constants.js'

/**
 * @typedef {{
 *     PasswordAllowedCharacters?: string,
 *     PasswordRequiredCharacters?: string[],
 *     PasswordRepeatedCharacterLimit?: number,
 *     PasswordConsecutiveCharacterLimit?: number,
 *     PasswordMinLength?: number,
 *     PasswordMaxLength?: number,
 * }} Requirements
 */

/**
 * @typedef {{
 *     NumberOfRequiredRandomCharacters: number,
 *     PasswordAllowedCharacters: string,
 *     RequiredCharacterSets: string[]
 * }} PasswordParameters
 */

const defaults = Object.freeze({
    SCAN_SET_ORDER: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-~!@#$%^&*_+=`|(){}[:;\\\"'<>,.?/ ]",
    defaultUnambiguousCharacters: 'abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789',
    defaultPasswordLength: constants.DEFAULT_MIN_LENGTH,
    defaultPasswordRules: constants.DEFAULT_PASSWORD_RULES,
    defaultRequiredCharacterSets: ['abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '0123456789'],
    /**
     * @type {typeof window.crypto.getRandomValues | null}
     */
    getRandomValues: null
})

/**
 * This is added here to ensure:
 *
 * 1) `getRandomValues` is called with the correct prototype chain
 * 2) `window` is not accessed when in a node environment
 * 3) `bind` is not called in a hot code path
 *
 * @type {{ getRandomValues: typeof window.crypto.getRandomValues }}
 */
const safeGlobals = {}
if (typeof window !== 'undefined') {
    safeGlobals.getRandomValues = window.crypto.getRandomValues.bind(window.crypto)
}

class Password {
    /**
     * @type {typeof defaults}
     */
    options;
    /**
     * @param {Partial<typeof defaults>} [options]
     */
    constructor (options = {}) {
        this.options = {
            ...defaults,
            ...options
        }
        return this
    }

    /**
     * This is here to provide external access to un-modified defaults
     * in case they are needed for tests/verifications
     * @type {typeof defaults}
     */
    static defaults = defaults;

    /**
     * Generates a password from the given input.
     *
     * Note: This method will throw an error if parsing fails - use with caution
     *
     * @example
     *
     * ```javascript
     * const password = Password.generateOrThrow("minlength: 20")
     * ```
     * @public
     * @param {string} inputString
     * @param {Partial<typeof defaults>} [options]
     * @throws {ParserError|Error}
     * @returns {string}
     */
    static generateOrThrow (inputString, options = {}) {
        return new Password(options)
            .parse(inputString)
            .generate()
    }
    /**
     * Generates a password using the default ruleset.
     *
     * @example
     *
     * ```javascript
     * const password = Password.generateDefault()
     * ```
     *
     * @public
     * @param {Partial<typeof defaults>} [options]
     * @returns {string}
     */
    static generateDefault (options = {}) {
        return new Password(options)
            .parse(Password.defaults.defaultPasswordRules)
            .generate()
    }

    /**
     * Convert a ruleset into it's internally-used component pieces.
     *
     * @param {string} inputString
     * @throws {parser.ParserError|Error}
     * @returns {{
     *    requirements: Requirements;
     *    parameters: PasswordParameters;
     *    rules: parser.Rule[],
     *    get entropy(): number;
     *    generate: () => string;
     * }}
     */
    parse (inputString) {
        const rules = parser.parsePasswordRules(inputString)
        const requirements = this._requirementsFromRules(rules)
        if (!requirements) throw new Error('could not generate requirements for ' + JSON.stringify(inputString))
        const parameters = this._passwordGenerationParametersDictionary(requirements)
        return {
            requirements,
            parameters,
            rules,
            get entropy () {
                return Math.log2(parameters.PasswordAllowedCharacters.length ** parameters.NumberOfRequiredRandomCharacters)
            },
            generate: () => {
                const password = this._generatedPasswordMatchingRequirements(requirements, parameters)
                /**
                 * The following is unreachable because if user input was incorrect then
                 * the parsing phase would throw. The following lines is to satisfy Typescript
                 */
                if (password === '') throw new Error('unreachable')
                return password
            }
        }
    }

    /**
     * Given an array of `Rule's`, convert into `Requirements`
     *
     * @param {parser.Rule[]} passwordRules
     * @returns {Requirements | null}
     */
    _requirementsFromRules (passwordRules) {
        /** @type {Requirements} */
        const requirements = {}
        for (let rule of passwordRules) {
            if (rule.name === parser.RuleName.ALLOWED) {
                console.assert(!('PasswordAllowedCharacters' in requirements))
                const chars = this._charactersFromCharactersClasses(rule.value)
                const scanSet = this._canonicalizedScanSetFromCharacters(chars)
                if (scanSet) {
                    requirements.PasswordAllowedCharacters = scanSet
                }
            } else if (rule.name === parser.RuleName.MAX_CONSECUTIVE) {
                console.assert(!('PasswordRepeatedCharacterLimit' in requirements))
                requirements.PasswordRepeatedCharacterLimit = rule.value
            } else if (rule.name === parser.RuleName.REQUIRED) {
                let requiredCharacters = requirements.PasswordRequiredCharacters
                if (!requiredCharacters) {
                    requiredCharacters = requirements.PasswordRequiredCharacters = []
                }
                requiredCharacters.push(this._canonicalizedScanSetFromCharacters(this._charactersFromCharactersClasses(rule.value)))
            } else if (rule.name === parser.RuleName.MIN_LENGTH) {
                requirements.PasswordMinLength = rule.value
            } else if (rule.name === parser.RuleName.MAX_LENGTH) {
                requirements.PasswordMaxLength = rule.value
            }
        }

        // Only include an allowed rule matching SCAN_SET_ORDER (all characters) when a required rule is also present.
        if (requirements.PasswordAllowedCharacters === this.options.SCAN_SET_ORDER && !requirements.PasswordRequiredCharacters) {
            delete requirements.PasswordAllowedCharacters
        }

        // Fix up PasswordRequiredCharacters, if needed.
        if (requirements.PasswordRequiredCharacters && requirements.PasswordRequiredCharacters.length === 1 && requirements.PasswordRequiredCharacters[0] === this.options.SCAN_SET_ORDER) {
            delete requirements.PasswordRequiredCharacters
        }

        return Object.keys(requirements).length ? requirements : null
    }

    /**
     * @param {number} range
     * @returns {number}
     */
    _randomNumberWithUniformDistribution (range) {
        const getRandomValues = this.options.getRandomValues || safeGlobals.getRandomValues
        // Based on the algorithm described in https://pthree.org/2018/06/13/why-the-multiply-and-floor-rng-method-is-biased/
        const max = Math.floor(2 ** 32 / range) * range
        let x
        do {
            x = getRandomValues(new Uint32Array(1))[0]
        } while (x >= max)

        return (x % range)
    }

    /**
     * @param {number} numberOfRequiredRandomCharacters
     * @param {string} allowedCharacters
     */
    _classicPassword (numberOfRequiredRandomCharacters, allowedCharacters) {
        const length = allowedCharacters.length
        const randomCharArray = Array(numberOfRequiredRandomCharacters)
        for (let i = 0; i < numberOfRequiredRandomCharacters; i++) {
            const index = this._randomNumberWithUniformDistribution(length)
            randomCharArray[i] = allowedCharacters[index]
        }
        return randomCharArray.join('')
    }

    /**
     * @param {string} password
     * @param {number} consecutiveCharLimit
     * @returns {boolean}
     */
    _passwordHasNotExceededConsecutiveCharLimit (password, consecutiveCharLimit) {
        let longestConsecutiveCharLength = 1
        let firstConsecutiveCharIndex = 0
        // Both "123" or "abc" and "321" or "cba" are considered consecutive.
        let isSequenceAscending
        for (let i = 1; i < password.length; i++) {
            const currCharCode = password.charCodeAt(i)
            const prevCharCode = password.charCodeAt(i - 1)
            if (isSequenceAscending) {
                // If `isSequenceAscending` is defined, then we know that we are in the middle of an existing
                // pattern. Check if the pattern continues based on whether the previous pattern was
                // ascending or descending.
                if ((isSequenceAscending.valueOf() && currCharCode === prevCharCode + 1) || (!isSequenceAscending.valueOf() && currCharCode === prevCharCode - 1)) {
                    continue
                }

                // Take into account the case when the sequence transitions from descending
                // to ascending.
                if (currCharCode === prevCharCode + 1) {
                    firstConsecutiveCharIndex = i - 1
                    isSequenceAscending = Boolean(true)
                    continue
                }

                // Take into account the case when the sequence transitions from ascending
                // to descending.
                if (currCharCode === prevCharCode - 1) {
                    firstConsecutiveCharIndex = i - 1
                    isSequenceAscending = Boolean(false)
                    continue
                }

                isSequenceAscending = null
            } else if (currCharCode === prevCharCode + 1) {
                isSequenceAscending = Boolean(true)
                continue
            } else if (currCharCode === prevCharCode - 1) {
                isSequenceAscending = Boolean(false)
                continue
            }

            const currConsecutiveCharLength = i - firstConsecutiveCharIndex
            if (currConsecutiveCharLength > longestConsecutiveCharLength) {
                longestConsecutiveCharLength = currConsecutiveCharLength
            }

            firstConsecutiveCharIndex = i
        }

        if (isSequenceAscending) {
            const currConsecutiveCharLength = password.length - firstConsecutiveCharIndex
            if (currConsecutiveCharLength > longestConsecutiveCharLength) {
                longestConsecutiveCharLength = currConsecutiveCharLength
            }
        }

        return longestConsecutiveCharLength <= consecutiveCharLimit
    }

    /**
     * @param {string} password
     * @param {number} repeatedCharLimit
     * @returns {boolean}
     */
    _passwordHasNotExceededRepeatedCharLimit (password, repeatedCharLimit) {
        let longestRepeatedCharLength = 1
        let lastRepeatedChar = password.charAt(0)
        let lastRepeatedCharIndex = 0
        for (let i = 1; i < password.length; i++) {
            const currChar = password.charAt(i)
            if (currChar === lastRepeatedChar) {
                continue
            }

            const currRepeatedCharLength = i - lastRepeatedCharIndex
            if (currRepeatedCharLength > longestRepeatedCharLength) {
                longestRepeatedCharLength = currRepeatedCharLength
            }

            lastRepeatedChar = currChar
            lastRepeatedCharIndex = i
        }
        return longestRepeatedCharLength <= repeatedCharLimit
    }

    /**
     * @param {string} password
     * @param {string[]} requiredCharacterSets
     * @returns {boolean}
     */
    _passwordContainsRequiredCharacters (password, requiredCharacterSets) {
        const requiredCharacterSetsLength = requiredCharacterSets.length
        const passwordLength = password.length
        for (let i = 0; i < requiredCharacterSetsLength; i++) {
            const requiredCharacterSet = requiredCharacterSets[i]
            let hasRequiredChar = false
            for (let j = 0; j < passwordLength; j++) {
                const char = password.charAt(j)
                if (requiredCharacterSet.indexOf(char) !== -1) {
                    hasRequiredChar = true
                    break
                }
            }
            if (!hasRequiredChar) {
                return false
            }
        }
        return true
    }

    /**
     * @param {string} string1
     * @param {string} string2
     * @returns {boolean}
     */
    _stringsHaveAtLeastOneCommonCharacter (string1, string2) {
        const string2Length = string2.length
        for (let i = 0; i < string2Length; i++) {
            const char = string2.charAt(i)
            if (string1.indexOf(char) !== -1) {
                return true
            }
        }

        return false
    }

    /**
     * @param {Requirements} requirements
     * @returns {PasswordParameters}
     */
    _passwordGenerationParametersDictionary (requirements) {
        let minPasswordLength = requirements.PasswordMinLength
        const maxPasswordLength = requirements.PasswordMaxLength

        // @ts-ignore
        if (minPasswordLength > maxPasswordLength) {
            // Resetting invalid value of min length to zero means "ignore min length parameter in password generation".
            minPasswordLength = 0
        }

        const requiredCharacterArray = requirements.PasswordRequiredCharacters
        let allowedCharacters = requirements.PasswordAllowedCharacters
        let requiredCharacterSets = this.options.defaultRequiredCharacterSets

        if (requiredCharacterArray) {
            const mutatedRequiredCharacterSets = []
            const requiredCharacterArrayLength = requiredCharacterArray.length

            for (let i = 0; i < requiredCharacterArrayLength; i++) {
                const requiredCharacters = requiredCharacterArray[i]
                if (allowedCharacters && this._stringsHaveAtLeastOneCommonCharacter(requiredCharacters, allowedCharacters)) {
                    mutatedRequiredCharacterSets.push(requiredCharacters)
                }
            }
            requiredCharacterSets = mutatedRequiredCharacterSets
        }

        // If requirements allow, we will generateOrThrow the password in default format: "xxx-xxx-xxx-xxx".
        let numberOfRequiredRandomCharacters = this.options.defaultPasswordLength
        if (minPasswordLength && minPasswordLength > numberOfRequiredRandomCharacters) {
            numberOfRequiredRandomCharacters = minPasswordLength
        }

        if (maxPasswordLength && maxPasswordLength < numberOfRequiredRandomCharacters) {
            numberOfRequiredRandomCharacters = maxPasswordLength
        }

        if (!allowedCharacters) {
            allowedCharacters = this.options.defaultUnambiguousCharacters
        }

        // In default password format, we use dashes only as separators, not as symbols you can encounter at a random position.

        if (!requiredCharacterSets) {
            requiredCharacterSets = this.options.defaultRequiredCharacterSets
        }

        // If we have more requirements of the type "need a character from set" than the length of the password we want to generateOrThrow, then
        // we will never be able to meet these requirements, and we'll end up in an infinite loop generating passwords. To avoid this,
        // reset required character sets if the requirements are impossible to meet.
        if (requiredCharacterSets.length > numberOfRequiredRandomCharacters) {
            requiredCharacterSets = []
        }

        // Do not require any character sets that do not contain allowed characters.
        const requiredCharacterSetsLength = requiredCharacterSets.length
        const mutatedRequiredCharacterSets = []
        const allowedCharactersLength = allowedCharacters.length
        for (let i = 0; i < requiredCharacterSetsLength; i++) {
            const requiredCharacterSet = requiredCharacterSets[i]
            let requiredCharacterSetContainsAllowedCharacters = false
            for (let j = 0; j < allowedCharactersLength; j++) {
                const character = allowedCharacters.charAt(j)
                if (requiredCharacterSet.indexOf(character) !== -1) {
                    requiredCharacterSetContainsAllowedCharacters = true
                    break
                }
            }
            if (requiredCharacterSetContainsAllowedCharacters) {
                mutatedRequiredCharacterSets.push(requiredCharacterSet)
            }
        }
        requiredCharacterSets = mutatedRequiredCharacterSets

        return {
            NumberOfRequiredRandomCharacters: numberOfRequiredRandomCharacters,
            PasswordAllowedCharacters: allowedCharacters,
            RequiredCharacterSets: requiredCharacterSets
        }
    }

    /**
     * @param {Requirements | null} requirements
     * @param {PasswordParameters} [parameters]
     * @returns {string}
     */
    _generatedPasswordMatchingRequirements (requirements, parameters) {
        requirements = requirements || {}
        parameters = parameters || this._passwordGenerationParametersDictionary(requirements)
        const numberOfRequiredRandomCharacters = parameters.NumberOfRequiredRandomCharacters
        const repeatedCharLimit = requirements.PasswordRepeatedCharacterLimit
        const allowedCharacters = parameters.PasswordAllowedCharacters
        const shouldCheckRepeatedCharRequirement = !!repeatedCharLimit

        while (true) {
            const password = this._classicPassword(numberOfRequiredRandomCharacters, allowedCharacters)

            if (!this._passwordContainsRequiredCharacters(password, parameters.RequiredCharacterSets)) {
                continue
            }

            if (shouldCheckRepeatedCharRequirement) {
                if (repeatedCharLimit !== undefined && repeatedCharLimit >= 1 && !this._passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit)) {
                    continue
                }
            }

            const consecutiveCharLimit = requirements.PasswordConsecutiveCharacterLimit
            if (consecutiveCharLimit && consecutiveCharLimit >= 1) {
                if (!this._passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit)) {
                    continue
                }
            }

            return password || ''
        }
    }

    /**
     * @param {parser.CustomCharacterClass | parser.NamedCharacterClass} characterClass
     * @returns {string[]}
     */
    _scanSetFromCharacterClass (characterClass) {
        if (characterClass instanceof parser.CustomCharacterClass) {
            return characterClass.characters
        }
        console.assert(characterClass instanceof parser.NamedCharacterClass)
        switch (characterClass.name) {
        case parser.Identifier.ASCII_PRINTABLE:
        case parser.Identifier.UNICODE:
            return this.options.SCAN_SET_ORDER.split('')
        case parser.Identifier.DIGIT:
            return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('0'), this.options.SCAN_SET_ORDER.indexOf('9') + 1).split('')
        case parser.Identifier.LOWER:
            return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('a'), this.options.SCAN_SET_ORDER.indexOf('z') + 1).split('')
        case parser.Identifier.SPECIAL:
            return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('-'), this.options.SCAN_SET_ORDER.indexOf(']') + 1).split('')
        case parser.Identifier.UPPER:
            return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('A'), this.options.SCAN_SET_ORDER.indexOf('Z') + 1).split('')
        }
        console.assert(false, parser.SHOULD_NOT_BE_REACHED)
        return []
    }

    /**
     * @param {(parser.CustomCharacterClass | parser.NamedCharacterClass)[]} characterClasses
     */
    _charactersFromCharactersClasses (characterClasses) {
        const output = []
        for (let characterClass of characterClasses) {
            output.push(...this._scanSetFromCharacterClass(characterClass))
        }
        return output
    }

    /**
     * @param {string[]} characters
     * @returns {string}
     */
    _canonicalizedScanSetFromCharacters (characters) {
        if (!characters.length) {
            return ''
        }
        let shadowCharacters = Array.prototype.slice.call(characters)
        shadowCharacters.sort((a, b) => this.options.SCAN_SET_ORDER.indexOf(a) - this.options.SCAN_SET_ORDER.indexOf(b))
        let uniqueCharacters = [shadowCharacters[0]]
        for (let i = 1, length = shadowCharacters.length; i < length; ++i) {
            if (shadowCharacters[i] === shadowCharacters[i - 1]) {
                continue
            }
            uniqueCharacters.push(shadowCharacters[i])
        }
        return uniqueCharacters.join('')
    }
}

export { Password }
