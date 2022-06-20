import {DeviceApiTransport} from '../../../packages/device-api'
import {
    GetAutofillDataCall,
    GetAvailableInputTypesCall,
    GetRuntimeConfigurationCall,
    StoreFormDataCall
} from '../__generated__/deviceApiCalls'

export class AndroidTransport extends DeviceApiTransport {
    /** @type {GlobalConfig} */
    config

    /** @param {GlobalConfig} globalConfig */
    constructor (globalConfig) {
        super()
        this.config = globalConfig
    }
    /**
     * @param {import("../../../packages/device-api").DeviceApiCall} deviceApiCall
     * @returns {Promise<any>}
     */
    async send (deviceApiCall) {
        if (deviceApiCall instanceof GetRuntimeConfigurationCall) {
            return androidSpecificRuntimeConfiguration(this.config)
        }

        if (deviceApiCall instanceof GetAvailableInputTypesCall) {
            return androidSpecificAvailableInputTypes(this.config)
        }

        if (deviceApiCall instanceof GetAutofillDataCall) {
            window.BrowserAutofill.getAutofillData(JSON.stringify(deviceApiCall.params))
            return waitForResponse(deviceApiCall.id, this.config)
        }
        if (deviceApiCall instanceof StoreFormDataCall) {
            return window.BrowserAutofill.storeFormData(JSON.stringify(deviceApiCall.params))
        }
        throw new Error('android: not implemented: ' + deviceApiCall.method)
    }
}

/**
 * @param {string} expectedResponse - the name/id of the response
 * @param {GlobalConfig} config
 * @returns {Promise<*>}
 */
function waitForResponse (expectedResponse, config) {
    return new Promise((resolve) => {
        const handler = e => {
            if (!config.isDDGTestMode) {
                if (e.origin !== '') {
                    return
                }
            }
            if (!e.data) {
                return
            }
            if (typeof e.data !== 'string') {
                if (config.isDDGTestMode) {
                    console.log('❌ event.data was not a string. Expected a string so that it can be JSON parsed')
                }
                return
            }
            try {
                let data = JSON.parse(e.data)
                if (data.type === expectedResponse) {
                    window.removeEventListener('message', handler)
                    return resolve(data)
                }
                if (config.isDDGTestMode) {
                    console.log(`❌ event.data.type was '${data.type}', which didnt match '${expectedResponse}'`, JSON.stringify(data))
                }
            } catch (e) {
                window.removeEventListener('message', handler)
                if (config.isDDGTestMode) {
                    console.log('❌ Could not JSON.parse the response')
                }
            }
        }
        window.addEventListener('message', handler)
    })
}

/**
 * @param {GlobalConfig} globalConfig
 * @returns {{success: import('../__generated__/validators-ts').RuntimeConfiguration}}
 */
function androidSpecificRuntimeConfiguration (globalConfig) {
    if (!globalConfig.userPreferences) {
        throw new Error('globalConfig.userPreferences not supported yet on Android')
    }
    return {
        success: {
            // @ts-ignore
            contentScope: globalConfig.contentScope,
            // @ts-ignore
            userPreferences: globalConfig.userPreferences,
            // @ts-ignore
            userUnprotectedDomains: globalConfig.userUnprotectedDomains
        }
    }
}

/**
 * @param {GlobalConfig} globalConfig
 * @returns {{success: import('../__generated__/validators-ts').AvailableInputTypes}}
 */
function androidSpecificAvailableInputTypes (globalConfig) {
    if (!globalConfig.availableInputTypes) {
        throw new Error('globalConfig.availableInputTypes not supported yet on Android')
    }
    return {
        success: globalConfig.availableInputTypes
    }
}
