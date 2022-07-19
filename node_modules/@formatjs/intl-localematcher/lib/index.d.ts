export interface Opts {
    algorithm: 'lookup' | 'best fit';
}
export declare function match(requestedLocales: string[], availableLocales: string[], defaultLocale: string, opts?: Opts): string;
export { LookupSupportedLocales } from './abstract/LookupSupportedLocales';
export { ResolveLocale } from './abstract/ResolveLocale';
//# sourceMappingURL=index.d.ts.map