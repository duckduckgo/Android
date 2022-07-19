import { getMagnitude } from '../utils';
import { ComputeExponentForMagnitude } from './ComputeExponentForMagnitude';
import { FormatNumericToString } from './FormatNumericToString';
/**
 * The abstract operation ComputeExponent computes an exponent (power of ten) by which to scale x
 * according to the number formatting settings. It handles cases such as 999 rounding up to 1000,
 * requiring a different exponent.
 *
 * NOT IN SPEC: it returns [exponent, magnitude].
 */
export function ComputeExponent(numberFormat, x, _a) {
    var getInternalSlots = _a.getInternalSlots;
    if (x === 0) {
        return [0, 0];
    }
    if (x < 0) {
        x = -x;
    }
    var magnitude = getMagnitude(x);
    var exponent = ComputeExponentForMagnitude(numberFormat, magnitude, {
        getInternalSlots: getInternalSlots,
    });
    // Preserve more precision by doing multiplication when exponent is negative.
    x = exponent < 0 ? x * Math.pow(10, -exponent) : x / Math.pow(10, exponent);
    var formatNumberResult = FormatNumericToString(getInternalSlots(numberFormat), x);
    if (formatNumberResult.roundedNumber === 0) {
        return [exponent, magnitude];
    }
    var newMagnitude = getMagnitude(formatNumberResult.roundedNumber);
    if (newMagnitude === magnitude - exponent) {
        return [exponent, magnitude];
    }
    return [
        ComputeExponentForMagnitude(numberFormat, magnitude + 1, {
            getInternalSlots: getInternalSlots,
        }),
        magnitude + 1,
    ];
}
