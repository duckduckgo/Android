import { click, elementExists, elementVisible, waitForElement } from "../rule-executors";
import { RunContext } from "../rules";
import { waitFor } from "../utils";
import AutoConsentCMPBase from "./base";

export default class TrustArcFrame extends AutoConsentCMPBase {
  constructor() {
    super("TrustArc-frame");
  }

  runContext: RunContext = {
    main: false,
    frame: true,
    url: "https://consent-pref.trustarc.com/?",
  }

  get hasSelfTest(): boolean {
    return false;
  }

  get isIntermediate(): boolean {
    return false;
  }

  async detectCmp() {
    return true;
  }

  async detectPopup() {
    // we're already inside the popup
    return elementVisible("#defaultpreferencemanager", 'any') && elementVisible(".mainContent", 'any');
  }

  async navigateToSettings() {
    // wait for it to load
    await waitFor(
      async () => {
        return (
          elementExists(".shp") ||
          elementVisible(".advance", 'any') ||
          elementExists(".switch span:first-child")
        );
      },
      10,
      500
    );
    // splash screen -> hit more information
    if (elementExists(".shp")) {
      click(".shp");
    }

    await waitForElement(".prefPanel", 5000);

    // go to advanced settings if not yet shown
    if (elementVisible(".advance", 'any')) {
      click(".advance");
    }

    // takes a while to load the opt-in/opt-out buttons
    return await waitFor(
      () => elementVisible(".switch span:first-child", 'any'),
      5,
      1000
    );
  }

  async optOut() {
    await waitFor(() => document.readyState === 'complete', 20, 100);
    await waitForElement(".mainContent[aria-hidden=false]", 5000);

    if (click(".rejectAll")) {
      return true;
    }

    if (elementExists('.prefPanel')) {
      await waitForElement('.prefPanel[style="visibility: visible;"]', 3000);
    }

    if (click("#catDetails0")) {
      click(".submit");
      return true;
    }

    if (click(".required")) {
      return true;
    }

    await this.navigateToSettings();

    click(".switch span:nth-child(1):not(.active)", true);

    click(".submit");

    // at this point, iframe usually closes. Sometimes we need to close manually, but we don't wait for it to report success
    waitForElement("#gwt-debug-close_id", 300000).then(() => {
      click("#gwt-debug-close_id");
    });

    return true;
  }

  async optIn() {
    await this.navigateToSettings();
    click(".switch span:nth-child(2)", true);

    click(".submit");

    // at this point, iframe usually closes. Sometimes we need to close manually, but we don't wait for it to report success
    waitForElement("#gwt-debug-close_id", 300000).then(() => {
      click("#gwt-debug-close_id");
    });

    return true;
  }
}
