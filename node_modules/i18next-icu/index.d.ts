declare module "i18next-icu" {
  import { i18n, ThirdPartyModule } from "i18next";

  /**
   * @see https://github.com/yahoo/intl-messageformat#user-defined-formats
   * @see https://github.com/i18next/i18next-icu/issues/12#issuecomment-578893063
   */
  // prettier-ignore
  export interface IcuFormats {
    number?: {
      [styleName: string]: Intl.NumberFormatOptions;
    },
    date?: {
      [styleName: string]: Intl.DateTimeFormatOptions;
    },
    time?: {
      [styleName: string]: Intl.DateTimeFormatOptions;
    }
  }

  export interface IcuConfig {
    memoize?: boolean;
    memoizeFallback?: boolean;
    formats?: IcuFormats;
    bindI18n?: string;
    bindI18nStore?: string;
    parseErrorHandler?: (err: Error, key: string, res: string, options: Object) => string;
  }

  export interface IcuInstance<TOptions = IcuConfig> extends ThirdPartyModule {
    init(i18next: i18n, options?: TOptions): void;
    addUserDefinedFormats(formats: IcuFormats): void;
    clearCache(): void;
  }

  interface IcuConstructor {
    new (config?: IcuConfig): IcuInstance;
    type: "i18nFormat";
  }

  const ICU: IcuConstructor;
  export default ICU;
}
