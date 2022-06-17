import InterfacePrototype from './InterfacePrototype.js'
import { sendAndWaitForAnswer } from '../autofill-utils'

class AndroidInterface extends InterfacePrototype {
    async getAlias () {
        const { alias } = await sendAndWaitForAnswer(() => {
            return window.EmailInterface.showTooltip()
        }, 'getAliasResponse')
        return alias
    }

    isDeviceSignedIn () {
        // isDeviceSignedIn is only available on DDG domains...
        if (this.globalConfig.isDDGDomain) return window.EmailInterface.isSignedIn() === 'true'

        // ...on other domains we assume true because the script wouldn't exist otherwise
        return true
    }

    async setupAutofill () {
        if (this.isDeviceSignedIn()) {
            const cleanup = this.scanner.init()
            this.addLogoutListener(cleanup)
        }
    }

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

    storeUserData ({addUserData: {token, userName, cohort}}) {
        return window.EmailInterface.storeCredentials(token, userName, cohort)
    }
}

export {AndroidInterface}
