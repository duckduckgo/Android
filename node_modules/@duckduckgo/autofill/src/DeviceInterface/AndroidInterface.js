import InterfacePrototype from './InterfacePrototype.js'
import {autofillEnabled, sendAndWaitForAnswer} from '../autofill-utils'
import { NativeUIController } from '../UI/controllers/NativeUIController.js'
import {processConfig} from '@duckduckgo/content-scope-scripts/src/apple-utils'

class AndroidInterface extends InterfacePrototype {
    async isEnabled () {
        return autofillEnabled(this.globalConfig, processConfig)
    }

    async getAlias () {
        const { alias } = await sendAndWaitForAnswer(() => {
            return window.EmailInterface.showTooltip()
        }, 'getAliasResponse')
        return alias
    }

    /**
     * @override
     */
    createUIController () {
        return new NativeUIController({
            onPointerDown: (event) => this._onPointerDown(event)
        })
    }

    /**
     * @deprecated use `this.settings.availableInputTypes.email` in the future
     * @returns {boolean}
     */
    isDeviceSignedIn () {
        // on DDG domains, always check via `window.EmailInterface.isSignedIn()`
        if (this.globalConfig.isDDGDomain) {
            return window.EmailInterface.isSignedIn() === 'true'
        }

        // on non-DDG domains, where `availableInputTypes.email` is present, use it
        if (typeof this.globalConfig.availableInputTypes?.email === 'boolean') {
            return this.globalConfig.availableInputTypes.email
        }

        // ...on other domains we assume true because the script wouldn't exist otherwise
        return true
    }

    async setupAutofill () {

    }

    postInit () {
        const cleanup = this.scanner.init()
        this.addLogoutListener(cleanup)
    }
    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData () {
        let userData = null

        try {
            userData = JSON.parse(window.EmailInterface.getUserData())
        } catch (e) {
            if (this.globalConfig.isDDGTestMode) {
                console.error(e)
            }
        }

        return Promise.resolve(userData)
    }

    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities () {
        let deviceCapabilities = null

        try {
            deviceCapabilities = JSON.parse(window.EmailInterface.getDeviceCapabilities())
        } catch (e) {
            if (this.globalConfig.isDDGTestMode) {
                console.error(e)
            }
        }

        return Promise.resolve(deviceCapabilities)
    }

    storeUserData ({addUserData: {token, userName, cohort}}) {
        return window.EmailInterface.storeCredentials(token, userName, cohort)
    }

    /**
      * Used by the email web app
      * Provides functionality to log the user out
      */
    removeUserData () {
        try {
            return window.EmailInterface.removeCredentials()
        } catch (e) {
            if (this.globalConfig.isDDGTestMode) {
                console.error(e)
            }
        }
    }

    addLogoutListener (handler) {
        // Only deal with logging out if we're in the email web app
        if (!this.globalConfig.isDDGDomain) return

        window.addEventListener('message', (e) => {
            if (this.globalConfig.isDDGDomain && e.data.emailProtectionSignedOut) {
                handler()
            }
        })
    }
}

export {AndroidInterface}
