import { defineProperty } from '../utils'

export function init () {
    const navigator = globalThis.navigator
    const Navigator = globalThis.Navigator

    /**
     * Temporary storage can be used to determine hard disk usage and size.
     * This will limit the max storage to 4GB without completely disabling the
     * feature.
     */
    if (navigator.webkitTemporaryStorage) {
        try {
            const org = navigator.webkitTemporaryStorage.queryUsageAndQuota
            const tStorage = navigator.webkitTemporaryStorage
            tStorage.queryUsageAndQuota = function queryUsageAndQuota (callback, err) {
                const modifiedCallback = function (usedBytes, grantedBytes) {
                    const maxBytesGranted = 4 * 1024 * 1024 * 1024
                    const spoofedGrantedBytes = Math.min(grantedBytes, maxBytesGranted)
                    callback(usedBytes, spoofedGrantedBytes)
                }
                org.call(navigator.webkitTemporaryStorage, modifiedCallback, err)
            }
            defineProperty(Navigator.prototype, 'webkitTemporaryStorage', { get: () => tStorage })
        } catch (e) {}
    }
}
