/**
 * Platforms should only need to implement this `send` method
 */
export class DeviceApiTransport {
    /**
     * @param {import("./device-api-call.js").DeviceApiCall} _deviceApiCall
     * @returns {Promise<any>}
     */
    async send (_deviceApiCall) {
        return undefined
    }
}

/**
 * This is the base Sender class that platforms can will implement.
 *
 * Note: The 'handle' method must be implemented, unless you also implement 'send'
 */
export class DeviceApi {
    /** @type {DeviceApiTransport} */
    transport;
    /** @param {DeviceApiTransport} transport */
    constructor (transport) {
        this.transport = transport
    }
    /**
     * @template {import("./device-api-call").DeviceApiCall} D
     * @param {D} deviceApiCall
     * @returns {Promise<ReturnType<D['validateResult']>['success']>}
     */
    async request (deviceApiCall) {
        deviceApiCall.validateParams()
        let result = await this.transport.send(deviceApiCall)
        let processed = deviceApiCall.preResultValidation(result)
        return deviceApiCall.validateResult(processed)
    }
    /**
     * @template {import("./device-api-call").DeviceApiCall} P
     * @param {P} deviceApiCall
     * @returns {Promise<void>}
     */
    async notify (deviceApiCall) {
        deviceApiCall.validateParams()
        await this.transport.send(deviceApiCall)
    }
}
