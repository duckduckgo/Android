import {Settings, fromRuntimeConfig} from './settings'
import {createRuntimeConfiguration} from '@duckduckgo/content-scope-scripts'

describe('autofill settings', () => {
    it('can be created with default values', () => {
        const settings = Settings.default()
        expect(settings.featureToggles).toBeDefined()
    })
    it('can be created from platform configuration (content-scope-scripts)', () => {
        const config = createRuntimeConfiguration({
            contentScope: {
                features: {
                    autofill: {
                        state: 'enabled',
                        exceptions: []
                    }
                },
                unprotectedTemporary: []
            },
            userUnprotectedDomains: [],
            userPreferences: {
                debug: false,
                platform: {
                    name: 'android'
                },
                features: {
                    autofill: {
                        settings: {
                            featureToggles: {
                                inputType_credentials: true,
                                inputType_identities: false,
                                inputType_creditCards: false,
                                emailProtection: true,
                                password_generation: false,
                                credentials_saving: true
                            }
                        }
                    }
                }
            }
        })
        const settings = fromRuntimeConfig(config)
        expect(settings.featureToggles).toBeDefined()
    })
})
