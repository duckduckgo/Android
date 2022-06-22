import { getCountryDisplayName } from '../Form/formatters'

/**
 * @implements {TooltipItemRenderer}
 */
export class IdentityTooltipItem {
    /** @type {IdentityObject} */
    #data;
    /** @param {IdentityObject} data */
    constructor (data) {
        this.#data = data
    }
    id = () => String(this.#data.id)
    labelMedium = (subtype) => {
        if (subtype === 'addressCountryCode') {
            return getCountryDisplayName('en', this.#data.addressCountryCode || '')
        }
        if (this.#data.id === 'privateAddress') {
            return 'Generated Private Duck Address'
        }
        return this.#data[subtype]
    };
    label (subtype) {
        if (this.#data.id === 'privateAddress') {
            return this.#data[subtype]
        }
        return null
    }
    labelSmall = (_) => {
        return this.#data.title
    };
}
