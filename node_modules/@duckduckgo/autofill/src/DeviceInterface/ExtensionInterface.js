import InterfacePrototype from './InterfacePrototype.js'

import {
    SIGN_IN_MSG,
    sendAndWaitForAnswer,
    setValue,
    formatDuckAddress
} from '../autofill-utils'

class ExtensionInterface extends InterfacePrototype {
    isDeviceSignedIn () {
        return this.hasLocalAddresses
    }

    async setupAutofill () {
        await this._addDeviceListeners()

        return this.getAddresses().then(_addresses => {
            if (this.hasLocalAddresses) {
                // todo(Shane): Should we re-evaluate input types now?
                this.availableInputTypes = {
                    ...this.availableInputTypes,
                    email: true
                }
                const cleanup = this.scanner.init()
                // todo(Shane): Should we re-evaluate input types now?
                this.addLogoutListener(() => {
                    cleanup()
                    this.availableInputTypes = {
                        ...this.availableInputTypes,
                        email: false
                    }
                })
            }
        })
    }

    getAddresses () {
        return new Promise(resolve => chrome.runtime.sendMessage(
            {getAddresses: true},
            (data) => {
                this.storeLocalAddresses(data)
                return resolve(data)
            }
        ))
    }

    getUserData () {
        return new Promise(resolve => chrome.runtime.sendMessage(
            {getUserData: true},
            (data) => resolve(data)
        ))
    }

    refreshAlias () {
        return chrome.runtime.sendMessage(
            {refreshAlias: true},
            (addresses) => this.storeLocalAddresses(addresses)
        )
    }

    async trySigningIn () {
        if (this.globalConfig.isDDGDomain) {
            const data = await sendAndWaitForAnswer(SIGN_IN_MSG, 'addUserData')
            this.storeUserData(data)
        }
    }

    storeUserData (data) {
        return chrome.runtime.sendMessage(data)
    }

    _addDeviceListeners () {
        // Add contextual menu listeners
        let activeEl = null
        document.addEventListener('contextmenu', e => {
            activeEl = e.target
        })

        chrome.runtime.onMessage.addListener((message, sender) => {
            if (sender.id !== chrome.runtime.id) return

            switch (message.type) {
            case 'ddgUserReady':
                this.setupAutofill().then(() => {
                    this.setupSettingsPage({shouldLog: true})
                })
                break
            case 'contextualAutofill':
                setValue(activeEl, formatDuckAddress(message.alias), this.globalConfig)
                activeEl.classList.add('ddg-autofilled')
                this.refreshAlias()

                // If the user changes the alias, remove the decoration
                activeEl.addEventListener(
                    'input',
                    (e) => e.target.classList.remove('ddg-autofilled'),
                    {once: true}
                )
                break
            default:
                break
            }
        })
    }

    addLogoutListener (handler) {
        // Cleanup on logout events
        chrome.runtime.onMessage.addListener((message, sender) => {
            if (sender.id === chrome.runtime.id && message.type === 'logout') {
                handler()
            }
        })
    }

    /** @override */
    tooltipStyles () {
        return `<link rel="stylesheet" href="${chrome.runtime.getURL('public/css/autofill.css')}" crossorigin="anonymous">`
    }
}

export { ExtensionInterface }
