"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.DefaultNumberOption = void 0;
function DefaultNumberOption(val, min, max, fallback) {
    if (val !== undefined) {
        val = Number(val);
        if (isNaN(val) || val < min || val > max) {
            throw new RangeError("".concat(val, " is outside of range [").concat(min, ", ").concat(max, "]"));
        }
        return Math.floor(val);
    }
    return fallback;
}
exports.DefaultNumberOption = DefaultNumberOption;
