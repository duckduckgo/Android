"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.InitializeNumberFormat = void 0;
var CanonicalizeLocaleList_1 = require("../CanonicalizeLocaleList");
var GetOption_1 = require("../GetOption");
var intl_localematcher_1 = require("@formatjs/intl-localematcher");
var SetNumberFormatUnitOptions_1 = require("./SetNumberFormatUnitOptions");
var CurrencyDigits_1 = require("./CurrencyDigits");
var SetNumberFormatDigitOptions_1 = require("./SetNumberFormatDigitOptions");
var utils_1 = require("../utils");
var CoerceOptionsToObject_1 = require("../CoerceOptionsToObject");
/**
 * https://tc39.es/ecma402/#sec-initializenumberformat
 */
function InitializeNumberFormat(nf, locales, opts, _a) {
    var getInternalSlots = _a.getInternalSlots, localeData = _a.localeData, availableLocales = _a.availableLocales, numberingSystemNames = _a.numberingSystemNames, getDefaultLocale = _a.getDefaultLocale, currencyDigitsData = _a.currencyDigitsData;
    // @ts-ignore
    var requestedLocales = (0, CanonicalizeLocaleList_1.CanonicalizeLocaleList)(locales);
    var options = (0, CoerceOptionsToObject_1.CoerceOptionsToObject)(opts);
    var opt = Object.create(null);
    var matcher = (0, GetOption_1.GetOption)(options, 'localeMatcher', 'string', ['lookup', 'best fit'], 'best fit');
    opt.localeMatcher = matcher;
    var numberingSystem = (0, GetOption_1.GetOption)(options, 'numberingSystem', 'string', undefined, undefined);
    if (numberingSystem !== undefined &&
        numberingSystemNames.indexOf(numberingSystem) < 0) {
        // 8.a. If numberingSystem does not match the Unicode Locale Identifier type nonterminal,
        // throw a RangeError exception.
        throw RangeError("Invalid numberingSystems: ".concat(numberingSystem));
    }
    opt.nu = numberingSystem;
    var r = (0, intl_localematcher_1.ResolveLocale)(availableLocales, requestedLocales, opt, 
    // [[RelevantExtensionKeys]] slot, which is a constant
    ['nu'], localeData, getDefaultLocale);
    var dataLocaleData = localeData[r.dataLocale];
    (0, utils_1.invariant)(!!dataLocaleData, "Missing locale data for ".concat(r.dataLocale));
    var internalSlots = getInternalSlots(nf);
    internalSlots.locale = r.locale;
    internalSlots.dataLocale = r.dataLocale;
    internalSlots.numberingSystem = r.nu;
    internalSlots.dataLocaleData = dataLocaleData;
    (0, SetNumberFormatUnitOptions_1.SetNumberFormatUnitOptions)(nf, options, { getInternalSlots: getInternalSlots });
    var style = internalSlots.style;
    var mnfdDefault;
    var mxfdDefault;
    if (style === 'currency') {
        var currency = internalSlots.currency;
        var cDigits = (0, CurrencyDigits_1.CurrencyDigits)(currency, { currencyDigitsData: currencyDigitsData });
        mnfdDefault = cDigits;
        mxfdDefault = cDigits;
    }
    else {
        mnfdDefault = 0;
        mxfdDefault = style === 'percent' ? 0 : 3;
    }
    var notation = (0, GetOption_1.GetOption)(options, 'notation', 'string', ['standard', 'scientific', 'engineering', 'compact'], 'standard');
    internalSlots.notation = notation;
    (0, SetNumberFormatDigitOptions_1.SetNumberFormatDigitOptions)(internalSlots, options, mnfdDefault, mxfdDefault, notation);
    var compactDisplay = (0, GetOption_1.GetOption)(options, 'compactDisplay', 'string', ['short', 'long'], 'short');
    if (notation === 'compact') {
        internalSlots.compactDisplay = compactDisplay;
    }
    var useGrouping = (0, GetOption_1.GetOption)(options, 'useGrouping', 'boolean', undefined, true);
    internalSlots.useGrouping = useGrouping;
    var signDisplay = (0, GetOption_1.GetOption)(options, 'signDisplay', 'string', ['auto', 'never', 'always', 'exceptZero'], 'auto');
    internalSlots.signDisplay = signDisplay;
    return nf;
}
exports.InitializeNumberFormat = InitializeNumberFormat;
