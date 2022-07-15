/**
 * https://tc39.es/ecma262/#sec-tostring
 */
export declare function ToString(o: unknown): string;
/**
 * https://tc39.es/ecma262/#sec-tonumber
 * @param val
 */
export declare function ToNumber(val: any): number;
/**
 * https://tc39.es/ecma262/#sec-timeclip
 * @param time
 */
export declare function TimeClip(time: number): number;
/**
 * https://tc39.es/ecma262/#sec-toobject
 * @param arg
 */
export declare function ToObject<T>(arg: T): T extends null ? never : T extends undefined ? never : T;
/**
 * https://www.ecma-international.org/ecma-262/11.0/index.html#sec-samevalue
 * @param x
 * @param y
 */
export declare function SameValue(x: any, y: any): boolean;
/**
 * https://www.ecma-international.org/ecma-262/11.0/index.html#sec-arraycreate
 * @param len
 */
export declare function ArrayCreate(len: number): any[];
/**
 * https://www.ecma-international.org/ecma-262/11.0/index.html#sec-hasownproperty
 * @param o
 * @param prop
 */
export declare function HasOwnProperty(o: object, prop: string): boolean;
/**
 * https://www.ecma-international.org/ecma-262/11.0/index.html#sec-type
 * @param x
 */
export declare function Type(x: any): "Null" | "Undefined" | "Object" | "Number" | "Boolean" | "String" | "Symbol" | "BigInt" | undefined;
/**
 * https://tc39.es/ecma262/#eqn-Day
 * @param t
 */
export declare function Day(t: number): number;
/**
 * https://tc39.es/ecma262/#sec-week-day
 * @param t
 */
export declare function WeekDay(t: number): number;
/**
 * https://tc39.es/ecma262/#sec-year-number
 * @param y
 */
export declare function DayFromYear(y: number): number;
/**
 * https://tc39.es/ecma262/#sec-year-number
 * @param y
 */
export declare function TimeFromYear(y: number): number;
/**
 * https://tc39.es/ecma262/#sec-year-number
 * @param t
 */
export declare function YearFromTime(t: number): number;
export declare function DaysInYear(y: number): 365 | 366;
export declare function DayWithinYear(t: number): number;
export declare function InLeapYear(t: number): 0 | 1;
/**
 * https://tc39.es/ecma262/#sec-month-number
 * @param t
 */
export declare function MonthFromTime(t: number): 0 | 1 | 2 | 3 | 4 | 7 | 5 | 6 | 8 | 9 | 10 | 11;
export declare function DateFromTime(t: number): number;
export declare function HourFromTime(t: number): number;
export declare function MinFromTime(t: number): number;
export declare function SecFromTime(t: number): number;
/**
 * The abstract operation OrdinaryHasInstance implements
 * the default algorithm for determining if an object O
 * inherits from the instance object inheritance path
 * provided by constructor C.
 * @param C class
 * @param O object
 * @param internalSlots internalSlots
 */
export declare function OrdinaryHasInstance(C: Object, O: any, internalSlots?: {
    boundTargetFunction: any;
}): boolean;
export declare function msFromTime(t: number): number;
//# sourceMappingURL=262.d.ts.map