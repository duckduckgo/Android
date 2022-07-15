export interface ResolveLocaleResult {
    locale: string;
    dataLocale: string;
    [k: string]: any;
}
/**
 * https://tc39.es/ecma402/#sec-resolvelocale
 */
export declare function ResolveLocale<K extends string, D extends {
    [k in K]: any;
}>(availableLocales: Set<string>, requestedLocales: string[], options: {
    localeMatcher: string;
    [k: string]: string;
}, relevantExtensionKeys: K[], localeData: Record<string, D | undefined>, getDefaultLocale: () => string): ResolveLocaleResult;
//# sourceMappingURL=ResolveLocale.d.ts.map