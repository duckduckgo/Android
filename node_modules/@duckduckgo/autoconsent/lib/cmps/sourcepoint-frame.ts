import { enableLogs } from "../config";
import { click, elementExists, wait, waitForElement } from "../rule-executors";
import { RunContext } from "../rules";
import { waitFor } from "../utils";
import AutoConsentCMPBase from "./base";

export default class SourcePoint extends AutoConsentCMPBase {

  ccpaMode = false;

  runContext: RunContext = {
    main: false,
    frame: true,
  }

  constructor() {
    super("Sourcepoint-frame");
  }

  get hasSelfTest(): boolean {
    return false; // self-test is done by parent frame
  }

  get isIntermediate(): boolean {
    return false;
  }

  async detectCmp() {
    const url = new URL(location.href);
    if (url.searchParams.has('message_id') && url.hostname === 'ccpa-notice.sp-prod.net') {
      this.ccpaMode = true;
      return true;
    }
    return (url.pathname === '/index.html' || url.pathname === '/privacy-manager/index.html')
        && url.searchParams.has('message_id') && url.searchParams.has('requestUUID');
  }

  async detectPopup() {
    return true;
  }

  async optIn() {
    if (click(".sp_choice_type_11")) {
      return true;
    }

    if (click('.sp_choice_type_ACCEPT_ALL')) {
      return true;
    }
    return false;
  }

  isManagerOpen() {
    return (new URL(location.href)).pathname === "/privacy-manager/index.html";
  }

  async optOut() {
    if (!this.isManagerOpen()) {
      if (!elementExists("button.sp_choice_type_12")) {
        // do not sell button
        click("button.sp_choice_type_13");
        return true;
      }

      click("button.sp_choice_type_12");
      await waitFor(
        () => location.pathname === "/privacy-manager/index.html",
        200,
        100
      );
    }

    await waitForElement('.type-modal', 20000);
    // reject all button is offered by some sites
    try {
      const rejectSelector1 = '.sp_choice_type_REJECT_ALL';
      const rejectSelector2 = '.reject-toggle';
      const path = await Promise.race([
        waitForElement(rejectSelector1, 2000).then(success => success ? 0: -1),
        waitForElement(rejectSelector2, 2000).then(success => success ? 1: -1),
        waitForElement('.pm-features', 2000).then(success => success ? 2: -1),
      ]);
      if (path === 0) {
        await wait(1000);
        click(rejectSelector1);
        return true;
      } else if (path === 1) {
        click(rejectSelector2);
      } else if (path === 2) {
        // TODO: check if this is still working
        await waitForElement('.pm-features', 10000);
        click('.checked > span', true);

        click('.chevron');
      }
    } catch (e) {
      enableLogs && console.warn(e);
    }
    click('.sp_choice_type_SAVE_AND_EXIT');
    return true;
  }
}
