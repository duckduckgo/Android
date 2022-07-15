"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FormatNumericToString = void 0;
var _262_1 = require("../262");
var ToRawPrecision_1 = require("./ToRawPrecision");
var utils_1 = require("../utils");
var ToRawFixed_1 = require("./ToRawFixed");
/**
 * https://tc39.es/ecma402/#sec-formatnumberstring
 */
function FormatNumericToString(intlObject, x) {
    var isNegative = x < 0 || (0, _262_1.SameValue)(x, -0);
    if (isNegative) {
        x = -x;
    }
    var result;
    var rourndingType = intlObject.roundingType;
    switch (rourndingType) {
        case 'significantDigits':
            result = (0, ToRawPrecision_1.ToRawPrecision)(x, intlObject.minimumSignificantDigits, intlObject.maximumSignificantDigits);
            break;
        case 'fractionDigits':
            result = (0, ToRawFixed_1.ToRawFixed)(x, intlObject.minimumFractionDigits, intlObject.maximumFractionDigits);
            break;
        default:
            result = (0, ToRawPrecision_1.ToRawPrecision)(x, 1, 2);
            if (result.integerDigitsCount > 1) {
                result = (0, ToRawFixed_1.ToRawFixed)(x, 0, 0);
            }
            break;
    }
    x = result.roundedNumber;
    var string = result.formattedString;
    var int = result.integerDigitsCount;
    var minInteger = intlObject.minimumIntegerDigits;
    if (int < minInteger) {
        var forwardZeros = (0, utils_1.repeat)('0', minInteger - int);
        string = forwardZeros + string;
    }
    if (isNegative) {
        x = -x;
    }
    return { roundedNumber: x, formattedString: string };
}
exports.FormatNumericToString = FormatNumericToString;
