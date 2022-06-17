import Ajv from 'ajv'
// import {Message} from './message'
import {Message, SchemaValidationError} from './message'

describe('Message', () => {
    it('exposes validation errors by throwing', async () => {
        expect.assertions(2)
        const schema = {
            type: 'object',
            properties: {
                name: { type: 'string' }
            },
            required: ['name']
        }
        const ajv = new Ajv()
        const fn = ajv.compile(schema)
        class MockMessage extends Message {
            reqValidator = fn;
        }
        try {
            new MockMessage({}).validateRequest()
        } catch (e) {
            if (e instanceof SchemaValidationError) {
                expect(e.validationErrors.length).toBe(1)
                expect(e.message).toMatchInlineSnapshot(`
"1 SchemaValidationError(s) errors for MockMessage
    must have required property 'name'"
`)
                for (let validationError of e.validationErrors) {
                    console.log(validationError)
                }
            }
        }
    })
})
