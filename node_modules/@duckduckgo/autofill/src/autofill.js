// Polyfills/shims
import './requestIdleCallback'
import {createDevice} from './DeviceInterface'

(() => {
    if (!window.isSecureContext) return false
    try {
        const deviceInterface = createDevice()
        deviceInterface.init()
    } catch (e) {
        console.error(e)
        // Noop, we errored
    }
})()
