/**
 * Returns the best matching date time pattern if a date time skeleton
 * pattern is provided with a locale. Follows the Unicode specification:
 * https://www.unicode.org/reports/tr35/tr35-dates.html#table-mapping-requested-time-skeletons-to-patterns
 * @param skeleton date time skeleton pattern that possibly includes j, J or C
 * @param locale
 */
export declare function getBestPattern(skeleton: string, locale: Intl.Locale): string;
//# sourceMappingURL=date-time-pattern-generator.d.ts.map