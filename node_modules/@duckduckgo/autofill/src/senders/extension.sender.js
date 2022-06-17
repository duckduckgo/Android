import {Sender} from './sender'
import {GetAvailableInputTypes, GetRuntimeConfiguration} from '../messages/messages'
import {isFeatureEnabledFromProcessedConfig} from '@duckduckgo/content-scope-scripts'

export class ExtensionSender extends Sender {
    async handle (msg) {
        if (msg instanceof GetRuntimeConfiguration) {
            return handlers.getRuntimeConfiguration(msg)
        }
        if (msg instanceof GetAvailableInputTypes) {
            return handlers.getAvailableInputTypes(msg)
        }
        throw new Error('not implemented yet')
    }
}

/**
 * Try to send a message to the Extension.
 *
 * This will try to detect if you've called a handler that's not available in the extension,
 * if so, it will throw a known message so that you can decide to fallback/recover if possible.
 *
 * For example, this can help when implementing new messaging
 *
 * @param {Record<string, any>} input
 * @returns {Promise<any>}
 */
function sendToExtension (input) {
    return new Promise((resolve, reject) => {
        chrome.runtime.sendMessage(
            input,
            (data) => {
                if (typeof data === 'undefined') {
                    reject(new Error('Unknown extension error for message: ' + name))
                } else {
                    return resolve(data)
                }
            }
        )
    })
}

const handlers = {
    /**
     * This is a stub for the new message until the extension supports it
     *
     * @param {GetAvailableInputTypes} msg
     * @returns {Promise<any>}
     */
    'getAvailableInputTypes': async (msg) => {
        return {
            success: msg.response({
                email: false,
                identities: false,
                credentials: false,
                creditCards: false
            })
        }
    },
    /**
     * This is a stub for the new message until the extension supports it
     *
     * @param {GetRuntimeConfiguration} msg
     */
    'getRuntimeConfiguration': async (msg) => {
        const extensionResponse = await sendToExtension({
            registeredTempAutofillContentScript: true,
            documentUrl: window.location.href
        })

        const enabled = isFeatureEnabledFromProcessedConfig(extensionResponse, 'autofill')

        /**
         * @type {FeatureToggles}
         */
        const featureToggles = {
            'inputType_credentials': false,
            'inputType_identities': false,
            'inputType_creditCards': false,
            'emailProtection': true,
            'password_generation': false,
            'credentials_saving': false
        }

        const response = msg.response({
            contentScope: {
                features: {
                    autofill: {
                        state: enabled ? 'enabled' : 'disabled',
                        exceptions: []
                    }
                },
                unprotectedTemporary: []
            },
            userPreferences: {
                // @ts-ignore
                sessionKey: '',
                debug: false,
                globalPrivacyControlValue: false,
                platform: {name: 'extension'},
                features: {
                    autofill: {
                        settings: {
                            featureToggles: featureToggles
                        }
                    }
                }
            },
            userUnprotectedDomains: []
        })

        return { success: response }
    }
}
