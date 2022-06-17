/**
 * This is the base Sender class that platforms can will implement.
 *
 * Note: The 'handle' method must be implemented, unless you also implement 'send'
 */
export class Sender {
    /**
     * Try to send a message. Throws with validation errors
     *
     * @throws {SchemaValidationError}
     * @template Request,Response
     * @param {import("./message").Message<Request, Response>} message
     * @returns {Promise<ReturnType<import("./message").Message<Request, Response>['validateResponse']>>}
     */
    async send (message) {
        message.validateRequest()
        const response = await this.handle(message)
        const processed = message.preResponseValidation(response)
        return message.validateResponse(processed)
    }

    /**
     * @template Request,Response
     * @param {import("./message").Message<Request, Response>} message
     * @returns {Promise<Response | undefined>}
     */
    async handle (message) {
        throw new UnimplementedError('Must implement `sender.handle`, tried to send message.name: ' + message.name)
    }
}

export class UnimplementedError extends Error {};
