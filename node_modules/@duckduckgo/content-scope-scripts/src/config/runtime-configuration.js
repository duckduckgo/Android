import { processConfig } from '../apple-utils.js'
import validateSchema from './validate.cjs'
import { isFeatureEnabledFromProcessedConfig } from '../utils.js'

class RuntimeConfiguration {
    validate = validateSchema

    /** @type {Input | null} */
    config = null

    /**
     * @throws
     * @param {Input} config
     * @returns {RuntimeConfiguration}
     */
    assign (config) {
        if (this.validate(config)) {
            this.config = config
        } else {
            for (const error of this.validate.errors) {
                // todo: Give an error summary
                console.error(error)
            }
            throw new Error('invalid inputs')
        }

        return this
    }

    /**
     * @param {any} config
     * @returns {{errors: import("ajv").ErrorObject[], config: RuntimeConfiguration | null}}
     */
    tryAssign (config) {
        if (this.validate(config)) {
            this.config = config
            return { errors: [], config: this }
        }
        return { errors: this.validate.errors.slice(), config: null }
    }

    /**
     * This will only return settings for a feature if that feature is remotely enabled.
     *
     * @param {string} featureName
     * @param {URL} [url]
     * @returns {null|Record<string, any>}
     */
    getSettings (featureName, url) {
        const isEnabled = this.isFeatureRemoteEnabled(featureName, url)
        if (!isEnabled) return null
        const settings = {
            ...this.config.userPreferences.features?.[featureName]?.settings
            // todo: Decide on merge strategy?
            // ...this.config.contentScope.features?.[featureName]?.settings
        }
        return settings
    }

    /**
     * @returns {"macos"|"ios"|"extension"|"windows"|"android"|"unknown"}
     */
    get platform () {
        return this.config.userPreferences.platform.name
    }

    /**
     * @param {string} featureName
     * @param {URL} [url]
     * @returns {boolean}
     */
    isFeatureRemoteEnabled (featureName, url) {
        const privacyConfig = processConfig(
            this.config.contentScope,
            this.config.userUnprotectedDomains,
            this.config.userPreferences,
            url
        )
        return isFeatureEnabledFromProcessedConfig(privacyConfig, featureName)
    }
}

/**
 * Factory for creating config instance
 * @param {Input} incoming
 * @returns {RuntimeConfiguration}
 */
function createRuntimeConfiguration (incoming) {
    return new RuntimeConfiguration().assign(incoming)
}

/**
 * Factory for creating config instance
 * @param {Input} incoming
 * @returns {{errors: import("ajv").ErrorObject[], config: RuntimeConfiguration | null}}
 */
function tryCreateRuntimeConfiguration (incoming) {
    return new RuntimeConfiguration().tryAssign(incoming)
}

export { RuntimeConfiguration, createRuntimeConfiguration, tryCreateRuntimeConfiguration }
