"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CoerceOptionsToObject = void 0;
var _262_1 = require("./262");
/**
 * https://tc39.es/ecma402/#sec-coerceoptionstoobject
 * @param options
 * @returns
 */
function CoerceOptionsToObject(options) {
    if (typeof options === 'undefined') {
        return Object.create(null);
    }
    return (0, _262_1.ToObject)(options);
}
exports.CoerceOptionsToObject = CoerceOptionsToObject;
