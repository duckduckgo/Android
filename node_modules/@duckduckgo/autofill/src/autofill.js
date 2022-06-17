// Polyfills/shims
import './requestIdleCallback'
import './senders/captureDdgGlobals'
import {createDevice} from './DeviceInterface'
import {createGlobalConfig} from './config'
import {createTooltip} from './UI/tooltips'
import {fromRuntimeConfig} from './settings/settings'
import {tryCreateRuntimeConfiguration} from '@duckduckgo/content-scope-scripts'
import {GetRuntimeConfiguration} from './messages/messages'
import {createSender} from './senders/create-sender'

(async () => {
    if (!window.isSecureContext) return false
    try {
        // this is config already present in the script, or derived from the page etc.
        const globalConfig = createGlobalConfig()

        // if (globalConfig.isDDGTestMode) {
        console.log('globalConfig', globalConfig)
        // }

        // Transport is needed very early because we may need to fetch initial configuration, before any
        // autofill logic can run...
        const sender = createSender(globalConfig)

        // Get runtime configuration - this may include messaging
        const runtimeConfiguration = await getRuntimeConfiguration(sender)

        // Autofill settings need to be derived from runtime config
        const autofillSettings = fromRuntimeConfig(runtimeConfiguration)

        // log feature toggles for clarity when testing
        if (globalConfig.isDDGTestMode) {
            console.log('autofillSettings.featureToggles', JSON.stringify(autofillSettings.featureToggles, null, 2))
        }

        // If it was enabled, try to ask for available input types
        if (!runtimeConfiguration.isFeatureRemoteEnabled('autofill')) {
            console.log('feature was remotely disabled')
            return
        }

        // Determine the tooltipHandler type
        const tooltip = createTooltip(globalConfig, runtimeConfiguration, autofillSettings)
        console.log(tooltip, 'tooltip')
        const device = createDevice(sender, tooltip, globalConfig, runtimeConfiguration, autofillSettings)
        console.log(device, 'device')

        // This is a workaround for the previous design, we should refactor if possible
        tooltip.setDevice?.(device)

        // Init services
        await device.init()
    } catch (e) {
        console.error(e)
        // Noop, we errored
    }
})()

/**
 * @public
 * @param {import("./senders/sender").Sender} sender
 * @returns {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration}
 */
async function getRuntimeConfiguration (sender) {
    const data = await sender.send(new GetRuntimeConfiguration(null))
    const {config, errors} = tryCreateRuntimeConfiguration(data)

    if (errors.length) {
        for (let error of errors) {
            console.log(error.message, error)
        }
        throw new Error(`${errors.length} errors prevented global configuration from being created.`)
    }

    return config
}
