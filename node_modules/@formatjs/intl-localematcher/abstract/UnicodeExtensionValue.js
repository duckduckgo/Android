"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.UnicodeExtensionValue = void 0;
var utils_1 = require("./utils");
/**
 * https://tc39.es/ecma402/#sec-unicodeextensionvalue
 * @param extension
 * @param key
 */
function UnicodeExtensionValue(extension, key) {
    (0, utils_1.invariant)(key.length === 2, 'key must have 2 elements');
    var size = extension.length;
    var searchValue = "-".concat(key, "-");
    var pos = extension.indexOf(searchValue);
    if (pos !== -1) {
        var start = pos + 4;
        var end = start;
        var k = start;
        var done = false;
        while (!done) {
            var e = extension.indexOf('-', k);
            var len = void 0;
            if (e === -1) {
                len = size - k;
            }
            else {
                len = e - k;
            }
            if (len === 2) {
                done = true;
            }
            else if (e === -1) {
                end = size;
                done = true;
            }
            else {
                end = e;
                k = e + 1;
            }
        }
        return extension.slice(start, end);
    }
    searchValue = "-".concat(key);
    pos = extension.indexOf(searchValue);
    if (pos !== -1 && pos + 3 === size) {
        return '';
    }
    return undefined;
}
exports.UnicodeExtensionValue = UnicodeExtensionValue;
