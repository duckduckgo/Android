import { click, elementExists, elementVisible } from "../rule-executors";
import { RunContext } from "../rules";
import { getStyleElement, hideElements } from "../utils";
import AutoConsentCMPBase from "./base";

const cookieSettingsButton = "#truste-show-consent";
const shortcutOptOut = '#truste-consent-required';
const shortcutOptIn = '#truste-consent-button';
const popupContent = '#truste-consent-content';
const bannerOverlay = '#trustarc-banner-overlay';
const bannerContainer = '#truste-consent-track';

export default class TrustArcTop extends AutoConsentCMPBase {

  prehideSelectors = [
    ".trustarc-banner-container",
    `.truste_popframe,.truste_overlay,.truste_box_overlay,${bannerContainer}`,
  ]
  runContext: RunContext = {
    main: true,
    frame: false,
  }

  _shortcutButton: HTMLElement;
  _optInDone: boolean;

  constructor() {
    super("TrustArc-top");
    this._shortcutButton = null; // indicates if the "reject all" button is detected
    this._optInDone = false;
  }

  get hasSelfTest(): boolean {
    return false;
  }

  get isIntermediate(): boolean {
    if (this._optInDone) {
      return false;
    }
    return !this._shortcutButton;
  }

  async detectCmp() {
    const result = elementExists(`${cookieSettingsButton},${bannerContainer}`);
    if (result) {
      // additionally detect the opt-out button
      this._shortcutButton = document.querySelector(shortcutOptOut);
    }
    return result;
  }

  async detectPopup() {
    // not every element should exist, but if it does, it's a popup
    return elementVisible(`${popupContent},${bannerOverlay},${bannerContainer}`, 'all');
  }

  openFrame() {
    click(cookieSettingsButton);
  }

  async optOut() {
    if (this._shortcutButton) {
      this._shortcutButton.click();
      return true;
    }

    // hide elements permanently, so user doesn't see the popup
    hideElements(
      getStyleElement(),
      [".truste_popframe", ".truste_overlay", ".truste_box_overlay", bannerContainer],
    );
    click(cookieSettingsButton);

    // schedule cleanup
    setTimeout(() => {
      getStyleElement().remove();
    }, 10000);

    return true;
  }

  async optIn() {
    this._optInDone = true; // just a hack to force autoconsentDone
    return click(shortcutOptIn);
  }

  async openCmp() {
    // await tab.eval("truste.eu.clickListener()");
    return true;
  }

  async test() {
    // TODO: find out how to test TrustArc
    return true;
  }
}
