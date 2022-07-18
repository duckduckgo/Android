import { parse, MessageFormatElement } from '@formatjs/icu-messageformat-parser';
import { Formatters, Formats, FormatXMLElementFn, PrimitiveType, MessageFormatPart } from './formatters';
export interface Options {
    formatters?: Formatters;
    /**
     * Whether to treat HTML/XML tags as string literal
     * instead of parsing them as tag token.
     * When this is false we only allow simple tags without
     * any attributes
     */
    ignoreTag?: boolean;
}
export declare class IntlMessageFormat {
    private readonly ast;
    private readonly locales;
    private readonly resolvedLocale;
    private readonly formatters;
    private readonly formats;
    private readonly message;
    private readonly formatterCache;
    constructor(message: string | MessageFormatElement[], locales?: string | string[], overrideFormats?: Partial<Formats>, opts?: Options);
    format: <T = void>(values?: Record<string, PrimitiveType | T | FormatXMLElementFn<T, string | T | (string | T)[]>> | undefined) => string | T | (string | T)[];
    formatToParts: <T>(values?: Record<string, PrimitiveType | T | FormatXMLElementFn<T, string | T | (string | T)[]>> | undefined) => MessageFormatPart<T>[];
    resolvedOptions: () => {
        locale: string;
    };
    getAst: () => MessageFormatElement[];
    private static memoizedDefaultLocale;
    static get defaultLocale(): string;
    static resolveLocale: (locales: string | string[]) => Intl.Locale;
    static __parse: typeof parse | undefined;
    static formats: Formats;
}
//# sourceMappingURL=core.d.ts.map