import {AppleSender} from './apple.sender'
import {AndroidSender} from './android.sender'
import {createWindowsSender} from './windows.sender'
import {ExtensionSender} from './extension.sender'
import {LoggingSender} from './sender'

/**
 * We have to decide on a sender, *before* we have a 'tooltipHandler'.
 *
 * This is because an initial message to retrieve the platform configuration might be needed
 *
 * @param {GlobalConfig} globalConfig
 * @returns {import("./sender").Sender}
 */
export function createSender (globalConfig) {
    const sender = selectSender(globalConfig)

    // if (globalConfig.isDDGTestMode) {
    // }

    // during integration, we are always adding this
    return new LoggingSender(sender)
}

/**
 * The runtime has to decide on a transport, *before* we have a 'tooltipHandler'.
 *
 * This is because an initial message to retrieve the platform configuration might be needed
 *
 * @param {GlobalConfig} globalConfig
 * @returns {import("./sender").Sender}
 */
function selectSender (globalConfig) {
    // On some platforms, things like `platform.name` are embedded into the script
    // and therefor may be immediately available.
    if (globalConfig.isWindows) {
        return createWindowsSender()
    }

    if (typeof globalConfig.userPreferences?.platform?.name === 'string') {
        switch (globalConfig.userPreferences?.platform?.name) {
        case 'ios':
        case 'macos':
            return new AppleSender(globalConfig)
        default:
            throw new Error('selectSender unimplemented!')
        }
    }

    if (globalConfig.isDDGApp) {
        if (globalConfig.isAndroid) {
            return new AndroidSender()
        }
        console.warn('should never get here...')
        return new AppleSender(globalConfig)
    }

    // falls back to extension... is this still the best way to determine this?
    return new ExtensionSender()
}
