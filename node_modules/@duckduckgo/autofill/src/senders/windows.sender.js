import {Sender} from './sender'
// import getAutofillData from '../schema/response.getAutofillData.schema.json'
import getAutofillInitDataResponse from '../schema/response.getAutofillInitData.schema.json'
import getAvailableInputTypes from '../schema/response.getAvailableInputTypes.schema.json'
import getRuntimeConfiguration from '../schema/response.getRuntimeConfiguration.schema.json'

class WindowsSender extends Sender {
    async handle (message) {
        const { name, data } = message
        switch (name) {
        case 'setSize': {
            windowsTransport(name)
            break
        }
        case 'showAutofillParent': {
            windowsTransport(name, data)
            break
        }
        case 'closeAutofillParent': {
            windowsTransport(name)
            break
        }
        case 'getRuntimeConfiguration': {
            return windowsTransport(name)
                .withResponse(getRuntimeConfiguration.properties.type.const)
        }
        case 'getAvailableInputTypes': {
            return windowsTransport(name)
                .withResponse(getAvailableInputTypes.properties.type.const)
        }
        case 'getAutofillInitData': {
            return windowsTransport(name)
                .withResponse(getAutofillInitDataResponse.properties.type.const)
        }
        case 'storeFormData': {
            windowsTransport('storeFormData', data)
            break
        }
        case 'selectedDetail': {
            windowsTransport(name, data)
            break
        }
        case 'getAutofillCredentials': {
            // todo(Shane): Schema
            return windowsTransport('getAutofillCredentials', data)
                .withResponse('getAutofillCredentialsResponse')
        }
        default: throw new Error('windows: not implemented: ' + name)
        }
    }
}

export function createWindowsSender () {
    return new WindowsSender()
}

/**
 * @param {Names} name
 * @param {any} [data]
 */
function windowsTransport (name, data) {
    if (data) {
        window.chrome.webview.postMessage({ type: name, data: data })
    } else {
        window.chrome.webview.postMessage({ type: name })
    }
    return {
        /**
         * Sends a message and returns a Promise that resolves with the response
         * @param responseName
         * @returns {Promise<*>}
         */
        withResponse (responseName) {
            return new Promise((resolve) => {
                const handler = event => {
                    /* if (event.origin !== window.origin) {
                        console.warn(`origin mis-match. window.origin: ${window.origin}, event.origin: ${event.origin}`)
                        return
                    } */
                    if (!event.data) {
                        console.warn('data absent from message')
                        return
                    }
                    if (event.data.type === responseName) {
                        resolve(event.data)
                        window.chrome.webview.removeEventListener('message', handler)
                    }
                    // at this point we're confident we have the correct message type
                }
                window.chrome.webview.addEventListener('message', handler, {once: true})
            })
        }
    }
}
