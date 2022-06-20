/**
 * This roughly follows https://www.jsonrpc.org/specification
 * @template {import("zod").ZodType} Params=import("zod").ZodType
 * @template {import("zod").ZodType} Result=import("zod").ZodType
 */
export class DeviceApiCall {
    /** @type {string} */
    method= 'unknown';
    /**
     * An optional 'id' - used to indicate if a request requires a response.
     * @type {string|null}
     */
    id = null;
    /** @type {Params | null | undefined} */
    paramsValidator = null;
    /** @type {Result | null | undefined} */
    resultValidator = null;
    /** @type {import("zod").infer<Params>} */
    params
    /**
     * This is a carve-out for legacy messages that are not typed yet.
     * If you set this to 'true', then the response will not be checked to conform
     * to any shape
     * @deprecated this is here to aid migration, should be removed ASAP
     * @type {boolean}
     */
    throwOnResultKeysMissing = true;
    /**
     * New messages should be in a particular format, eg: { success: T },
     * but you can set this to false if you want to access the result as-is,
     * without any unwrapping logic
     * @deprecated this is here to aid migration, should be removed ASAP
     * @type {boolean}
     */
    unwrapResult = true;
    /**
     * @param {import("zod").infer<Params>} data
     */
    constructor (data) {
        this.params = data
    }

    /**
     * @returns {import("zod").infer<Params>|undefined}
     */
    validateParams () {
        if (this.params === undefined) {
            return undefined
        }
        this._validate(this.params, this.paramsValidator)
        return this.params
    }

    /**
     * @param {any|null} incoming
     * @returns {import("zod").infer<Result>}
     */
    validateResult (incoming) {
        this._validate(incoming, this.resultValidator)
        if (!incoming) {
            return incoming
        }
        if (!this.unwrapResult) {
            return incoming
        }
        if ('data' in incoming) {
            console.warn('response had `data` property. Please migrate to `success`')
            return incoming.data
        }
        if ('success' in incoming) {
            return incoming.success
        }
        if ('error' in incoming) {
            if (typeof incoming.error.message === 'string') {
                throw new DeviceApiCallError(`${this.method}: ${incoming.error.message}`)
            }
        }
        if (this.throwOnResultKeysMissing) {
            throw new Error('unreachable. Response did not contain `success` or `data`')
        }
        return incoming
    }

    /**
     * @param {any} data
     * @param {import("zod").ZodType|undefined|null} [validator]
     * @private
     */
    _validate (data, validator) {
        if (!validator) return data
        if (validator) {
            const result = validator?.safeParse(data)
            if (!result) {
                throw new Error('unreachable')
            }
            if (!result.success) {
                if ('error' in result) {
                    this.throwError(result.error.issues)
                } else {
                    console.error('unknown error from validate')
                }
            }
        }
    }

    /**
     * @param {import('zod').ZodIssue[]} errors
     */
    throwError (errors) {
        const error = SchemaValidationError.fromZodErrors(errors, this.constructor.name)
        throw error
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
     * @param {import("zod").infer<Result>} response
     * @returns {import("zod").infer<Result>}
     */
    result (response) {
        return response
    }
    /**
     * @returns {import("zod").infer<Result>}
     */
    preResultValidation (response) {
        return response
    }
}

export class DeviceApiCallError extends Error {}

/**
 * Check for this error if you'd like to
 */
export class SchemaValidationError extends Error {
    /** @type {import("zod").ZodIssue[]} */
    validationErrors = [];

    /**
     * @param {import("zod").ZodIssue[]} errors
     * @param {string} name
     * @returns {SchemaValidationError}
     */
    static fromZodErrors (errors, name) {
        const heading = `${errors.length} SchemaValidationError(s) errors for ` + name
        function log (issue) {
            switch (issue.code) {
            case 'invalid_literal':
            case 'invalid_type': {
                console.log(`${name}. Path: '${issue.path.join('.')}', Error: '${issue.message}'`)
                break
            }
            case 'invalid_union': {
                for (let unionError of issue.unionErrors) {
                    for (let issue1 of unionError.issues) {
                        log(issue1)
                    }
                }
                break
            }
            default: {
                console.log(name, 'other issue:', issue)
            }
            }
        }
        for (let error of errors) {
            log(error)
        }
        const message = [heading, 'please see the details above'].join('\n    ')
        const error = new SchemaValidationError(message)
        error.validationErrors = errors
        return error
    }
}

/**
 * Creates an instance of `DeviceApiCall` from only a name and 'params'
 * and optional validators. Use this to help migrate existing messages.
 *
 * @template {import("zod").ZodType} Params
 * @template {import("zod").ZodType} Result
 * @param {string} method
 * @param {import("zod").infer<Params>} [params]
 * @param {Params|null} [paramsValidator]
 * @param {Result|null} [resultValidator]
 * @returns {DeviceApiCall<Params, Result>}
 */
export function createDeviceApiCall (method, params, paramsValidator = null, resultValidator = null) {
    /** @type {DeviceApiCall<Params, Result>} */
    const deviceApiCall = new DeviceApiCall(params)
    deviceApiCall.paramsValidator = paramsValidator
    deviceApiCall.resultValidator = resultValidator
    deviceApiCall.method = method
    deviceApiCall.throwOnResultKeysMissing = false
    deviceApiCall.unwrapResult = false
    return deviceApiCall
}

/**
 * Creates an instance of `DeviceApiCall` from only a name and 'params'
 * and optional validators. Use this to help migrate existing messages.
 *
 * Note: This creates a regular DeviceApiCall, but adds the 'id' as a string
 * so that transports know that it expects a response.
 *
 * @template {import("zod").ZodType} Params
 * @template {import("zod").ZodType} Result
 * @param {string} method
 * @param {import("zod").infer<Params>} [params]
 * @param {string} [id]
 * @param {Params|null} [paramsValidator]
 * @param {Result|null} [resultValidator]
 * @returns {DeviceApiCall<Params, Result>}
 */
export function createRequest (method, params, id = 'n/a', paramsValidator = null, resultValidator = null) {
    const call = createDeviceApiCall(method, params, paramsValidator, resultValidator)
    call.id = id
    return call
}

export const createNotification = createDeviceApiCall

/**
 * Validate any arbitrary data with any Zod validator
 *
 * @template {import("zod").ZodType} Validator
 * @param {any} data
 * @param {Validator | null} [validator]
 * @returns {import("zod").infer<Validator>}
 */
export function validate (data, validator = null) {
    if (validator) {
        return validator.parse(data)
    }
    return data
}
