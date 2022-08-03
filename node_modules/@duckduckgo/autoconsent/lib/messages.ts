import { Config, RuleBundle } from "./types";

export type BackgroundMessage =
  InitResponseMessage
  | EvalResponseMessage
  | OptOutMessage
  | OptInMessage
  | SelfTestMessage;

export type ContentScriptMessage =
  InitMessage
  | EvalMessage
  | DetectedMessage
  | FoundMessage
  | OptOutResultMessage
  | OptInResultMessage
  | SelfTestResultMessage
  | DoneMessage
  | ErrorMessage;

export type InitMessage = {
  type: "init";
  url: string;
};

export type EvalMessage = {
  type: "eval";
  id: string;
  code: string;
};

export type DetectedMessage = {
  type: "cmpDetected";
  cmp: string;
  url: string;
}

export type FoundMessage = {
  type: "popupFound";
  cmp: string;
  url: string;
}

export type OptOutResultMessage = {
  type: "optOutResult";
  cmp: string;
  result: boolean;
  scheduleSelfTest: boolean;
  url: string;
};

export type OptInResultMessage = {
  type: "optInResult";
  cmp: string;
  result: boolean;
  scheduleSelfTest: boolean;
  url: string;
};

export type SelfTestResultMessage = {
  type: "selfTestResult";
  cmp: string;
  result: boolean;
  url: string;
};

export type DoneMessage = {
  type: "autoconsentDone";
  cmp: string;
  url: string;
}

export type ErrorMessage = {
  type: "autoconsentError";
  details: any;
}

export type InitResponseMessage = {
  type: "initResp";
  rules: RuleBundle;
  config: Config;
}

export type EvalResponseMessage = {
  type: "evalResp";
  id: string;
  result: any;
}

export type OptOutMessage = {
  type: "optOut";
}

export type OptInMessage = {
  type: "optIn";
}

export type SelfTestMessage = {
  type: "selfTest";
}
