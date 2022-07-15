import { NumberFormatInternal } from '../types/number';
/**
 * The abstract operation ComputeExponentForMagnitude computes an exponent by which to scale a
 * number of the given magnitude (power of ten of the most significant digit) according to the
 * locale and the desired notation (scientific, engineering, or compact).
 */
export declare function ComputeExponentForMagnitude(numberFormat: Intl.NumberFormat, magnitude: number, { getInternalSlots, }: {
    getInternalSlots(nf: Intl.NumberFormat): NumberFormatInternal;
}): number;
//# sourceMappingURL=ComputeExponentForMagnitude.d.ts.map