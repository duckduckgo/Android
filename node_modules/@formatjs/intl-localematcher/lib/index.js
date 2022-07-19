import { CanonicalizeLocaleList } from './abstract/CanonicalizeLocaleList';
import { ResolveLocale } from './abstract/ResolveLocale';
export function match(requestedLocales, availableLocales, defaultLocale, opts) {
    var locales = availableLocales.reduce(function (all, l) {
        all.add(l);
        return all;
    }, new Set());
    return ResolveLocale(locales, CanonicalizeLocaleList(requestedLocales), {
        localeMatcher: (opts === null || opts === void 0 ? void 0 : opts.algorithm) || 'best fit',
    }, [], {}, function () { return defaultLocale; }).locale;
}
export { LookupSupportedLocales } from './abstract/LookupSupportedLocales';
export { ResolveLocale } from './abstract/ResolveLocale';
