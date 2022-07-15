"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ResolveLocale = exports.LookupSupportedLocales = exports.match = void 0;
var CanonicalizeLocaleList_1 = require("./abstract/CanonicalizeLocaleList");
var ResolveLocale_1 = require("./abstract/ResolveLocale");
function match(requestedLocales, availableLocales, defaultLocale, opts) {
    var locales = availableLocales.reduce(function (all, l) {
        all.add(l);
        return all;
    }, new Set());
    return (0, ResolveLocale_1.ResolveLocale)(locales, (0, CanonicalizeLocaleList_1.CanonicalizeLocaleList)(requestedLocales), {
        localeMatcher: (opts === null || opts === void 0 ? void 0 : opts.algorithm) || 'best fit',
    }, [], {}, function () { return defaultLocale; }).locale;
}
exports.match = match;
var LookupSupportedLocales_1 = require("./abstract/LookupSupportedLocales");
Object.defineProperty(exports, "LookupSupportedLocales", { enumerable: true, get: function () { return LookupSupportedLocales_1.LookupSupportedLocales; } });
var ResolveLocale_2 = require("./abstract/ResolveLocale");
Object.defineProperty(exports, "ResolveLocale", { enumerable: true, get: function () { return ResolveLocale_2.ResolveLocale; } });
