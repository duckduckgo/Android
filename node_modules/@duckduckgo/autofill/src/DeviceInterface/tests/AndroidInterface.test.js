import {AndroidInterface} from '../AndroidInterface'
import {createGlobalConfig} from '../../config'
import {RuntimeConfiguration, tryCreateRuntimeConfiguration} from '@duckduckgo/content-scope-scripts'
import {Settings, fromRuntimeConfig} from '../../settings/settings'
import {NativeTooltip} from '../../UI/NativeTooltip'
import {NullSender} from '../../senders/sender'

describe('AndroidInterface', function () {
    beforeEach(() => {
        require('../../requestIdleCallback')
    })
    it('can be instantiated without throwing', () => {
        const config = createGlobalConfig()
        const runtimeConfig = new RuntimeConfiguration()
        const tooltip = new NativeTooltip()
        const sender = new NullSender()
        const device = new AndroidInterface(sender, tooltip, config, runtimeConfig, Settings.default())
        device.init()
    })
    it('can create platform configuration', () => {
        const {config} = tryCreateRuntimeConfiguration({
            contentScope: {
                features: {
                    autofill: {
                        state: 'enabled',
                        exceptions: []
                    }
                },
                unprotectedTemporary: []
            },
            userPreferences: {
                debug: true,
                features: {
                    autofill: {
                        settings: {
                            featureToggles: {
                                'inputType_credentials': true,
                                'inputType_identities': true,
                                'inputType_creditCards': true,
                                'emailProtection': true,
                                'password_generation': true,
                                'credentials_saving': true
                            }
                        }
                    }
                },
                platform: {name: 'windows'}
            },
            userUnprotectedDomains: []
        })
        const settings = fromRuntimeConfig(config)
        expect(settings.featureToggles.inputType_credentials).toBe(true)
    })
})
