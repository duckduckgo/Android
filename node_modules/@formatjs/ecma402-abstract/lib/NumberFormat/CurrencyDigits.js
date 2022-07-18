import { HasOwnProperty } from '../262';
/**
 * https://tc39.es/ecma402/#sec-currencydigits
 */
export function CurrencyDigits(c, _a) {
    var currencyDigitsData = _a.currencyDigitsData;
    return HasOwnProperty(currencyDigitsData, c)
        ? currencyDigitsData[c]
        : 2;
}
