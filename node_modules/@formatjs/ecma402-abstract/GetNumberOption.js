"use strict";
/**
 * https://tc39.es/ecma402/#sec-getnumberoption
 * @param options
 * @param property
 * @param min
 * @param max
 * @param fallback
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.GetNumberOption = void 0;
var DefaultNumberOption_1 = require("./DefaultNumberOption");
function GetNumberOption(options, property, minimum, maximum, fallback) {
    var val = options[property];
    // @ts-expect-error
    return (0, DefaultNumberOption_1.DefaultNumberOption)(val, minimum, maximum, fallback);
}
exports.GetNumberOption = GetNumberOption;
