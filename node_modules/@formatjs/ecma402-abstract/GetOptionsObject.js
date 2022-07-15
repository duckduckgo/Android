"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.GetOptionsObject = void 0;
/**
 * https://tc39.es/ecma402/#sec-getoptionsobject
 * @param options
 * @returns
 */
function GetOptionsObject(options) {
    if (typeof options === 'undefined') {
        return Object.create(null);
    }
    if (typeof options === 'object') {
        return options;
    }
    throw new TypeError('Options must be an object');
}
exports.GetOptionsObject = GetOptionsObject;
