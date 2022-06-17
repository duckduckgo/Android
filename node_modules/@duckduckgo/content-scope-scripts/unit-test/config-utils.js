import { createRuntimeConfiguration, tryCreateRuntimeConfiguration } from '../src/config/runtime-configuration.js'

const TEST_URL_EXAMPLE_01 = new URL('http://example.com')

describe('Platform Configuration', () => {
    it('accepts minimal valid configuration', () => {
        const json = {
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
                    name: 'macos'
                },
                features: {}
            }
        }
        const config = createRuntimeConfiguration(json)
        expect(config.isFeatureRemoteEnabled('autofill', TEST_URL_EXAMPLE_01)).toBeTrue()
    })
    it('enforces platform name enum', () => {
        const json = {
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
                    name: 'mac-os' // typo
                },
                features: {}
            }
        }
        const { config, errors } = tryCreateRuntimeConfiguration(json)
        expect(config).toBeNull()
        expect(errors[0].message).toBe('must be equal to one of the allowed values')
    })
    it('accepts user-level feature settings', () => {
        const json = {
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
                    name: 'macos'
                },
                features: {
                    autofill: {
                        settings: {
                            featureToggles: {
                                'inputType.credentials': true,
                                'inputType.identities': false
                            }
                        }
                    }
                }
            }
        }
        const config = createRuntimeConfiguration(json)
        const settings = config.getSettings('autofill', TEST_URL_EXAMPLE_01)
        expect(settings.featureToggles['inputType.credentials']).toBeTrue()
    })
    it('all missing', async () => {
        const json = {}
        const { config, errors } = tryCreateRuntimeConfiguration(json)
        expect(config).toBeNull()
        expect(errors[0].message).toBe('must have required property \'contentScope\'')
    })
    it('missing contentScope', async () => {
        const json = {
            userUnprotectedDomains: [],
            userPreferences: {
                debug: false,
                platform: {
                    name: 'extension'
                },
                features: {
                }
            }
        }
        const { config, errors } = tryCreateRuntimeConfiguration(json)
        expect(config).toBeNull()
        expect(errors[0].message).toBe('must have required property \'contentScope\'')
    })
    it('missing userUnprotectedDomains', async () => {
        const json = {
            contentScope: {
                features: {},
                unprotectedTemporary: []
            },
            userPreferences: {
                debug: false,
                platform: {
                    name: 'extension'
                },
                features: {
                }
            }
        }
        const { config, errors } = tryCreateRuntimeConfiguration(json)
        expect(config).toBeNull()
        expect(errors[0].message).toBe('must have required property \'userUnprotectedDomains\'')
    })
    it('missing userPreferences', async () => {
        const json = {
            contentScope: {
                features: {},
                unprotectedTemporary: []
            },
            userUnprotectedDomains: []
        }
        const { config, errors } = tryCreateRuntimeConfiguration(json)
        expect(config).toBeNull()
        expect(errors[0].message).toBe('must have required property \'userPreferences\'')
    })
})
