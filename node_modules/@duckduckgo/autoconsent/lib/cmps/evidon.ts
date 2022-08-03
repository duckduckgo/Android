import { click, elementExists, elementVisible, waitForElement } from "../rule-executors";
import { getStyleElement, hideElements } from "../utils";
import AutoConsentCMPBase from "./base";

export default class Evidon extends AutoConsentCMPBase {
  constructor() {
    super("Evidon");
  }

  get hasSelfTest(): boolean {
    return false;
  }

  get isIntermediate(): boolean {
    return false;
  }

  async detectCmp() {
    return elementExists("#_evidon_banner");
  }

  async detectPopup() {
    return elementVisible("#_evidon_banner", 'any');
  }

  async optOut() {
    if (click("#_evidon-decline-button")) {
      return true;
    }

    hideElements(getStyleElement(), ["#evidon-prefdiag-overlay", "#evidon-prefdiag-background"]);
    click("#_evidon-option-button");

    await waitForElement("#evidon-prefdiag-overlay", 5000);

    click("#evidon-prefdiag-decline");
    return true;
  }

  async optIn() {
    return click("#_evidon-accept-button");
  }
}
