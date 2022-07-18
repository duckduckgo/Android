export declare type Formats = Pick<Intl.DateTimeFormatOptions, 'weekday' | 'era' | 'year' | 'month' | 'day' | 'hour' | 'minute' | 'second' | 'timeZoneName'> & {
    fractionalSecondDigits?: 0 | 1 | 2;
    hour12?: boolean;
    pattern: string;
    pattern12: string;
    skeleton: string;
    rawPattern: string;
    rangePatterns: Record<TABLE_2 | 'default', RangePatterns>;
    rangePatterns12: Record<TABLE_2 | 'default', RangePatterns>;
};
export interface IntlDateTimeFormatInternal {
    locale: string;
    dataLocale: string;
    calendar?: string;
    dateStyle?: 'full' | 'long' | 'medium' | 'short';
    timeStyle?: 'full' | 'long' | 'medium' | 'short';
    weekday: 'narrow' | 'short' | 'long';
    era: 'narrow' | 'short' | 'long';
    year: '2-digit' | 'numeric';
    month: '2-digit' | 'numeric' | 'narrow' | 'short' | 'long';
    day: '2-digit' | 'numeric';
    dayPeriod: 'narrow' | 'short' | 'long';
    hour: '2-digit' | 'numeric';
    minute: '2-digit' | 'numeric';
    second: '2-digit' | 'numeric';
    timeZoneName: 'short' | 'long';
    fractionalSecondDigits?: 1 | 2 | 3;
    hourCycle: string;
    numberingSystem: string;
    timeZone: string;
    pattern: string;
    format: Formats;
    rangePatterns: Record<TABLE_2 | 'default', RangePatterns>;
    boundFormat?: Intl.DateTimeFormat['format'];
}
export interface RangePatternPart<T extends RangePatternType = RangePatternType> {
    source: T;
    pattern: string;
}
export declare type RangePatterns = Pick<Intl.DateTimeFormatOptions, 'weekday' | 'era' | 'year' | 'month' | 'day' | 'hour' | 'minute' | 'second' | 'timeZoneName'> & {
    hour12?: boolean;
    patternParts: Array<RangePatternPart>;
};
export declare enum RangePatternType {
    startRange = "startRange",
    shared = "shared",
    endRange = "endRange"
}
export declare type TABLE_6 = 'weekday' | 'era' | 'year' | 'month' | 'day' | 'hour' | 'minute' | 'second' | 'fractionalSecondDigits' | 'timeZoneName';
export declare type TABLE_2 = 'era' | 'year' | 'month' | 'day' | 'dayPeriod' | 'ampm' | 'hour' | 'minute' | 'second' | 'fractionalSecondDigits';
export declare type TimeZoneNameData = Record<string, {
    long?: [string, string];
    short?: [string, string];
}>;
export interface EraData {
    BC: string;
    AD: string;
}
export interface DateTimeFormatLocaleInternalData {
    am: string;
    pm: string;
    weekday: {
        narrow: string[];
        long: string[];
        short: string[];
    };
    era: {
        narrow: EraData;
        long: EraData;
        short: EraData;
    };
    month: {
        narrow: string[];
        long: string[];
        short: string[];
    };
    timeZoneName: TimeZoneNameData;
    /**
     * So we can construct GMT+08:00
     */
    gmtFormat: string;
    /**
     * So we can construct GMT+08:00
     */
    hourFormat: string;
    hourCycle: string;
    dateFormat: {
        full: Formats;
        long: Formats;
        medium: Formats;
        short: Formats;
    };
    timeFormat: {
        full: Formats;
        long: Formats;
        medium: Formats;
        short: Formats;
    };
    dateTimeFormat: {
        full: string;
        long: string;
        medium: string;
        short: string;
    };
    formats: Record<string, Formats[]>;
    nu: string[];
    hc: string[];
    ca: string[];
}
export declare type IntervalFormatsData = {
    intervalFormatFallback: string;
} & Record<string, Record<string, string>>;
export interface DateTimeFormat extends Omit<Intl.DateTimeFormat, 'resolvedOptions'> {
    resolvedOptions(): ResolvedDateTimeFormatOptions;
    formatRange(startDate: number | Date, endDate: number | Date): string;
    formatRangeToParts(startDate: number | Date, endDate: number | Date): IntlDateTimeFormatPart[];
}
export interface ResolvedDateTimeFormatOptions extends Intl.ResolvedDateTimeFormatOptions {
    dateStyle?: 'full' | 'long' | 'medium' | 'short';
    timeStyle?: 'full' | 'long' | 'medium' | 'short';
    numberingSystem: string;
}
export declare type UnpackedZoneData = [
    number,
    string,
    number,
    boolean
];
export declare type IntlDateTimeFormatPartType = Intl.DateTimeFormatPartTypes | 'ampm' | 'relatedYear' | 'yearName' | 'unknown' | 'fractionalSecondDigits';
export interface IntlDateTimeFormatPart {
    type: IntlDateTimeFormatPartType;
    value: string | undefined;
    source?: RangePatternType;
}
//# sourceMappingURL=date-time.d.ts.map