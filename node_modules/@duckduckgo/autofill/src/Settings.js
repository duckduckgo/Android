import { validate } from '../packages/device-api'
import {GetAvailableInputTypesCall, GetRuntimeConfigurationCall} from './deviceApiCalls/__generated__/deviceApiCalls'
import {autofillSettingsSchema} from './deviceApiCalls/__generated__/validators.zod'

/**
 * Some Type helpers to prevent duplication
 * @typedef {import("./deviceApiCalls/__generated__/validators-ts").AutofillFeatureToggles} AutofillFeatureToggles
 * @typedef {import("./deviceApiCalls/__generated__/validators-ts").AvailableInputTypes} AvailableInputTypes
 * @typedef {import("../packages/device-api").DeviceApi} DeviceApi
 */

/**
 * The Settings class encapsulates the concept of 1) feature toggles + 2) available input types.
 *
 * 1) Feature toggles are boolean flags that can represent a device's capabilities. That may be user-toggled
 * or not, we don't make that distinction.
 *
 * 2) Available Input Types are indicators to whether the given platform can provide data for a given type.
 * For example, a user might have credentials saved for https://example.com, so when the page loads, but **before**
 * we can decorate any fields, we determine this first.
 */
export class Settings {
    /** @type {GlobalConfig} */
    globalConfig;
    /** @type {DeviceApi} */
    deviceApi;
    /** @type {AutofillFeatureToggles | null} */
    _featureToggles = null
    /** @type {AvailableInputTypes | null} */
    _availableInputTypes = null

    /**
     * @param {GlobalConfig} config
     * @param {DeviceApi} deviceApi
     */
    constructor (config, deviceApi) {
        this.deviceApi = deviceApi
        this.globalConfig = config
    }

    /**
     * Feature toggles are delivered as part of the Runtime Configuration - a flexible design that
     * allows data per user + remote config to be accessed together.
     *
     * Once we access the Runtime Configuration, we then extract the autofill-specific settings via
     * `runtimeConfig.userPreferences.features.autofill.settings` and validate that separately.
     *
     * The 2-step validation occurs because RuntimeConfiguration will be coming from a shared library
     * and does not know about the shape of Autofill specific settings.
     *
     * @returns {Promise<AutofillFeatureToggles>}
     */
    async getFeatureToggles () {
        try {
            const runtimeConfig = await this.deviceApi.request(new GetRuntimeConfigurationCall(null))
            const autofillSettings = validate(runtimeConfig?.userPreferences?.features?.autofill?.settings, autofillSettingsSchema)
            return autofillSettings.featureToggles
        } catch (e) {
            // these are the fallbacks for when a platform hasn't implemented the calls above. (like on android)
            if (this.globalConfig.isDDGTestMode) {
                console.log('isDDGTestMode: getFeatureToggles: ❌', e)
            }
            return Settings.defaults.featureToggles
        }
    }

    /**
     * Available Input Types are boolean indicators to represent which input types the
     * current **user** has data available for.
     *
     * @returns {Promise<AvailableInputTypes>}
     */
    async getAvailableInputTypes () {
        try {
            return await this.deviceApi.request(new GetAvailableInputTypesCall(null))
        } catch (e) {
            if (this.globalConfig.isDDGTestMode) {
                console.log('isDDGTestMode: getAvailableInputTypes: ❌', e)
            }
            return Settings.defaults.availableInputTypes
        }
    }

    /**
     * To 'refresh' settings means to re-call APIs to determine new state. This may
     * only occur once per page, but it must be done before any page scanning/decorating can happen
     *
     * @returns {Promise<{
     *      availableInputTypes: AvailableInputTypes,
     *      featureToggles: AutofillFeatureToggles
     * }>}
     * @param {AvailableInputTypes} [availableInputTypesOverrides] a migration aid so that macOS can provide data in its old way initially
     */
    async refresh (availableInputTypesOverrides) {
        this.setFeatureToggles(await this.getFeatureToggles())
        const availableInputTypesFromRemote = await this.getAvailableInputTypes()

        /** @type {AvailableInputTypes} */
        const availableInputTypes = {
            email: false, // not supported yet
            ...availableInputTypesFromRemote,
            ...availableInputTypesOverrides
        }

        // Update the availableInputTypes to take into account the feature toggles.
        if (!this.featureToggles.inputType_credentials) {
            availableInputTypes.credentials = false
        }
        if (!this.featureToggles.inputType_identities) {
            availableInputTypes.identities = false
        }
        if (!this.featureToggles.inputType_creditCards) {
            availableInputTypes.creditCards = false
        }

        // at this point we've fetched from remote + merged local overrides, so we're ready to set.
        this.setAvailableInputTypes(availableInputTypes)

        return {
            featureToggles: this.featureToggles,
            availableInputTypes: this.availableInputTypes
        }
    }

    /** @returns {AutofillFeatureToggles} */
    get featureToggles () {
        if (this._featureToggles === null) throw new Error('feature toggles accessed before being set')
        return this._featureToggles
    }

    /** @param {AutofillFeatureToggles} input */
    setFeatureToggles (input) {
        this._featureToggles = input
    }

    /** @returns {AvailableInputTypes} */
    get availableInputTypes () {
        if (this._availableInputTypes === null) throw new Error('available input types accessed before being set')
        return this._availableInputTypes
    }

    /** @param {AvailableInputTypes} value */
    setAvailableInputTypes (value) {
        this._availableInputTypes = value
    }

    static defaults = {
        /** @type {AutofillFeatureToggles} */
        featureToggles: {
            credentials_saving: false,
            password_generation: false,
            emailProtection: false,
            inputType_identities: false,
            inputType_credentials: false,
            inputType_creditCards: false,
            inlineIcon_credentials: false
        },
        /** @type {AvailableInputTypes} */
        availableInputTypes: {
            credentials: false,
            identities: false,
            creditCards: false,
            email: false
        }
    }

    static default (globalConfig, deviceApi) {
        const settings = new Settings(globalConfig, deviceApi)
        settings.setFeatureToggles(Settings.defaults.featureToggles)
        settings.setAvailableInputTypes(Settings.defaults.availableInputTypes)
        return settings
    }
}
