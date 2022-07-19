import { NumberFormatOptions } from '@formatjs/ecma402-abstract';
export interface ExtendedNumberFormatOptions extends NumberFormatOptions {
    scale?: number;
}
export interface NumberSkeletonToken {
    stem: string;
    options: string[];
}
export declare function parseNumberSkeletonFromString(skeleton: string): NumberSkeletonToken[];
/**
 * https://github.com/unicode-org/icu/blob/master/docs/userguide/format_parse/numbers/skeletons.md#skeleton-stems-and-options
 */
export declare function parseNumberSkeleton(tokens: NumberSkeletonToken[]): ExtendedNumberFormatOptions;
//# sourceMappingURL=number.d.ts.map