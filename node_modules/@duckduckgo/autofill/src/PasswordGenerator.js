import { generate } from '../packages/password'
import rules from '../packages/password/rules.json'

/**
 * Create a password once and reuse it.
 */
class PasswordGenerator {
    /** @type {string|null} */
    #previous = null;

    /** @returns {boolean} */
    get generated () {
        return this.#previous !== null
    }

    /** @returns {string|null} */
    get password () {
        return this.#previous
    }

    /** @param {import('../packages/password').GenerateOptions} [params] */
    generate (params = {}) {
        if (this.#previous) {
            return this.#previous
        }

        this.#previous = generate({ ...params, rules })

        return this.#previous
    }
}

export { PasswordGenerator }
