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
     * @param {import("../messages/message").Message<Request, Response>} message
     * @returns {Promise<ReturnType<import("../messages/message").Message<Request, Response>['validateResponse']>>}
     */
    async send (message) {
        message.validateRequest()
        let response = await this.handle(message)
        let processed = message.preResponseValidation(response)
        return message.validateResponse(processed)
    }
    /**
     * @template Request,Response
     * @param {import("../messages/message").Message<Request, Response>} message
     * @returns {Promise<Response | undefined>}
     */
    async handle (message) {
        throw new Error('must implement `.handle`, message: ' + message.name)
    }
}

export class NullSender extends Sender {
    /**
     * @param _message
     * @returns {Promise<any>}
     */
    async send (_message) {
        return null
    }
}

/**
 *
 */
export class LoggingSender extends Sender {
    sender;
    constructor (sender) {
        super()
        this.sender = sender
    }

    async handle (message) {
        LoggingSender.printOutgoing(message)
        const value = await this.sender.handle(message)
        LoggingSender.printIncoming(message, value)
        return value
    }

    /**
     * @param {import("../messages/message").Message} message
     */
    static printOutgoing (message) {
        if (message.data) {
            if (typeof message.data === 'string') {
                return console.log('✈️', message.name, message.data)
            } else {
                return console.log(`✈`, message.name, JSON.stringify(message.data))
            }
        }
        console.log('✈️', message.name)
    }

    /**
     * @param {import("../messages/message").Message} message
     * @param {any} value
     */
    static printIncoming (message, value) {
        console.log(`📥`, message.name, JSON.stringify(value, null, 2))
    }
}
