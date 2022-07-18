import { RawNumberFormatResult } from '../types/number';
/**
 * TODO: dedup with intl-pluralrules and support BigInt
 * https://tc39.es/ecma402/#sec-torawfixed
 * @param x a finite non-negative Number or BigInt
 * @param minFraction and integer between 0 and 20
 * @param maxFraction and integer between 0 and 20
 */
export declare function ToRawFixed(x: number, minFraction: number, maxFraction: number): RawNumberFormatResult;
//# sourceMappingURL=ToRawFixed.d.ts.map