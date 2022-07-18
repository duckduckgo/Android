import { NumberFormatDigitInternalSlots } from '../types/number';
/**
 * https://tc39.es/ecma402/#sec-formatnumberstring
 */
export declare function FormatNumericToString(intlObject: Pick<NumberFormatDigitInternalSlots, 'roundingType' | 'minimumSignificantDigits' | 'maximumSignificantDigits' | 'minimumIntegerDigits' | 'minimumFractionDigits' | 'maximumFractionDigits'>, x: number): {
    roundedNumber: number;
    formattedString: string;
};
//# sourceMappingURL=FormatNumericToString.d.ts.map