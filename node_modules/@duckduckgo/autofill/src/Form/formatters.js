import { matchInPlaceholderAndLabels, checkPlaceholderAndLabels } from './matching'
import { COUNTRY_CODES_TO_NAMES, COUNTRY_NAMES_TO_CODES } from './countryNames'

// Matches strings like mm/yy, mm-yyyy, mm-aa
const DATE_SEPARATOR_REGEX = /\w\w\s?(?<separator>[/\s.\-_—–])\s?\w\w/i
// Matches 4 non-digit repeated characters (YYYY or AAAA) or 4 digits (2022)
const FOUR_DIGIT_YEAR_REGEX = /(\D)\1{3}|\d{4}/i

/**
 * Format the cc year to best adapt to the input requirements (YY vs YYYY)
 * @param {HTMLInputElement} input
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */
const formatCCYear = (input, year, form) => {
    const selector = form.matching.cssSelector('FORM_INPUTS_SELECTOR')
    if (
        input.maxLength === 4 ||
        checkPlaceholderAndLabels(input, FOUR_DIGIT_YEAR_REGEX, form.form, selector)
    ) return year

    return `${Number(year) - 2000}`
}

/**
 * Get a unified expiry date with separator
 * @param {HTMLInputElement} input
 * @param {string} month
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */
const getUnifiedExpiryDate = (input, month, year, form) => {
    const formattedYear = formatCCYear(input, year, form)
    const paddedMonth = `${month}`.padStart(2, '0')
    const cssSelector = form.matching.cssSelector('FORM_INPUTS_SELECTOR')
    const separator = matchInPlaceholderAndLabels(input, DATE_SEPARATOR_REGEX, form.form, cssSelector)?.groups?.separator || '/'

    return `${paddedMonth}${separator}${formattedYear}`
}

const formatFullName = ({firstName = '', middleName = '', lastName = ''}) =>
    `${firstName} ${middleName ? middleName + ' ' : ''}${lastName}`.trim()

/**
 * Tries to look up a human-readable country name from the country code
 * @param {string} locale
 * @param {string} addressCountryCode
 * @return {string} - Returns the country code if we can't find a name
 */
const getCountryDisplayName = (locale, addressCountryCode) => {
    try {
        const regionNames = new Intl.DisplayNames([locale], { type: 'region' })
        // Adding this ts-ignore to prevent having to change this implementation.
        // @ts-ignore
        return regionNames.of(addressCountryCode)
    } catch (e) {
        return COUNTRY_CODES_TO_NAMES[addressCountryCode] || addressCountryCode
    }
}

/**
 * Tries to infer the element locale or returns 'en'
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string | 'en'}
 */
const inferElementLocale = (el) =>
    el.lang || el.form?.lang || document.body.lang || document.documentElement.lang || 'en'

/**
 * Tries to format the country code into a localised country name
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {{addressCountryCode?: string}} options
 */
const getCountryName = (el, options = {}) => {
    const {addressCountryCode} = options
    if (!addressCountryCode) return ''

    // Try to infer the field language or fallback to en
    const elLocale = inferElementLocale(el)
    const localisedCountryName = getCountryDisplayName(elLocale, addressCountryCode)

    // If it's a select el we try to find a suitable match to autofill
    if (el.nodeName === 'SELECT') {
        const englishCountryName = getCountryDisplayName('en', addressCountryCode)
        // This regex matches both the localised and English country names
        const countryNameRegex = new RegExp(String.raw`${
            localisedCountryName.replace(/ /g, '.?')
        }|${
            englishCountryName.replace(/ /g, '.?')
        }`, 'i')
        const countryCodeRegex = new RegExp(String.raw`\b${addressCountryCode}\b`, 'i')

        // We check the country code first because it's more accurate
        if (el instanceof HTMLSelectElement) {
            for (const option of el.options) {
                if (countryCodeRegex.test(option.value)) {
                    return option.value
                }
            }

            for (const option of el.options) {
                if (
                    countryNameRegex.test(option.value) ||
                    countryNameRegex.test(option.innerText)
                ) return option.value
            }
        }
    }

    return localisedCountryName
}

/**
 * Try to get a map of localised country names to code, or falls back to the English map
 * @param {HTMLInputElement | HTMLSelectElement} el
 */
const getLocalisedCountryNamesToCodes = (el) => {
    if (typeof Intl.DisplayNames !== 'function') return COUNTRY_NAMES_TO_CODES

    // Try to infer the field language or fallback to en
    const elLocale = inferElementLocale(el)

    return Object.fromEntries(
        Object.entries(COUNTRY_CODES_TO_NAMES)
            .map(([code]) => [getCountryDisplayName(elLocale, code), code])
    )
}

/**
 * Try to infer a country code from an element we identified as identities.addressCountryCode
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string}
 */
const inferCountryCodeFromElement = (el) => {
    if (COUNTRY_CODES_TO_NAMES[el.value]) return el.value
    if (COUNTRY_NAMES_TO_CODES[el.value]) return COUNTRY_NAMES_TO_CODES[el.value]

    const localisedCountryNamesToCodes = getLocalisedCountryNamesToCodes(el)
    if (localisedCountryNamesToCodes[el.value]) return localisedCountryNamesToCodes[el.value]

    if (el instanceof HTMLSelectElement) {
        const selectedText = el.selectedOptions[0]?.text
        if (COUNTRY_CODES_TO_NAMES[selectedText]) return selectedText
        if (COUNTRY_NAMES_TO_CODES[selectedText]) return localisedCountryNamesToCodes[selectedText]
        if (localisedCountryNamesToCodes[selectedText]) return localisedCountryNamesToCodes[selectedText]
    }
    return ''
}

/**
 * Gets separate expiration month and year from a single string
 * @param {string} expiration
 * @return {{expirationYear: string, expirationMonth: string}}
 */
const getMMAndYYYYFromString = (expiration) => {
    const values = expiration.match(/(\d+)/g) || []
    return values?.reduce((output, current) => {
        if (Number(current) > 12) {
            output.expirationYear = current.padStart(4, '20')
        } else {
            output.expirationMonth = current.padStart(2, '0')
        }
        return output
    }, {expirationYear: '', expirationMonth: ''})
}

/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */
const shouldStoreCredentials = ({credentials}) =>
    Boolean(credentials.password)

/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */
const shouldStoreIdentities = ({identities}) =>
    Boolean(
        (identities.firstName || identities.fullName) &&
        identities.addressStreet &&
        identities.addressCity
    )

/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */
const shouldStoreCreditCards = ({creditCards}) => {
    if (!creditCards.cardNumber) return false
    if (creditCards.cardSecurityCode) return true
    // Some forms (Amazon) don't have the cvv, so we still save if there's the expiration
    if (creditCards.expiration) return true
    // Expiration can also be two separate values
    return Boolean(creditCards.expirationYear && creditCards.expirationMonth)
}

/**
 * Formats form data into an object to send to the device for storage
 * If values are insufficient for a complete entry, they are discarded
 * @param {InternalDataStorageObject} formValues
 * @return {DataStorageObject}
 */
const prepareFormValuesForStorage = (formValues) => {
    /** @type {Partial<InternalDataStorageObject>} */
    let {credentials, identities, creditCards} = formValues

    // If we have an identity name but not a card name, copy it over there
    if (!creditCards.cardName && (identities?.fullName || identities?.firstName)) {
        creditCards.cardName = identities?.fullName || formatFullName(identities)
    }

    /** Fixes for credentials **/
    // Don't store if there isn't enough data
    if (shouldStoreCredentials(formValues)) {
        // If we don't have a username to match a password, let's see if the email is available
        if (credentials.password && !credentials.username && identities.emailAddress) {
            credentials.username = identities.emailAddress
        }
    } else {
        credentials = undefined
    }

    /** Fixes for identities **/
    // Don't store if there isn't enough data
    if (shouldStoreIdentities(formValues)) {
        if (identities.fullName) {
            // when forms have both first/last and fullName we keep the individual values and drop the fullName
            if (!(identities.firstName && identities.lastName)) {
                // If the fullname can be easily split into two, we'll store it as first and last
                const nameParts = identities.fullName.trim().split(/\s+/)
                if (nameParts.length === 2) {
                    identities.firstName = nameParts[0]
                    identities.lastName = nameParts[1]
                } else {
                    // If we can't split it, just store it as first name
                    identities.firstName = identities.fullName
                }
            }
            delete identities.fullName
        }
    } else {
        identities = undefined
    }

    /** Fixes for credit cards **/
    // Don't store if there isn't enough data
    if (shouldStoreCreditCards(formValues)) {
        if (creditCards.expiration) {
            const {expirationMonth, expirationYear} = getMMAndYYYYFromString(creditCards.expiration)
            creditCards.expirationMonth = expirationMonth
            creditCards.expirationYear = expirationYear
            delete creditCards.expiration
        }
        creditCards.expirationYear = creditCards.expirationYear?.padStart(4, '20')
        if (creditCards.cardNumber) {
            creditCards.cardNumber = creditCards.cardNumber.replace(/\D/g, '')
        }
    } else {
        creditCards = undefined
    }

    return {credentials, identities, creditCards}
}

export {
    formatCCYear,
    getUnifiedExpiryDate,
    formatFullName,
    getCountryDisplayName,
    getCountryName,
    inferCountryCodeFromElement,
    getMMAndYYYYFromString,
    prepareFormValuesForStorage
}
