import { LookupMatcherResult } from './types';
/**
 * https://tc39.es/ecma402/#sec-bestfitmatcher
 * @param availableLocales
 * @param requestedLocales
 * @param getDefaultLocale
 */
export declare function BestFitMatcher(availableLocales: Set<string>, requestedLocales: string[], getDefaultLocale: () => string): LookupMatcherResult;
//# sourceMappingURL=BestFitMatcher.d.ts.map