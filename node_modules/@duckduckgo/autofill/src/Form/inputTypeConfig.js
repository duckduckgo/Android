import { daxBase64 } from './logo-svg'
import * as ddgPasswordIcons from '../UI/img/ddgPasswordIcon'
import { getInputType, getMainTypeFromType, getInputSubtype } from './matching'
import { createCredentialsTooltipItem } from '../InputTypes/Credentials'
import { CreditCardTooltipItem } from '../InputTypes/CreditCard'
import { IdentityTooltipItem } from '../InputTypes/Identity'

/**
 * Get the icon for the identities (currently only Dax for emails)
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {string}
 */
const getIdentitiesIcon = (input, {device}) => {
    // In Firefox web_accessible_resources could leak a unique user identifier, so we avoid it here
    const { isDDGApp, isFirefox, isWindows } = device.globalConfig
    const getDaxImg = isDDGApp || isFirefox || isWindows ? daxBase64 : chrome.runtime.getURL('img/logo-small.svg')
    const subtype = getInputSubtype(input)

    if (subtype === 'emailAddress') {
        // todo(Shane): Needs runtime polymorphism again here
        if (device.availableInputTypes.email) {
            return getDaxImg
        }
    }

    return ''
}

/**
 * Inputs with readOnly or disabled should never be decorated
 * @param {HTMLInputElement} input
 * @return {boolean}
 */
const canBeDecorated = (input) => !input.readOnly && !input.disabled

/**
 * A map of config objects. These help by centralising here some complexity
 * @type {InputTypeConfig}
 */
const inputTypeConfig = {
    /** @type {CredentialsInputTypeConfig} */
    credentials: {
        type: 'credentials',
        getIconBase: () => ddgPasswordIcons.ddgPasswordIconBase,
        getIconFilled: () => ddgPasswordIcons.ddgPasswordIconFilled,
        shouldDecorate: (input, {isLogin, device}) => {
            // if we are on a 'login' page, continue to use old logic, eg: just checking if there's a
            // saved password
            if (isLogin) {
                return true
            }

            // at this point, it's not a 'login' attempt, so we could offer to provide a password?
            // todo(Shane): move this
            if (device.autofillSettings.featureToggles.password_generation) {
                const subtype = getInputSubtype(input)
                if (subtype === 'password') {
                    return true
                }
            }

            return false
        },
        dataType: 'Credentials',
        tooltipItem: (data) => createCredentialsTooltipItem(data)
    },
    /** @type {CreditCardsInputTypeConfig} */
    creditCards: {
        type: 'creditCards',
        getIconBase: () => '',
        getIconFilled: () => '',
        shouldDecorate: (_input, {device}) =>
            // todo(Shane): shouldn't need this hasLocalCreditCards check with toggles
            canBeDecorated(_input) && device.hasLocalCreditCards,
        dataType: 'CreditCards',
        tooltipItem: (data) => new CreditCardTooltipItem(data)
    },
    /** @type {IdentitiesInputTypeConfig} */
    identities: {
        type: 'identities',
        getIconBase: getIdentitiesIcon,
        getIconFilled: getIdentitiesIcon,
        shouldDecorate: (_input, {device}) => {
            if (!canBeDecorated(_input)) {
                console.warn('cannot be decorated')
                return false
            }

            const subtype = getInputSubtype(_input)

            // todo(Shane): Handle the mac specific logic also with feature toggles
            if (device.globalConfig.isApp) {
                return Boolean(device.getLocalIdentities()?.some((identity) => !!identity[subtype]))
            }

            // if it's email then we can always decorate
            if (subtype === 'emailAddress') {
                return Boolean(device.availableInputTypes.email)
            }

            return false
        },
        dataType: 'Identities',
        tooltipItem: (data) => new IdentityTooltipItem(data)
    },
    /** @type {UnknownInputTypeConfig} */
    unknown: {
        type: 'unknown',
        getIconBase: () => '',
        getIconFilled: () => '',
        shouldDecorate: () => false,
        dataType: '',
        tooltipItem: (_data) => {
            throw new Error('unreachable')
        }
    }
}

/**
 * Retrieves configs from an input el
 * @param {HTMLInputElement} input
 * @returns {InputTypeConfigs}
 */
const getInputConfig = (input) => {
    const inputType = getInputType(input)
    return getInputConfigFromType(inputType)
}

/**
 * Retrieves configs from an input type
 * @param {import('./matching').SupportedTypes | string} inputType
 * @returns {InputTypeConfigs}
 */
const getInputConfigFromType = (inputType) => {
    const inputMainType = getMainTypeFromType(inputType)
    return inputTypeConfig[inputMainType]
}

export {
    getInputConfig,
    getInputConfigFromType
}
