"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CanonicalizeLocaleList = void 0;
/**
 * http://ecma-international.org/ecma-402/7.0/index.html#sec-canonicalizelocalelist
 * @param locales
 */
function CanonicalizeLocaleList(locales) {
    // TODO
    return Intl.getCanonicalLocales(locales);
}
exports.CanonicalizeLocaleList = CanonicalizeLocaleList;
