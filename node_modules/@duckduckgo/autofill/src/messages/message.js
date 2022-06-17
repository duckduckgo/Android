/**
 * @template [Request=any],[Response=any]
 */
export class Message {
    /**
     * @type {any}
     */
    reqValidator = null
    /**
     * @type {any}
     */
    resValidator = null

    /**
     * String representation of this message's name
     * @type {string}
     */
    name = 'unknown'

    /**
     * The name of a response message, if it exists
     * @type {string}
     */
    responseName = this.name + 'Response'

    /**
     * This is the data that will be sent in the message.
     * @type {Request|undefined}
     */
    data

    /**
     * @param {Request} [data]
     */
    constructor (data) {
        this.data = data
    }

    /**
     * @returns {Request|undefined}
     */
    validateRequest () {
        if (this.data === undefined) {
            return undefined
        }
        if (this.reqValidator && !this.reqValidator?.(this.data)) {
            this.throwError(this.reqValidator?.['errors'])
        }
        return this.data
    }

    /**
     * @param {import('ajv').ErrorObject[]} errors
     */
    throwError (errors) {
        const error = SchemaValidationError.fromErrors(errors, this.constructor.name)
        throw error
    }

    /**
     * @param {any|null} incoming
     * @returns {Response}
     */
    validateResponse (incoming) {
        if (this.resValidator && !this.resValidator?.(incoming)) {
            this.throwError(this.resValidator?.errors)
        }
        if (!incoming) {
            return incoming
        }
        if ('data' in incoming) {
            console.warn('response had `data` property. Please migrate to `success`')
            return incoming.data
        }
        if ('success' in incoming) {
            return incoming.success
        }
        throw new Error('unreachable. Response did not contain `success` or `data`')
    }

    /**
     * Use this helper for creating stand-in response messages that are typed correctly.
     *
     * @examples
     *
     * ```js
     * const msg = new Message();
     * const response = msg.response({}) // <-- This argument will be typed correctly
     * ```
     *
     * @param {Response} response
     * @returns {Response}
     */
    response (response) {
        return response
    }

    /**
     * @param {any} response
     * @returns {{success: Response, error?: string}}
     */
    preResponseValidation (response) {
        return response
    }
}

/**
 * Check for this error if you'd like to
 */
export class SchemaValidationError extends Error {
    /** @type {import("ajv").ErrorObject[]} */
    validationErrors = [];

    /**
     * @param {import("ajv").ErrorObject[]} errors
     * @param {string} name
     * @returns {SchemaValidationError}
     */
    static fromErrors (errors, name) {
        const heading = `${errors.length} SchemaValidationError(s) errors for ` + name
        const lines = []
        for (let error of errors) {
            // console.log(JSON.stringify(error, null, 2));
            lines.push(error.message || 'unknown')
        }
        const message = [heading, ...lines].join('\n    ')
        const error = new SchemaValidationError(message)
        error.validationErrors = errors
        return error
    }
}
