"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.IsWellFormedUnitIdentifier = void 0;
var IsSanctionedSimpleUnitIdentifier_1 = require("./IsSanctionedSimpleUnitIdentifier");
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
function IsWellFormedUnitIdentifier(unit) {
    unit = toLowerCase(unit);
    if ((0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(unit)) {
        return true;
    }
    var units = unit.split('-per-');
    if (units.length !== 2) {
        return false;
    }
    var numerator = units[0], denominator = units[1];
    if (!(0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(numerator) ||
        !(0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(denominator)) {
        return false;
    }
    return true;
}
exports.IsWellFormedUnitIdentifier = IsWellFormedUnitIdentifier;
