"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SupportedLocales = void 0;
var _262_1 = require("./262");
var GetOption_1 = require("./GetOption");
var intl_localematcher_1 = require("@formatjs/intl-localematcher");
/**
 * https://tc39.es/ecma402/#sec-supportedlocales
 * @param availableLocales
 * @param requestedLocales
 * @param options
 */
function SupportedLocales(availableLocales, requestedLocales, options) {
    var matcher = 'best fit';
    if (options !== undefined) {
        options = (0, _262_1.ToObject)(options);
        matcher = (0, GetOption_1.GetOption)(options, 'localeMatcher', 'string', ['lookup', 'best fit'], 'best fit');
    }
    if (matcher === 'best fit') {
        return (0, intl_localematcher_1.LookupSupportedLocales)(availableLocales, requestedLocales);
    }
    return (0, intl_localematcher_1.LookupSupportedLocales)(availableLocales, requestedLocales);
}
exports.SupportedLocales = SupportedLocales;
