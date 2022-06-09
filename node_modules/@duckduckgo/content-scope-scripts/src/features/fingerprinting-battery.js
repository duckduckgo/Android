import { defineProperty } from '../utils'

/**
 * Overwrites the Battery API if present in the browser.
 * It will return the values defined in the getBattery function to the client,
 * as well as prevent any script from listening to events.
 */
export function init (args) {
    if (globalThis.navigator.getBattery) {
        const BatteryManager = globalThis.BatteryManager

        const spoofedValues = {
            charging: true,
            chargingTime: 0,
            dischargingTime: Infinity,
            level: 1
        }
        const eventProperties = ['onchargingchange', 'onchargingtimechange', 'ondischargingtimechange', 'onlevelchange']

        for (const [prop, val] of Object.entries(spoofedValues)) {
            try {
                defineProperty(BatteryManager.prototype, prop, { get: () => val })
            } catch (e) { }
        }
        for (const eventProp of eventProperties) {
            try {
                defineProperty(BatteryManager.prototype, eventProp, { get: () => null })
            } catch (e) { }
        }
    }
}
