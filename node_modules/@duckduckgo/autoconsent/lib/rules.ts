export type AutoConsentCMPRule = {
  name: string
  prehideSelectors?: string[]
  runContext?: RunContext,
  intermediate?: boolean
  detectCmp: AutoConsentRuleStep[]
  detectPopup: AutoConsentRuleStep[]
  optOut: AutoConsentRuleStep[]
  optIn: AutoConsentRuleStep[]
  openCmp?: AutoConsentRuleStep[]
  test?: AutoConsentRuleStep[]
}

export type RunContext = {
  main?: boolean,
  frame?: boolean,
  url?: string,
}

export type AutoConsentRuleStep = { optional?: boolean } & Partial<
  ElementExistsRule
> &
  Partial<ElementVisibleRule> &
  Partial<EvalRule> &
  Partial<WaitForRule> &
  Partial<WaitForVisibleRule> &
  Partial<ClickRule> &
  Partial<WaitForThenClickRule> &
  Partial<WaitRule> &
  Partial<UrlRule> &
  Partial<HideRule>;

export type ElementExistsRule = {
  exists: string;
};

export type VisibilityCheck = "any" | "all" | "none";

export type ElementVisibleRule = {
  visible: string;
  check?: VisibilityCheck;
};

export type EvalRule = {
  eval: string;
};

export type WaitForRule = {
  waitFor: string;
  timeout?: number;
};

export type WaitForVisibleRule = {
  waitForVisible: string;
  timeout?: number;
  check?: VisibilityCheck;
};

export type ClickRule = {
  click: string;
  all?: boolean;
};

export type WaitForThenClickRule = {
  waitForThenClick: string;
  timeout?: number;
};

export type WaitRule = {
  wait: number;
};

export type UrlRule = {
  url: string;
};

export type HideMethod = 'display' | 'opacity';

export type HideRule = {
  hide: string[];
  method?: HideMethod;
};
