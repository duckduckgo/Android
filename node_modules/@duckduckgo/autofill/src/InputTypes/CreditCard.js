/**
 * @implements {TooltipItemRenderer}
 */
export class CreditCardTooltipItem {
    /** @type {CreditCardObject} */
    #data;
    /** @param {CreditCardObject} data */
    constructor (data) {
        this.#data = data
    }
    id = () => String(this.#data.id)
    labelMedium = (_) => this.#data.title;
    labelSmall = (_) => this.#data.displayNumber
}
