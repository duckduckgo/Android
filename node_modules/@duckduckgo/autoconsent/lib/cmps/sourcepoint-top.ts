import { doEval, elementExists, elementVisible } from "../rule-executors";
import { RunContext } from "../rules";
import AutoConsentCMPBase from "./base";

export default class SourcePoint extends AutoConsentCMPBase {
  prehideSelectors = ["div[id^='sp_message_container_'],.message-overlay"]

  constructor() {
    super("Sourcepoint-top");
  }

  runContext: RunContext = {
    main: true,
    frame: false,
  }

  get hasSelfTest(): boolean {
    return true;
  }

  get isIntermediate(): boolean {
    return true;
  }

  async detectCmp() {
    return elementExists("div[id^='sp_message_container_']");
  }

  async detectPopup() {
    return elementVisible("div[id^='sp_message_container_']", 'all');
  }

  async optIn() {
    return true;
  }

  async optOut() {
    return true;
  }

  async test() {
    await doEval("__tcfapi('getTCData', 2, r => window.__rcsResult = r)");
    return await doEval(
      "Object.values(window.__rcsResult.purpose.consents).every(c => !c)"
    );
  }
}
