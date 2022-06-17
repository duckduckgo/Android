import validators from '../schema/validators.cjs'

/**
 * A wrapper for Autofill settings
 */
class Settings {
    validate = validators['#/definitions/AutofillSettings']

    /** @type {Settings | null} */
    settings = null

    /**
     * Try to convert an object into Autofill Settings.
     * This will try to validate the keys against the schema
     *
     * @throws
     * @returns {Settings}
     */
    from (input) {
        if (this.validate(input)) {
            this.settings = input
        } else {
            // @ts-ignore
            for (const error of this.validate.errors) {
                console.error(error.message)
                console.error(error)
            }
            throw new Error('Could not create settings from global configuration')
        }

        return this
    }

    /**
     * @returns {FeatureToggles}
     */
    get featureToggles () {
        if (!this.settings) throw new Error('unreachable')
        return this.settings.featureToggles
    }

    /** @returns {Settings} */
    static default () {
        return new Settings().from({
            /** @type {FeatureToggles} */
            featureToggles: {
                inputType_credentials: true,
                inputType_identities: true,
                inputType_creditCards: true,
                emailProtection: true,
                password_generation: true,
                credentials_saving: true
            }
        })
    }
}

/**
 * @param {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} config
 * @returns {Settings}
 */
export function fromRuntimeConfig (config) {
    const autofillSettings = config.getSettings('autofill')
    const settings = (new Settings()).from(autofillSettings)
    return settings
}

export { Settings }
