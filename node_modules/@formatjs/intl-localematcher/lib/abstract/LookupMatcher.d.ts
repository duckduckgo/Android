import { LookupMatcherResult } from './types';
/**
 * https://tc39.es/ecma402/#sec-lookupmatcher
 * @param availableLocales
 * @param requestedLocales
 * @param getDefaultLocale
 */
export declare function LookupMatcher(availableLocales: Set<string>, requestedLocales: string[], getDefaultLocale: () => string): LookupMatcherResult;
//# sourceMappingURL=LookupMatcher.d.ts.map