import { NumberFormatOptionsStyle, NumberFormatOptionsNotation, NumberFormatOptionsCompactDisplay, NumberFormatOptionsCurrencyDisplay, NumberFormatOptionsCurrencySign, NumberFormatOptionsUnitDisplay, NumberFormatLocaleInternalData, NumberFormatPart } from '../types/number';
interface NumberResult {
    formattedString: string;
    roundedNumber: number;
    sign: -1 | 0 | 1;
    exponent: number;
    magnitude: number;
}
export default function formatToParts(numberResult: NumberResult, data: NumberFormatLocaleInternalData, pl: Intl.PluralRules, options: {
    numberingSystem: string;
    useGrouping: boolean;
    style: NumberFormatOptionsStyle;
    notation: NumberFormatOptionsNotation;
    compactDisplay?: NumberFormatOptionsCompactDisplay;
    currency?: string;
    currencyDisplay?: NumberFormatOptionsCurrencyDisplay;
    currencySign?: NumberFormatOptionsCurrencySign;
    unit?: string;
    unitDisplay?: NumberFormatOptionsUnitDisplay;
}): NumberFormatPart[];
export {};
//# sourceMappingURL=format_to_parts.d.ts.map