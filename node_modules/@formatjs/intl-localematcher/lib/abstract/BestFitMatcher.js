import { BestAvailableLocale } from './BestAvailableLocale';
import { UNICODE_EXTENSION_SEQUENCE_REGEX } from './utils';
/**
 * https://tc39.es/ecma402/#sec-bestfitmatcher
 * @param availableLocales
 * @param requestedLocales
 * @param getDefaultLocale
 */
export function BestFitMatcher(availableLocales, requestedLocales, getDefaultLocale) {
    var minimizedAvailableLocaleMap = {};
    var availableLocaleMap = {};
    var canonicalizedLocaleMap = {};
    var minimizedAvailableLocales = new Set();
    availableLocales.forEach(function (locale) {
        var minimizedLocale = new Intl.Locale(locale)
            .minimize()
            .toString();
        var canonicalizedLocale = Intl.getCanonicalLocales(locale)[0] || locale;
        minimizedAvailableLocaleMap[minimizedLocale] = locale;
        availableLocaleMap[locale] = locale;
        canonicalizedLocaleMap[canonicalizedLocale] = locale;
        minimizedAvailableLocales.add(minimizedLocale);
        minimizedAvailableLocales.add(locale);
        minimizedAvailableLocales.add(canonicalizedLocale);
    });
    var foundLocale;
    for (var _i = 0, requestedLocales_1 = requestedLocales; _i < requestedLocales_1.length; _i++) {
        var l = requestedLocales_1[_i];
        if (foundLocale) {
            break;
        }
        var noExtensionLocale = l.replace(UNICODE_EXTENSION_SEQUENCE_REGEX, '');
        if (availableLocales.has(noExtensionLocale)) {
            foundLocale = noExtensionLocale;
            break;
        }
        if (minimizedAvailableLocales.has(noExtensionLocale)) {
            foundLocale = noExtensionLocale;
            break;
        }
        var locale = new Intl.Locale(noExtensionLocale);
        var maximizedRequestedLocale = locale.maximize().toString();
        var minimizedRequestedLocale = locale.minimize().toString();
        // Check minimized locale
        if (minimizedAvailableLocales.has(minimizedRequestedLocale)) {
            foundLocale = minimizedRequestedLocale;
            break;
        }
        // Lookup algo on maximized locale
        foundLocale = BestAvailableLocale(minimizedAvailableLocales, maximizedRequestedLocale);
    }
    if (!foundLocale) {
        return { locale: getDefaultLocale() };
    }
    return {
        locale: availableLocaleMap[foundLocale] ||
            canonicalizedLocaleMap[foundLocale] ||
            minimizedAvailableLocaleMap[foundLocale] ||
            foundLocale,
    };
}
