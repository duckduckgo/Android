import {AndroidInterface} from './DeviceInterface/AndroidInterface'
import {ExtensionInterface} from './DeviceInterface/ExtensionInterface'
import {AppleDeviceInterface} from './DeviceInterface/AppleDeviceInterface'
import {WindowsInterface, WindowsOverlayDeviceInterface} from './DeviceInterface/WindowsInterface'
import {AppleOverlayDeviceInterface} from './DeviceInterface/AppleOverlayDeviceInterface'

/**
 * @param {import("./senders/sender").Sender} sender
 * @param {TooltipInterface} tooltip
 * @param {GlobalConfig} globalConfig
 * @param {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} platformConfig
 * @param {import("./settings/settings").Settings} autofillSettings
 * @returns {AndroidInterface|AppleDeviceInterface|AppleOverlayDeviceInterface|ExtensionInterface|WindowsInterface|WindowsOverlayDeviceInterface}
 */
export function createDevice (sender, tooltip, globalConfig, platformConfig, autofillSettings) {
    switch (platformConfig.platform) {
    case 'macos':
    case 'ios': {
        if (globalConfig.isTopFrame) {
            return new AppleOverlayDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
        }
        return new AppleDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
    }
    case 'extension':
        return new ExtensionInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
    case 'windows': {
        if (globalConfig.isTopFrame) {
            return new WindowsOverlayDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
        } else {
            return new WindowsInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
        }
    }
    case 'android':
        return new AndroidInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings)
    case 'unknown':
        throw new Error('unreachable. tooltipHandler platform was "unknown"')
    }
    throw new Error('undefined')
}
