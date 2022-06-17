import {Sender} from './sender'
import {GetAutofillData, GetAvailableInputTypes, GetRuntimeConfiguration, StoreFormData} from '../messages/messages'

export class AndroidSender extends Sender {
    async handle (msg) {
        const { data } = msg

        if (msg instanceof GetRuntimeConfiguration) {
            window.BrowserAutofill.getRuntimeConfiguration()
            return waitForResponse(msg.responseName)
        }

        if (msg instanceof GetAvailableInputTypes) {
            window.BrowserAutofill.getAvailableInputTypes()
            return waitForResponse(msg.responseName)
        }

        if (msg instanceof GetAutofillData) {
            window.BrowserAutofill.getAutofillData(JSON.stringify(data))
            return waitForResponse(msg.responseName)
        }

        if (msg instanceof StoreFormData) {
            return window.BrowserAutofill.storeFormData(JSON.stringify(data))
        }

        throw new Error('android: not implemented: ' + msg.name)
    }
}

/**
 * Sends a message and returns a Promise that resolves with the response
 *
 * @param {string} expectedResponse - the name of the response
 * @returns {Promise<*>}
 */
function waitForResponse (expectedResponse) {
    return new Promise((resolve) => {
        const handler = e => {
            // todo(Shane): Allow blank string, try sandboxed iframe. allow-scripts
            // if (e.origin !== window.origin) {
            //     console.log(`❌ origin-mismatch e.origin(${e.origin}) !== window.origin(${window.origin})`);
            //     return
            // }
            console.warn('event.origin check was disabled on Android.')
            if (!e.data) {
                console.log('❌ event.data missing')
                return
            }
            if (typeof e.data !== 'string') {
                console.log('❌ event.data was not a string. Expected a string so that it can be JSON parsed')
                return
            }
            try {
                let data = JSON.parse(e.data)
                if (data.type === expectedResponse) {
                    window.removeEventListener('message', handler)
                    return resolve(data)
                }
                console.log(`❌ event.data.type was '${data.type}', which didnt match '${expectedResponse}'`, JSON.stringify(data))
            } catch (e) {
                window.removeEventListener('message', handler)
                console.log('❌ Could not JSON.parse the response')
            }
        }
        window.addEventListener('message', handler)
    })
}
