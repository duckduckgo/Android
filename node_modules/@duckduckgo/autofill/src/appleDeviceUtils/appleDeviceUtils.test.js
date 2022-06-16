import { createGlobalConfig } from '../config'
import { createRequest, DeviceApi } from '../../packages/device-api'
import { AppleTransport } from '../deviceApiCalls/transports/apple.transport'

const webkitMock = jest.fn(async (data) => {
    const { messageHandling } = data

    if (messageHandling.secret !== 'PLACEHOLDER_SECRET') return

    const message = {data: 'test'}

    const iv = new Uint8Array(messageHandling.iv)
    const keyBuffer = new Uint8Array(messageHandling.key)
    const key = await crypto.subtle.importKey('raw', keyBuffer, 'AES-GCM', false, ['encrypt'])

    const encrypt = (message) => {
        let enc = new TextEncoder()
        return crypto.subtle.encrypt({name: 'AES-GCM', iv}, key, enc.encode(message))
    }

    return encrypt(JSON.stringify(message))
        .then((ciphertext) =>
            // @ts-ignore add method names here?
            window[messageHandling.methodName]({
                ciphertext: new Uint8Array(ciphertext),
                tag: []
            })
        )
})
window.webkit = {messageHandlers: {
    testMock: {postMessage: webkitMock}
}}

describe('wkSendAndWait', () => {
    it('returns the expected unencrypted data', async () => {
        const config = createGlobalConfig()
        const transport = new AppleTransport(config)
        const io = new DeviceApi(transport)
        const response = await io.request(createRequest('testMock', {}))
        expect(response.data).toBe('test')
    })
})
