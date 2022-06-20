import { DeviceApi } from '../lib/device-api'
import { createDeviceApiCall, DeviceApiCall } from '../lib/device-api-call'
import { z } from 'zod'

/**
 * @returns {{handler: DeviceApi, transport: import("../").DeviceApiTransport}}
 */
function testIo () {
    const transport = {
        send: jest.fn().mockReturnValue({ success: 'hello world' })
    }
    const handler = new DeviceApi(transport)
    return { transport, handler }
}

describe('device-api', () => {
    it('can send notification messages in old format', async () => {
        const { handler, transport } = testIo()
        await handler.notify(createDeviceApiCall('hello-world', { id: 1 }))
        expect(transport.send).toHaveBeenCalledTimes(1)
    })
    it('can perform request->response in old format', async () => {
        expect.assertions(2)
        const params = { id: 1 }
        const result = { a: 'b' }
        /** @type {import("../").DeviceApiTransport} */
        const transport = {
            send: async (deviceApiCall) => {
                expect(deviceApiCall.params).toBe(params)
                return result
            }
        }
        const handler = new DeviceApi(transport)
        const deviceApiCall = createDeviceApiCall('hello-world', params)
        const returnedResult = await handler.request(deviceApiCall)
        expect(returnedResult).toEqual(result)
    })
    describe('can send new messages', () => {
        it('when there is no validation', async () => {
            class T1 extends DeviceApiCall {
        method = 'abc';
            }
            const { handler, transport } = testIo()
            await handler.notify(new T1(null))
            expect(transport.send).toHaveBeenCalledTimes(1)
        })
        it('when there is params validation', async () => {
            expect.assertions(2)
            class T1 extends DeviceApiCall {
        method = 'abc';
        paramsValidator = z.string();
            }
            const { handler, transport } = testIo()
            try {
                await handler.notify(new T1(3))
            } catch (/** @type {any} */e) {
                expect(transport.send).toHaveBeenCalledTimes(0)
                expect(e.message).toMatchInlineSnapshot(`
          "1 SchemaValidationError(s) errors for T1
              please see the details above"
        `)
            }
        })
        it('when there is result validation', async () => {
            expect.assertions(2)
            class T1 extends DeviceApiCall {
        method = 'abc';
        resultValidator = z.string();
            }
            const { handler, transport } = testIo()
            try {
                await handler.request(new T1(3))
            } catch (/** @type {any} */e) {
                expect(transport.send).toHaveBeenCalledTimes(1)
                expect(e.message).toMatchInlineSnapshot(`
          "1 SchemaValidationError(s) errors for T1
              please see the details above"
        `)
            }
        })
        it('when there is an error in a result', async () => {
            expect.assertions(1)
            const transport = {
                send: jest.fn().mockReturnValue({ error: { message: 'hello world' } })
            }
            const handler = new DeviceApi(transport)
            class T1 extends DeviceApiCall {
                method = 'abc';
                resultValidator = z.object({
                    success: z.string().optional(),
                    error: z.object({ message: z.string() })
                })
            }
            try {
                await handler.request(new T1(null))
            } catch (/** @type {any} */e) {
                expect(e).toBeDefined()
            }
        })
    })
})
