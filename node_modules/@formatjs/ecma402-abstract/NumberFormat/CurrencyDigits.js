"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CurrencyDigits = void 0;
var _262_1 = require("../262");
/**
 * https://tc39.es/ecma402/#sec-currencydigits
 */
function CurrencyDigits(c, _a) {
    var currencyDigitsData = _a.currencyDigitsData;
    return (0, _262_1.HasOwnProperty)(currencyDigitsData, c)
        ? currencyDigitsData[c]
        : 2;
}
exports.CurrencyDigits = CurrencyDigits;
