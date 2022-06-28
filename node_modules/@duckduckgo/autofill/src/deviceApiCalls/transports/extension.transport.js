import {DeviceApiTransport} from '../../../packages/device-api'

export class ExtensionTransport extends DeviceApiTransport {
    async send (deviceApiCall) {
        throw new Error('not implemented yet for ' + deviceApiCall.method)
    }
}
