import InterfacePrototype from './InterfacePrototype.js'
import {formatDuckAddress} from '../autofill-utils'
import {CSS_STYLES} from '../UI/styles/styles'
import {createLegacyMessage, EmailRefreshAlias} from '../messages/messages'

class AppleDeviceInterface extends InterfacePrototype {
    /** @override */
    initialSetupDelayMs = 300

    async setupAutofill () {
        if (this.globalConfig.isApp) {
            const response = await this.getAutofillInitData()
            this.storeLocalData(response)
        }

        const signedIn = this.availableInputTypes.email

        if (signedIn) {
            if (this.globalConfig.isApp) {
                await this.getAddresses()
            }
            this.scanner.forms.forEach(form => form.redecorateAllInputs())
        }

        const cleanup = this.scanner.init()
        this.addLogoutListener(cleanup)
    }

    isDeviceSignedIn () {
        return Boolean(this.availableInputTypes.email)
    }

    getUserData () {
        return this.sender.send(createLegacyMessage('emailHandlerGetUserData'))
    }

    async getAddresses () {
        if (!this.globalConfig.isApp) return this.getAlias()

        const {addresses} = await this.sender.send(createLegacyMessage('emailHandlerGetAddresses'))
        this.storeLocalAddresses(addresses)
        return addresses
    }

    async refreshAlias () {
        await this.sender.send(new EmailRefreshAlias())
        // On macOS we also update the addresses stored locally
        if (this.globalConfig.isApp) this.getAddresses()
    }

    storeUserData ({addUserData: {token, userName, cohort}}) {
        return this.sender.send(createLegacyMessage('emailHandlerStoreToken', {token, username: userName, cohort}))
    }

    /**
     * PM endpoints
     */

    /**
     * Sends credentials to the native layer
     * @param {{username: string, password: string}} credentials
     * @deprecated
     */
    storeCredentials (credentials) {
        return this.sender.send(createLegacyMessage('pmHandlerStoreCredentials', credentials))
    }

    /**
     * Opens the native UI for managing passwords
     */
    openManagePasswords () {
        return this.sender.send(createLegacyMessage('pmHandlerOpenManagePasswords'))
    }

    /**
     * Opens the native UI for managing identities
     */
    openManageIdentities () {
        return this.sender.send(createLegacyMessage('pmHandlerOpenManageIdentities'))
    }

    /**
     * Opens the native UI for managing credit cards
     */
    openManageCreditCards () {
        return this.sender.send(createLegacyMessage('pmHandlerOpenManageCreditCards'))
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
        return this.sender.send(createLegacyMessage('pmHandlerGetCreditCard', {id}))
    }

    // Used to encode data to send back to the child autofill
    async selectedDetail (detailIn, configType) {
        this.activeFormSelectedDetail(detailIn, configType)
    }

    async getCurrentInputType () {
        const {inputType} = this.getTopContextData() || {}
        return inputType || 'unknown'
    }

    async getAlias () {
        const {alias} = await this.sender.send(createLegacyMessage('emailHandlerGetAlias', {
            requiresUserPermission: !this.globalConfig.isApp,
            shouldConsumeAliasIfProvided: !this.globalConfig.isApp
        }))
        return formatDuckAddress(alias)
    }

    tooltipStyles () {
        return `<style>${CSS_STYLES}</style>`
    }
}

export {AppleDeviceInterface}
