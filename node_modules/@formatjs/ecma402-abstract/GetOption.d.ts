/**
 * https://tc39.es/ecma402/#sec-getoption
 * @param opts
 * @param prop
 * @param type
 * @param values
 * @param fallback
 */
export declare function GetOption<T extends object, K extends keyof T, F>(opts: T, prop: K, type: 'string' | 'boolean', values: T[K][] | undefined, fallback: F): Exclude<T[K], undefined> | F;
//# sourceMappingURL=GetOption.d.ts.map