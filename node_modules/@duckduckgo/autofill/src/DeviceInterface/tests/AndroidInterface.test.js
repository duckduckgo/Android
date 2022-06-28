import {AndroidInterface} from '../AndroidInterface'
import { createGlobalConfig } from '../../config'
import {AndroidTransport} from '../../deviceApiCalls/transports/android.transport'
import {Settings} from '../../Settings'
import {DeviceApi} from '../../../packages/device-api'

describe('AndroidInterface', function () {
    beforeEach(() => {
        require('../../requestIdleCallback')
    })
    it('can be instantiated without throwing', () => {
        const config = createGlobalConfig()
        const ioHandler = new DeviceApi(new AndroidTransport(config))
        const settings = new Settings(config, ioHandler)
        const device = new AndroidInterface(config, ioHandler, settings)
        device.init()
    })
})
