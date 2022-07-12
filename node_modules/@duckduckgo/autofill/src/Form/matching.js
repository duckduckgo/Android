import { createCacheableVendorRegexes } from './vendor-regex'
import { constants } from '../constants'
import { extractElementStrings } from './label-util'
import { FORM_INPUTS_SELECTOR } from './selectors-css'
import { matchingConfiguration } from './matching-configuration'

const { TEXT_LENGTH_CUTOFF, ATTR_INPUT_TYPE } = constants

/**
 * An abstraction around the concept of classifying input fields.
 *
 * The only state this class keeps is derived from the passed-in MatchingConfiguration.
 */
class Matching {
    /** @type {MatchingConfiguration} */
    #config;

    /** @type {CssSelectorConfiguration['selectors']} */
    #cssSelectors;

    /** @type {Record<string, DDGMatcher>} */
    #ddgMatchers;

    /**
     * This acts as an internal cache for the larger vendorRegexes
     * @type {{RULES: Record<keyof VendorRegexRules, RegExp|undefined>}}
     */
    #vendorRegExpCache;

    /** @type {MatcherLists} */
    #matcherLists;

    /** @type {Array<StrategyNames>} */
    #defaultStrategyOrder = ['cssSelector', 'ddgMatcher', 'vendorRegex']

    /** @type {Record<MatchableStrings, string>} */
    activeElementStrings = {
        nameAttr: '',
        labelText: '',
        placeholderAttr: '',
        relatedText: '',
        id: ''
    }

    /**
     * @param {MatchingConfiguration} config
     */
    constructor (config) {
        this.#config = config

        const { rules, ruleSets } = this.#config.strategies.vendorRegex
        this.#vendorRegExpCache = createCacheableVendorRegexes(rules, ruleSets)
        this.#cssSelectors = this.#config.strategies.cssSelector.selectors
        this.#ddgMatchers = this.#config.strategies.ddgMatcher.matchers

        this.#matcherLists = {
            cc: [],
            id: [],
            password: [],
            username: [],
            email: []
        }

        /**
         * Convert the raw config data into actual references.
         *
         * For example this takes `email: ["email"]` and creates
         *
         * `email: [{type: "email", strategies: {cssSelector: "email", ... etc}]`
         */
        for (let [listName, matcherNames] of Object.entries(this.#config.matchers.lists)) {
            for (let fieldName of matcherNames) {
                if (!this.#matcherLists[listName]) {
                    this.#matcherLists[listName] = []
                }
                this.#matcherLists[listName].push(this.#config.matchers.fields[fieldName])
            }
        }
    }

    /**
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} formEl
     */
    setActiveElementStrings (input, formEl) {
        this.activeElementStrings = this.getElementStrings(input, formEl)
    }

    /**
     * Try to access a 'vendor regex' by name
     * @param {string} regexName
     * @returns {RegExp | undefined}
     */
    vendorRegex (regexName) {
        const match = this.#vendorRegExpCache.RULES[regexName]
        if (!match) {
            console.warn('Vendor Regex not found for', regexName)
            return undefined
        }
        return match
    }

    /**
     * Try to access a 'css selector' by name from configuration
     * @param {keyof RequiredCssSelectors | string} selectorName
     * @returns {string};
     */
    cssSelector (selectorName) {
        const match = this.#cssSelectors[selectorName]
        if (!match) {
            console.warn('CSS selector not found for %s, using a default value', selectorName)
            return ''
        }
        if (Array.isArray(match)) {
            return match.join(',')
        }
        return match
    }

    /**
     * Try to access a 'ddg matcher' by name from configuration
     * @param {keyof RequiredCssSelectors | string} matcherName
     * @returns {DDGMatcher | undefined}
     */
    ddgMatcher (matcherName) {
        const match = this.#ddgMatchers[matcherName]
        if (!match) {
            console.warn('DDG matcher not found for', matcherName)
            return undefined
        }
        return match
    }

    /**
     * Try to access a list of matchers by name - these are the ones collected in the constructor
     * @param {keyof MatcherLists} listName
     * @return {Matcher[]}
     */
    matcherList (listName) {
        const matcherList = this.#matcherLists[listName]
        if (!matcherList) {
            console.warn('MatcherList not found for ', listName)
            return []
        }
        return matcherList
    }

    /**
     * Convert a list of matchers into a single CSS selector.
     *
     * This will consider all matchers in the list and if it
     * contains a CSS Selector it will be added to the final output
     *
     * @param {keyof MatcherLists} listName
     * @returns {string | undefined}
     */
    joinCssSelectors (listName) {
        const matcherList = this.matcherList(listName)
        if (!matcherList) {
            console.warn('Matcher list not found for', listName)
            return undefined
        }

        /**
         * @type {string[]}
         */
        const selectors = []

        for (let matcher of matcherList) {
            if (matcher.strategies.cssSelector) {
                const css = this.cssSelector(matcher.strategies.cssSelector)
                if (css) {
                    selectors.push(css)
                }
            }
        }

        return selectors.join(', ')
    }

    /**
     * Tries to infer the input type for an input
     *
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} formEl
     * @param {{isLogin?: boolean}} [opts]
     * @returns {SupportedTypes}
     */
    inferInputType (input, formEl, opts = {}) {
        const presetType = getInputType(input)
        if (presetType !== 'unknown') {
            return presetType
        }

        this.setActiveElementStrings(input, formEl)

        // // For CC forms we run aggressive matches, so we want to make sure we only
        // // run them on actual CC forms to avoid false positives and expensive loops
        if (this.isCCForm(formEl)) {
            const subtype = this.subtypeFromMatchers('cc', input)
            if (subtype && isValidCreditCardSubtype(subtype)) {
                return `creditCards.${subtype}`
            }
        }

        if (input instanceof HTMLInputElement) {
            if (this.subtypeFromMatchers('password', input)) {
                return 'credentials.password'
            }

            if (this.subtypeFromMatchers('email', input)) {
                return opts.isLogin ? 'credentials.username' : 'identities.emailAddress'
            }

            if (this.subtypeFromMatchers('username', input)) {
                return 'credentials.username'
            }
        }

        const idSubtype = this.subtypeFromMatchers('id', input)

        if (idSubtype && isValidIdentitiesSubtype(idSubtype)) {
            return `identities.${idSubtype}`
        }

        return 'unknown'
    }

    /**
     * Sets the input type as a data attribute to the element and returns it
     * @param {HTMLInputElement} input
     * @param {HTMLElement} formEl
     * @param {{isLogin?: boolean}} [opts]
     * @returns {SupportedSubTypes | string}
     */
    setInputType (input, formEl, opts = {}) {
        const type = this.inferInputType(input, formEl, opts)
        input.setAttribute(ATTR_INPUT_TYPE, type)
        return type
    }

    /**
     * Tries to infer input subtype, with checks in decreasing order of reliability
     * @param {keyof MatcherLists} listName
     * @param {HTMLInputElement|HTMLSelectElement} el
     * @return {MatcherTypeNames|undefined}
     */
    subtypeFromMatchers (listName, el) {
        const matchers = this.matcherList(listName)

        /**
         * Loop through each strategy in order
         */
        for (let strategyName of this.#defaultStrategyOrder) {
            let result
            /**
             * Now loop through each matcher in the list.
             */
            for (let matcher of matchers) {
                /**
                 * for each `strategyName` (such as cssSelector), check
                 * if the current matcher implements it.
                 */
                const lookup = matcher.strategies[strategyName]
                /**
                 * Sometimes a matcher may not implement the current strategy,
                 * so we skip it
                 */
                if (!lookup) continue

                /**
                 * Now perform the matching
                 */
                if (strategyName === 'cssSelector') {
                    result = this.execCssSelector(lookup, el)
                }
                if (strategyName === 'ddgMatcher') {
                    result = this.execDDGMatcher(lookup)
                }
                if (strategyName === 'vendorRegex') {
                    result = this.execVendorRegex(lookup)
                }

                /**
                 * If there's a match, return the matcher type.
                 *
                 * So, for example if 'username' had a `cssSelector` implemented, and
                 * it matched the current element, then we'd return 'username'
                 */
                if (result?.matched) {
                    return matcher.type
                }

                /**
                 * If a matcher wants to prevent all future matching on this element,
                 * it would return { matched: false, proceed: false }
                 */
                if (!result?.matched && result?.proceed === false) {
                    // If we get here, do not allow subsequent strategies to continue
                    return undefined
                }
            }

            if (result?.skip) break
        }
        return undefined
    }

    /**
     * CSS selector matching just leverages the `.matches` method on elements
     *
     * @param {string} lookup
     * @param {HTMLInputElement|HTMLSelectElement} el
     * @returns {MatchingResult}
     */
    execCssSelector (lookup, el) {
        const selector = this.cssSelector(lookup)
        return { matched: el.matches(selector) }
    }

    /**
     * A DDG Matcher can have a `match` regex along with a `not` regex. This is done
     * to allow it to be driven by configuration as it avoids needing to invoke custom functions.
     *
     * todo: maxDigits was added as an edge-case when converting this over to be declarative, but I'm
     * unsure if it's actually needed. It's not urgent, but we should consider removing it if that's the case
     *
     * @param {string} lookup
     * @returns {MatchingResult}
     */
    execDDGMatcher (lookup) {
        const ddgMatcher = this.ddgMatcher(lookup)
        if (!ddgMatcher || !ddgMatcher.match) {
            return { matched: false }
        }
        let matchRexExp = safeRegex(ddgMatcher.match || '')
        if (!matchRexExp) {
            return {matched: false}
        }

        let requiredScore = ['match', 'forceUnknown', 'maxDigits'].filter(ddgMatcherProp => ddgMatcherProp in ddgMatcher).length

        /** @type {MatchableStrings[]} */
        const matchableStrings = ddgMatcher.matchableStrings || ['labelText', 'placeholderAttr', 'relatedText']

        for (let stringName of matchableStrings) {
            let elementString = this.activeElementStrings[stringName]
            if (!elementString) continue
            elementString = elementString.toLowerCase()

            // Scoring to ensure all DDG tests are valid
            let score = 0

            // If a negated regex was provided, ensure it does not match
            // If it DOES match - then we need to prevent any future strategies from continuing
            if (ddgMatcher.forceUnknown) {
                let notRegex = safeRegex(ddgMatcher.forceUnknown)
                if (!notRegex) {
                    return { matched: false }
                }
                if (notRegex.test(elementString)) {
                    return { matched: false, proceed: false }
                } else {
                    // All good here, increment the score
                    score++
                }
            }

            if (ddgMatcher.skip) {
                let skipRegex = safeRegex(ddgMatcher.skip)
                if (!skipRegex) {
                    return { matched: false }
                }
                if (skipRegex.test(elementString)) {
                    return { matched: false, skip: true }
                }
            }

            // if the `match` regex fails, moves onto the next string
            if (!matchRexExp.test(elementString)) {
                continue
            }

            // Otherwise, increment the score
            score++

            // If a 'maxDigits' rule was provided, validate it
            if (ddgMatcher.maxDigits) {
                const digitLength = elementString.replace(/[^0-9]/g, '').length
                if (digitLength > ddgMatcher.maxDigits) {
                    return { matched: false }
                } else {
                    score++
                }
            }

            if (score === requiredScore) {
                return { matched: true }
            }
        }
        return { matched: false }
    }

    /**
     * If we get here, a firefox/vendor regex was given and we can execute it on the element
     * strings
     * @param {string} lookup
     * @return {MatchingResult}
     */
    execVendorRegex (lookup) {
        const regex = this.vendorRegex(lookup)
        if (!regex) {
            return { matched: false }
        }
        /** @type {MatchableStrings[]} */
        const stringsToMatch = ['placeholderAttr', 'nameAttr', 'labelText', 'id', 'relatedText']
        for (let stringName of stringsToMatch) {
            let elementString = this.activeElementStrings[stringName]
            if (!elementString) continue
            elementString = elementString.toLowerCase()
            if (regex.test(elementString)) {
                return { matched: true }
            }
        }
        return { matched: false }
    }

    /**
     * Yield strings in the order in which they should be checked against.
     *
     * Note: some strategies may not want to accept all strings, which is
     * where `matchableStrings` helps. It defaults to when you see below but can
     * be overridden.
     *
     * For example, `nameAttr` is first, since this has the highest chance of matching
     * and then the rest are in decreasing order of value vs cost
     *
     * A generator function is used here to prevent any potentially expensive
     * lookups occurring if they are rare. For example if 90% of all matching never needs
     * to look at the output from `relatedText`, then the cost of computing it will be avoided.
     *
     * @param {HTMLInputElement|HTMLSelectElement} el
     * @param {HTMLElement} form
     * @returns {Record<MatchableStrings, string>}
     */
    _elementStringCache = new WeakMap();
    getElementStrings (el, form) {
        if (this._elementStringCache.has(el)) {
            return this._elementStringCache.get(el)
        }

        const explicitLabelsText = getExplicitLabelsText(el)

        /** @type {Record<MatchableStrings, string>} */
        const next = {
            nameAttr: el.name,
            labelText: explicitLabelsText,
            placeholderAttr: el.placeholder || '',
            id: el.id,
            relatedText: explicitLabelsText ? '' : getRelatedText(el, form, this.cssSelector('FORM_INPUTS_SELECTOR'))
        }
        this._elementStringCache.set(el, next)
        return next
    }
    clear () {
        this._elementStringCache = new WeakMap()
    }

    /**
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} form
     * @returns {Matching}
     */
    forInput (input, form) {
        this.setActiveElementStrings(input, form)
        return this
    }
    /**
     * Tries to infer if it's a credit card form
     * @param {HTMLElement} formEl
     * @returns {boolean}
     */
    isCCForm (formEl) {
        const ccFieldSelector = this.joinCssSelectors('cc')
        if (!ccFieldSelector) {
            return false
        }
        const hasCCSelectorChild = formEl.querySelector(ccFieldSelector)
        // If the form contains one of the specific selectors, we have high confidence
        if (hasCCSelectorChild) return true

        // Read form attributes to find a signal
        const hasCCAttribute = [...formEl.attributes].some(({name, value}) =>
            /(credit|payment).?card/i.test(`${name}=${value}`)
        )
        if (hasCCAttribute) return true

        // Match form textContent against common cc fields (includes hidden labels)
        const textMatches = formEl.textContent?.match(/(credit)?card(.?number)?|ccv|security.?code|cvv|cvc|csc/ig)

        // We check for more than one to minimise false positives
        return Boolean(textMatches && textMatches.length > 1)
    }

    /**
     * @type {MatchingConfiguration}
     */
    static emptyConfig = {
        matchers: {
            lists: {},
            fields: {}
        },
        strategies: {
            'vendorRegex': {
                rules: {},
                ruleSets: []
            },
            'ddgMatcher': {
                matchers: {}
            },
            'cssSelector': {
                selectors: {
                    FORM_INPUTS_SELECTOR
                }
            }
        }
    }
}

/**
 *  @returns {SupportedTypes}
 */
function getInputType (input) {
    const attr = input.getAttribute(ATTR_INPUT_TYPE)
    if (isValidSupportedType(attr)) {
        return attr
    }
    return 'unknown'
}

/**
 * Retrieves the main type
 * @param {SupportedTypes | string} type
 * @returns {SupportedMainTypes}
 */
function getMainTypeFromType (type) {
    const mainType = type.split('.')[0]
    switch (mainType) {
    case 'credentials':
    case 'creditCards':
    case 'identities':
        return mainType
    }
    return 'unknown'
}

/**
 * Retrieves the input main type
 * @param {HTMLInputElement} input
 * @returns {SupportedMainTypes}
 */
const getInputMainType = (input) =>
    getMainTypeFromType(getInputType(input))

/** @typedef {supportedIdentitiesSubtypes[number]} SupportedIdentitiesSubTypes */
const supportedIdentitiesSubtypes = /** @type {const} */ ([
    'emailAddress',
    'firstName',
    'middleName',
    'lastName',
    'fullName',
    'phone',
    'addressStreet',
    'addressStreet2',
    'addressCity',
    'addressProvince',
    'addressPostalCode',
    'addressCountryCode',
    'birthdayDay',
    'birthdayMonth',
    'birthdayYear'
])

/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedIdentitiesSubTypes}
 */
function isValidIdentitiesSubtype (supportedType) {
    return supportedIdentitiesSubtypes.includes(supportedType)
}

/** @typedef {supportedCreditCardSubtypes[number]} SupportedCreditCardSubTypes */
const supportedCreditCardSubtypes = /** @type {const} */ ([
    'cardName',
    'cardNumber',
    'cardSecurityCode',
    'expirationMonth',
    'expirationYear',
    'expiration'
])

/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCreditCardSubTypes}
 */
function isValidCreditCardSubtype (supportedType) {
    return supportedCreditCardSubtypes.includes(supportedType)
}

/** @typedef {supportedCredentialsSubtypes[number]} SupportedCredentialsSubTypes */
const supportedCredentialsSubtypes = /** @type {const} */ ([
    'password',
    'username'
])

/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCredentialsSubTypes}
 */
function isValidCredentialsSubtype (supportedType) {
    return supportedCredentialsSubtypes.includes(supportedType)
}

/** @typedef {SupportedIdentitiesSubTypes | SupportedCreditCardSubTypes | SupportedCredentialsSubTypes} SupportedSubTypes */

/** @typedef {`identities.${SupportedIdentitiesSubTypes}` | `creditCards.${SupportedCreditCardSubTypes}` | `credentials.${SupportedCredentialsSubTypes}` | 'unknown'} SupportedTypes */
const supportedTypes = [
    ...supportedIdentitiesSubtypes.map((type) => `identities.${type}`),
    ...supportedCreditCardSubtypes.map((type) => `creditCards.${type}`),
    ...supportedCredentialsSubtypes.map((type) => `credentials.${type}`)
]

/**
 * Retrieves the subtype
 * @param {SupportedTypes | string} type
 * @returns {SupportedSubTypes | 'unknown'}
 */
function getSubtypeFromType (type) {
    const subType = type?.split('.')[1]
    const validType = isValidSubtype(subType)
    return validType ? subType : 'unknown'
}

/**
 * @param {SupportedSubTypes | any} supportedSubType
 * @returns {supportedSubType is SupportedSubTypes}
 */
function isValidSubtype (supportedSubType) {
    return isValidIdentitiesSubtype(supportedSubType) ||
        isValidCreditCardSubtype(supportedSubType) ||
        isValidCredentialsSubtype(supportedSubType)
}

/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedTypes}
 */
function isValidSupportedType (supportedType) {
    return supportedTypes.includes(supportedType)
}

/**
 * Retrieves the input subtype
 * @param {HTMLInputElement|Element} input
 * @returns {SupportedSubTypes | 'unknown'}
 */
function getInputSubtype (input) {
    const type = getInputType(input)
    return getSubtypeFromType(type)
}

/**
 * Remove whitespace of more than 2 in a row and trim the string
 * @param {string | null} string
 * @return {string}
 */
const removeExcessWhitespace = (string = '') => {
    return (string || '')
        .replace(/\n/g, ' ')
        .replace(/\s{2,}/, ' ').trim()
}

/**
 * Get text from all explicit labels
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @return {string}
 */
const getExplicitLabelsText = (el) => {
    const labelTextCandidates = []
    for (let label of el.labels || []) {
        labelTextCandidates.push(...extractElementStrings(label))
    }
    if (el.hasAttribute('aria-label')) {
        labelTextCandidates.push(removeExcessWhitespace(el.getAttribute('aria-label')))
    }

    // Try to access another element if it was marked as the label for this input/select
    const ariaLabelAttr = removeExcessWhitespace(el.getAttribute('aria-labelled') || el.getAttribute('aria-labelledby'))

    if (ariaLabelAttr) {
        const labelledByElement = document.getElementById(ariaLabelAttr)
        if (labelledByElement) {
            labelTextCandidates.push(...extractElementStrings(labelledByElement))
        }
    }

    // Labels with long text are likely to be noisy and lead to false positives
    const filteredLabels = labelTextCandidates.filter((string) => string.length < 65)

    if (filteredLabels.length > 0) {
        return filteredLabels.join(' ')
    }

    return ''
}

/**
 * Get all text close to the input (useful when no labels are defined)
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {string}
 */
const getRelatedText = (el, form, cssSelector) => {
    let scope = getLargestMeaningfulContainer(el, form, cssSelector)

    // If we didn't find a container, try looking for an adjacent label
    if (scope === el) {
        if (el.previousElementSibling instanceof HTMLLabelElement) {
            scope = el.previousElementSibling
        }
    }

    // If there is still no meaningful container return empty string
    if (scope === el || scope.nodeName === 'SELECT') return ''

    // If the container has a select element, remove its contents to avoid noise
    const text = removeExcessWhitespace(extractElementStrings(scope).join(' '))
    // If the text is longer than n chars it's too noisy and likely to yield false positives, so return ''
    if (text.length < TEXT_LENGTH_CUTOFF) return text
    return ''
}

/**
 * Find a container for the input field that won't contain other inputs (useful to get elements related to the field)
 * @param {HTMLElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {HTMLElement}
 */
const getLargestMeaningfulContainer = (el, form, cssSelector) => {
    /* TODO: there could be more than one select el for the same label, in that case we should
        change how we compute the container */
    const parentElement = el.parentElement
    if (!parentElement || el === form) return el

    const inputsInParentsScope = parentElement.querySelectorAll(cssSelector)
    // To avoid noise, ensure that our input is the only in scope
    if (inputsInParentsScope.length === 1) {
        return getLargestMeaningfulContainer(parentElement, form, cssSelector)
    }
    return el
}

/**
 * Find a regex match for a given input
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {RegExpMatchArray|null}
 */
const matchInPlaceholderAndLabels = (input, regex, form, cssSelector) => {
    return input.placeholder?.match(regex) ||
        getExplicitLabelsText(input).match(regex) ||
        getRelatedText(input, form, cssSelector).match(regex)
}

/**
 * Check if a given input matches a regex
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {boolean}
 */
const checkPlaceholderAndLabels = (input, regex, form, cssSelector) => {
    return !!matchInPlaceholderAndLabels(input, regex, form, cssSelector)
}

/**
 * Creating Regex instances can throw, so we add this to be
 * @param {string} string
 * @returns {RegExp | undefined} string
 */
const safeRegex = (string) => {
    try {
        // This is lower-cased here because giving a `i` on a regex flag is a performance problem in some cases
        const input = String(string).toLowerCase().normalize('NFKC')
        return new RegExp(input, 'u')
    } catch (e) {
        console.warn('Could not generate regex from string input', string)
        return undefined
    }
}

/**
 * Factory for instances of Matching
 *
 * @return {Matching}
 */
function createMatching () {
    return new Matching(matchingConfiguration)
}

export {
    getInputType,
    getInputSubtype,
    getSubtypeFromType,
    removeExcessWhitespace,
    getInputMainType,
    getMainTypeFromType,
    getExplicitLabelsText,
    getRelatedText,
    matchInPlaceholderAndLabels,
    checkPlaceholderAndLabels,
    safeRegex,
    Matching,
    createMatching
}
