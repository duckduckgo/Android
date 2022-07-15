import { IsSanctionedSimpleUnitIdentifier } from './IsSanctionedSimpleUnitIdentifier';
/**
 * This follows https://tc39.es/ecma402/#sec-case-sensitivity-and-case-mapping
 * @param str string to convert
 */
function toLowerCase(str) {
    return str.replace(/([A-Z])/g, function (_, c) { return c.toLowerCase(); });
}
/**
 * https://tc39.es/ecma402/#sec-iswellformedunitidentifier
 * @param unit
 */
export function IsWellFormedUnitIdentifier(unit) {
    unit = toLowerCase(unit);
    if (IsSanctionedSimpleUnitIdentifier(unit)) {
        return true;
    }
    var units = unit.split('-per-');
    if (units.length !== 2) {
        return false;
    }
    var numerator = units[0], denominator = units[1];
    if (!IsSanctionedSimpleUnitIdentifier(numerator) ||
        !IsSanctionedSimpleUnitIdentifier(denominator)) {
        return false;
    }
    return true;
}
