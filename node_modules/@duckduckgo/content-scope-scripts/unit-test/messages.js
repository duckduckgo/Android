import Ajv from 'ajv'
import { Message, SchemaValidationError } from '../src/messaging/message.js'

describe('Message', () => {
    describe('when validating request data', () => {
        const requestSchema = {
            type: 'object',
            properties: {
                name: { type: 'string' }
            },
            required: ['name']
        }
        it('succeeds when data is valid', () => {
            const msg = messageWithRequestSchema(requestSchema, { name: 'hello!' })
            expect(() => msg.validateRequest()).not.toThrowError()
        })
        it('throws when data is undefined', async () => {
            const msg = messageWithRequestSchema(requestSchema, undefined)
            // assert error instance
            expect(() => msg.validateRequest())
                .toThrowMatching(e => {
                    return e instanceof SchemaValidationError
                })
            // assert messages are present
            expect(() => msg.validateRequest())
                .toThrowMatching((e) => {
                    return e.validationErrors.length === 1
                })
        })
        it('throws when data is present, but invalid', async () => {
            const msg = messageWithRequestSchema(requestSchema, { not_name: 'oops' })

            // assert messages are present
            expect(() => msg.validateRequest())
                .toThrowError(`1 SchemaValidationError(s) errors for MockMessage
    should have required property 'name'`)
        })
    })
    describe('when validating response data', () => {
        const successSchema = {
            type: 'object',
            properties: {
                success: {
                    type: 'string'
                }
            },
            required: ['success']
        }
        it('succeeds when data is valid', () => {
            const msg = messageWithResponseSchema(successSchema)
            msg.validateResponse({ success: 'hello world' })
        })
        it('throws when data is undefined', () => {
            const msg = messageWithResponseSchema(successSchema, undefined)

            // assert error instance
            expect(() => msg.validateResponse())
                .toThrowMatching(e => {
                    return e instanceof SchemaValidationError
                })

            // assert messages are present
            expect(() => msg.validateResponse())
                .toThrowMatching((e) => {
                    return e.validationErrors.length === 1
                })
        })
        it('throws when data is present, but invalid', () => {
            const msg = messageWithResponseSchema(successSchema)

            // assert messages are present
            expect(() => msg.validateResponse({ not_success: 'oops' }))
                .toThrowError(`1 SchemaValidationError(s) errors for MockMessage
    should have required property 'success'`)
        })
    })
})

function messageWithRequestSchema (schema, data) {
    const ajv = new Ajv()
    const fn = ajv.compile(schema)

    class MockMessage extends Message {
        reqValidator = fn;
    }

    const msg = new MockMessage(data)
    return msg
}

function messageWithResponseSchema (schema, data) {
    const ajv = new Ajv()
    const fn = ajv.compile(schema)

    class MockMessage extends Message {
        resValidator = fn;
    }

    const msg = new MockMessage(data)
    return msg
}
