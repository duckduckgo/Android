import { GetOption } from '../GetOption';
import { IsWellFormedCurrencyCode } from '../IsWellFormedCurrencyCode';
import { IsWellFormedUnitIdentifier } from '../IsWellFormedUnitIdentifier';
/**
 * https://tc39.es/ecma402/#sec-setnumberformatunitoptions
 */
export function SetNumberFormatUnitOptions(nf, options, _a) {
    if (options === void 0) { options = Object.create(null); }
    var getInternalSlots = _a.getInternalSlots;
    var internalSlots = getInternalSlots(nf);
    var style = GetOption(options, 'style', 'string', ['decimal', 'percent', 'currency', 'unit'], 'decimal');
    internalSlots.style = style;
    var currency = GetOption(options, 'currency', 'string', undefined, undefined);
    if (currency !== undefined && !IsWellFormedCurrencyCode(currency)) {
        throw RangeError('Malformed currency code');
    }
    if (style === 'currency' && currency === undefined) {
        throw TypeError('currency cannot be undefined');
    }
    var currencyDisplay = GetOption(options, 'currencyDisplay', 'string', ['code', 'symbol', 'narrowSymbol', 'name'], 'symbol');
    var currencySign = GetOption(options, 'currencySign', 'string', ['standard', 'accounting'], 'standard');
    var unit = GetOption(options, 'unit', 'string', undefined, undefined);
    if (unit !== undefined && !IsWellFormedUnitIdentifier(unit)) {
        throw RangeError('Invalid unit argument for Intl.NumberFormat()');
    }
    if (style === 'unit' && unit === undefined) {
        throw TypeError('unit cannot be undefined');
    }
    var unitDisplay = GetOption(options, 'unitDisplay', 'string', ['short', 'narrow', 'long'], 'short');
    if (style === 'currency') {
        internalSlots.currency = currency.toUpperCase();
        internalSlots.currencyDisplay = currencyDisplay;
        internalSlots.currencySign = currencySign;
    }
    if (style === 'unit') {
        internalSlots.unit = unit;
        internalSlots.unitDisplay = unitDisplay;
    }
}
