import { Sender, UnimplementedError } from '../src/messaging/sender.js'
import { Message, SchemaValidationError } from '../src/messaging/message.js'

describe('Sender', () => {
    describe('when extending', () => {
        class NullSender extends Sender {
            async handle (msg) {
                return undefined
            }
        }
        function validate () {
            // @ts-ignore
            validate.errors = [{ message: 'oops!' }]
            return false
        }
        it('calls request validation if present', async () => {
            class ThrowingMessage extends Message {
                reqValidator = validate;
            }
            const sender = new NullSender()
            let callCount = 0

            try {
                await sender.send(new ThrowingMessage())
            } catch (e) {
                callCount += 1
                expect(e).toBeInstanceOf(SchemaValidationError)
            }

            expect(callCount).toBe(1)
        })
        it('calls response validation if present', async () => {
            class ThrowingMessage extends Message {
                resValidator = validate;
            }

            const sender = new NullSender()
            let callCount = 0

            try {
                await sender.send(new ThrowingMessage())
            } catch (e) {
                callCount += 1
                expect(e).toBeInstanceOf(SchemaValidationError)
            }

            expect(callCount).toBe(1)
        })
        it('calls overridden .handle()', async () => {
            let callCount = 0
            class AppleSender extends Sender {
                async handle (msg) {
                    callCount += 1
                    return undefined
                }
            }
            const sender = new AppleSender()
            const msg = new Message()
            msg.name = 'test_message'
            await sender.send(msg)
            expect(callCount).toBe(1)
        })
        it('throws if `handle` is not implemented', async () => {
            class AppleSender extends Sender {}
            const sender = new AppleSender()
            const msg = new Message()
            msg.name = 'test_message'

            let callCount = 0 // needed to ensure catch block is executed
            try {
                await sender.send(msg)
            } catch (e) {
                callCount += 1
                expect(e instanceof UnimplementedError)
                expect(e.message).toBe('Must implement `sender.handle`, tried to send message.name: test_message')
            }
            expect(callCount).toBe(1)
        })
        it('allows data to be altered before response validation occurs', async () => {
            class RecoveringMessage extends Message {
                preResponseValidation (response) {
                    return { success: 'dax@example.com' }
                }
            }
            const sender = new NullSender()
            const response = await sender.send(new RecoveringMessage())
            console.log(response)
        })
    })
})
