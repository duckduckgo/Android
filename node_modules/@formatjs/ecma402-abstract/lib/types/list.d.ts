import { LocaleData } from './core';
export declare type ListPatternLocaleData = LocaleData<ListPatternFieldsData>;
export interface ListPatternFieldsData {
    conjunction?: ListPatternData;
    disjunction?: ListPatternData;
    unit?: ListPatternData;
}
export interface ListPattern {
    start: string;
    middle: string;
    end: string;
    pair: string;
}
export interface ListPatternData {
    long: ListPattern;
    short?: ListPattern;
    narrow?: ListPattern;
}
//# sourceMappingURL=list.d.ts.map