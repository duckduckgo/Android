"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ComputeExponent = void 0;
var utils_1 = require("../utils");
var ComputeExponentForMagnitude_1 = require("./ComputeExponentForMagnitude");
var FormatNumericToString_1 = require("./FormatNumericToString");
/**
 * The abstract operation ComputeExponent computes an exponent (power of ten) by which to scale x
 * according to the number formatting settings. It handles cases such as 999 rounding up to 1000,
 * requiring a different exponent.
 *
 * NOT IN SPEC: it returns [exponent, magnitude].
 */
function ComputeExponent(numberFormat, x, _a) {
    var getInternalSlots = _a.getInternalSlots;
    if (x === 0) {
        return [0, 0];
    }
    if (x < 0) {
        x = -x;
    }
    var magnitude = (0, utils_1.getMagnitude)(x);
    var exponent = (0, ComputeExponentForMagnitude_1.ComputeExponentForMagnitude)(numberFormat, magnitude, {
        getInternalSlots: getInternalSlots,
    });
    // Preserve more precision by doing multiplication when exponent is negative.
    x = exponent < 0 ? x * Math.pow(10, -exponent) : x / Math.pow(10, exponent);
    var formatNumberResult = (0, FormatNumericToString_1.FormatNumericToString)(getInternalSlots(numberFormat), x);
    if (formatNumberResult.roundedNumber === 0) {
        return [exponent, magnitude];
    }
    var newMagnitude = (0, utils_1.getMagnitude)(formatNumberResult.roundedNumber);
    if (newMagnitude === magnitude - exponent) {
        return [exponent, magnitude];
    }
    return [
        (0, ComputeExponentForMagnitude_1.ComputeExponentForMagnitude)(numberFormat, magnitude + 1, {
            getInternalSlots: getInternalSlots,
        }),
        magnitude + 1,
    ];
}
exports.ComputeExponent = ComputeExponent;
