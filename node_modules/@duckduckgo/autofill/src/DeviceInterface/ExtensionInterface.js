import InterfacePrototype from './InterfacePrototype.js'

import {
    SIGN_IN_MSG,
    sendAndWaitForAnswer,
    setValue,
    formatDuckAddress,
    isAutofillEnabledFromProcessedConfig
} from '../autofill-utils'
import {HTMLTooltipUIController} from '../UI/controllers/HTMLTooltipUIController'
import {defaultOptions} from '../UI/HTMLTooltip'

class ExtensionInterface extends InterfacePrototype {
    /**
     * @override
     */
    createUIController () {
        /** @type {import('../UI/HTMLTooltip.js').HTMLTooltipOptions} */
        const htmlTooltipOptions = {
            ...defaultOptions,
            css: `<link rel="stylesheet" href="${chrome.runtime.getURL('public/css/autofill.css')}" crossOrigin="anonymous">`,
            testMode: this.isTestMode()
        }
        return new HTMLTooltipUIController({ tooltipKind: 'legacy', device: this }, htmlTooltipOptions)
    }

    async isEnabled () {
        return new Promise(resolve => {
            chrome.runtime.sendMessage(
                {
                    registeredTempAutofillContentScript: true,
                    documentUrl: window.location.href
                },
                (response) => {
                    if (response && 'site' in response) {
                        resolve(isAutofillEnabledFromProcessedConfig(response))
                    }
                }
            )
        })
    }

    isDeviceSignedIn () {
        return this.hasLocalAddresses
    }

    setupAutofill () {
        return this.getAddresses()
    }

    postInit () {
        if (this.hasLocalAddresses) {
            const cleanup = this.scanner.init()
            this.addLogoutListener(cleanup)
        }
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

    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData () {
        return new Promise(resolve => chrome.runtime.sendMessage(
            {getUserData: true},
            (data) => resolve(data)
        ))
    }

    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities () {
        return new Promise(resolve => chrome.runtime.sendMessage(
            {getEmailProtectionCapabilities: true},
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

    /**
     * @param {object} message
     * @param {object} message.addUserData
     * @param {string} message.addUserData.token
     * @param {string} message.addUserData.userName
     * @param {string} message.addUserData.cohort
     */
    storeUserData (message) {
        return chrome.runtime.sendMessage(message)
    }

    /**
     * Used by the email web app
     * Provides functionality to log the user out
     */
    removeUserData () {
        return chrome.runtime.sendMessage({removeUserData: true})
    }

    addDeviceListeners () {
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
                    this.refreshSettings().then(() => {
                        this.setupSettingsPage({shouldLog: true}).then(() => {
                            return this.postInit()
                        })
                    })
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
}

export { ExtensionInterface }
