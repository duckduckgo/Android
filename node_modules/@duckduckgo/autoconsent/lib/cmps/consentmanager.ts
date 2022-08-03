import { click, elementExists, elementVisible, waitForElement } from "../rule-executors";
import AutoConsentCMPBase from "./base";

// Note: JS API is also available:
// https://help.consentmanager.net/books/cmp/page/javascript-api
export default class ConsentManager extends AutoConsentCMPBase {

  prehideSelectors = ["#cmpbox,#cmpbox2"]

  get hasSelfTest(): boolean {
    return false;
  }

  get isIntermediate(): boolean {
    return false;
  }

  constructor() {
    super("consentmanager.net");
  }

  async detectCmp() {
    return elementExists("#cmpbox");
  }

  async detectPopup() {
    return elementVisible("#cmpbox .cmpmore", 'any');
  }

  async optOut() {
    if (click(".cmpboxbtnno")) {
      return true;
    }

    if (elementExists(".cmpwelcomeprpsbtn")) {
      click(".cmpwelcomeprpsbtn > a[aria-checked=true]", true);
      click(".cmpboxbtnsave");
      return true;
    }

    click(".cmpboxbtncustom");
    await waitForElement(".cmptblbox", 2000);
    click(".cmptdchoice > a[aria-checked=true]", true);
    click(".cmpboxbtnyescustomchoices");
    return true;
  }

  async optIn() {
    return click(".cmpboxbtnyes");
  }
}
