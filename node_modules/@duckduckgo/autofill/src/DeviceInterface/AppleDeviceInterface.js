import InterfacePrototype from './InterfacePrototype.js'
import { formatDuckAddress, autofillEnabled } from '../autofill-utils'
import { processConfig } from '@duckduckgo/content-scope-scripts/src/apple-utils'
import { defaultOptions } from '../UI/HTMLTooltip'
import { HTMLTooltipUIController } from '../UI/controllers/HTMLTooltipUIController'
import { OverlayUIController } from '../UI/controllers/OverlayUIController'
import { createNotification, createRequest } from '../../packages/device-api'
import { GetAlias } from '../deviceApiCalls/additionalDeviceApiCalls'
import { NativeUIController } from '../UI/controllers/NativeUIController'

class AppleDeviceInterface extends InterfacePrototype {
    /** @override */
    initialSetupDelayMs = 300

    async isEnabled () {
        return autofillEnabled(this.globalConfig, processConfig)
    }

    /**
     * The default functionality of this class is to operate as an 'overlay controller' -
     * which means it's purpose is to message the native layer about when to open/close the overlay.
     *
     * There is an additional use-case though, when running on older macOS versions, we just display the
     * HTMLTooltip in-page (like the extension does). This is why the `!this.globalConfig.supportsTopFrame`
     * check exists below - if we know we don't support the overlay, we fall back to in-page.
     *
     * @override
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController () {
        if (this.globalConfig.userPreferences?.platform?.name === 'ios') {
            return new NativeUIController({
                onPointerDown: (event) => this._onPointerDown(event)
            })
        }

        if (!this.globalConfig.supportsTopFrame) {
            const options = {
                ...defaultOptions,
                testMode: this.isTestMode()
            }
            return new HTMLTooltipUIController({
                device: this,
                tooltipKind: 'modern',
                onPointerDown: (e) => this._onPointerDown(e)
            }, options)
        }

        /**
         * If we get here, we're just a controller for an overlay
         */
        return new OverlayUIController({
            remove: async () => this._closeAutofillParent(),
            show: async (details) => this._show(details),
            onPointerDown: (event) => this._onPointerDown(event)
        })
    }

    /**
     * For now, this could be running
     *  1) on iOS
     *  2) on macOS + Overlay
     *  3) on macOS + in-page HTMLTooltip
     *
     * @override
     * @returns {Promise<void>}
     */
    async setupAutofill () {
        if (this.globalConfig.isApp) {
            await this._getAutofillInitData()
        }

        const signedIn = await this._checkDeviceSignedIn()
        if (signedIn) {
            if (this.globalConfig.isApp) {
                await this.getAddresses()
            }
        }
    }

    async postInit () {
        if (this.isDeviceSignedIn()) {
            this.scanner.forms.forEach(form => form.redecorateAllInputs())
        }
        const cleanup = this.scanner.init()
        this.addLogoutListener(cleanup)
    }

    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData () {
        return this.deviceApi.request(createRequest('emailHandlerGetUserData'))
    }

    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities () {
        return this.deviceApi.request(createRequest('emailHandlerGetCapabilities'))
    }

    /**
     */
    async getSelectedCredentials () {
        return this.deviceApi.request(createRequest('getSelectedCredentials'))
    }

    /**
     * @param {import('../UI/controllers/OverlayUIController.js').ShowAutofillParentRequest} parentArgs
     */
    async _showAutofillParent (parentArgs) {
        return this.deviceApi.notify(createNotification('showAutofillParent', parentArgs))
    }

    /**
     * @returns {Promise<any>}
     */
    async _closeAutofillParent () {
        return this.deviceApi.notify(createNotification('closeAutofillParent', {}))
    }

    /**
     * @param {import('../UI/controllers/OverlayUIController.js').ShowAutofillParentRequest} details
     */
    async _show (details) {
        await this._showAutofillParent(details)
        this._listenForSelectedCredential()
            .then((response) => {
                if (!response) {
                    return
                }
                this.activeFormSelectedDetail(response.data, response.configType)
            })
            .catch(e => {
                console.error('unknown error', e)
            })
    }

    async getAddresses () {
        if (!this.globalConfig.isApp) return this.getAlias()

        const {addresses} = await this.deviceApi.request(createRequest('emailHandlerGetAddresses'))
        this.storeLocalAddresses(addresses)
        return addresses
    }

    async refreshAlias () {
        await this.deviceApi.notify(createNotification('emailHandlerRefreshAlias'))
        // On macOS we also update the addresses stored locally
        if (this.globalConfig.isApp) this.getAddresses()
    }

    async _checkDeviceSignedIn () {
        const {isAppSignedIn} = await this.deviceApi.request(createRequest('emailHandlerCheckAppSignedInStatus'))
        this.isDeviceSignedIn = () => !!isAppSignedIn
        return !!isAppSignedIn
    }

    storeUserData ({addUserData: {token, userName, cohort}}) {
        return this.deviceApi.notify(createNotification('emailHandlerStoreToken', { token, username: userName, cohort }))
    }

    /**
     * Used by the email web app
     * Provides functionality to log the user out
     */
    removeUserData () {
        this.deviceApi.notify(createNotification('emailHandlerRemoveToken'))
    }

    /**
     * PM endpoints
     */

    /**
     * Sends credentials to the native layer
     * @param {{username: string, password: string}} credentials
     */
    storeCredentials (credentials) {
        return this.deviceApi.notify(createNotification('pmHandlerStoreCredentials', credentials))
    }

    /**
     * Sends form data to the native layer
     * @deprecated should use the base implementation once available on Apple devices (instead of this override)
     * @param {DataStorageObject} data
     */
    storeFormData (data) {
        this.deviceApi.notify(createNotification('pmHandlerStoreData', data))
    }

    /**
     * Gets the init data from the device
     * @returns {APIResponse<PMData>}
     */
    async _getAutofillInitData () {
        const response = await this.deviceApi.request(createRequest('pmHandlerGetAutofillInitData'))
        this.storeLocalData(response.success)
        return response
    }

    /**
     * Gets credentials ready for autofill
     * @param {Number} id - the credential id
     * @returns {APIResponseSingle<CredentialsObject>}
     */
    getAutofillCredentials (id) {
        return this.deviceApi.request(createRequest('pmHandlerGetAutofillCredentials', { id }))
    }

    /**
     * Opens the native UI for managing passwords
     */
    openManagePasswords () {
        return this.deviceApi.notify(createNotification('pmHandlerOpenManagePasswords'))
    }

    /**
     * Opens the native UI for managing identities
     */
    openManageIdentities () {
        return this.deviceApi.notify(createNotification('pmHandlerOpenManageIdentities'))
    }

    /**
     * Opens the native UI for managing credit cards
     */
    openManageCreditCards () {
        return this.deviceApi.notify(createNotification('pmHandlerOpenManageCreditCards'))
    }

    /**
     * Gets a single identity obj once the user requests it
     * @param {Number} id
     * @returns {Promise<{success: IdentityObject|undefined}>}
     */
    getAutofillIdentity (id) {
        const identity = this.getLocalIdentities().find(({id: identityId}) => `${identityId}` === `${id}`)
        return Promise.resolve({success: identity})
    }

    /**
     * Gets a single complete credit card obj once the user requests it
     * @param {Number} id
     * @returns {APIResponse<CreditCardObject>}
     */
    getAutofillCreditCard (id) {
        return this.deviceApi.request(createRequest('pmHandlerGetCreditCard', { id }))
    }

    async getCurrentInputType () {
        const {inputType} = this.getTopContextData() || {}
        return inputType || 'unknown'
    }

    /**
     * @returns {Promise<string>}
     */
    async getAlias () {
        const {alias} = await this.deviceApi.request(new GetAlias({
            requiresUserPermission: !this.globalConfig.isApp,
            shouldConsumeAliasIfProvided: !this.globalConfig.isApp
        }))
        return formatDuckAddress(alias)
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

    /** @type {any} */
    pollingTimeout = null;
    /**
     * Poll the native listener until the user has selected a credential.
     * Message return types are:
     * - 'stop' is returned whenever the message sent doesn't match the native last opened tooltip.
     *     - This also is triggered when the close event is called and prevents any edge case continued polling.
     * - 'ok' is when the user has selected a credential and the value can be injected into the page.
     * - 'none' is when the tooltip is open in the native window however hasn't been entered.
     * @returns {Promise<{data:IdentityObject|CreditCardObject|CredentialsObject, configType: string} | null>}
     */
    async _listenForSelectedCredential () {
        return new Promise((resolve) => {
            // Prevent two timeouts from happening
            // @ts-ignore
            const poll = async () => {
                clearTimeout(this.pollingTimeout)
                const response = await this.getSelectedCredentials()
                switch (response.type) {
                case 'none':
                    // Parent hasn't got a selected credential yet
                    // @ts-ignore
                    this.pollingTimeout = setTimeout(() => {
                        poll()
                    }, 100)
                    return
                case 'ok': {
                    return resolve({data: response.data, configType: response.configType})
                }
                case 'stop':
                    // Parent wants us to stop polling
                    resolve(null)
                    break
                }
            }
            poll()
        })
    }
}

export {AppleDeviceInterface}
