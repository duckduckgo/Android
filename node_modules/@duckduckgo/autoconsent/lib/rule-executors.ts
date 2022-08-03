import { enableLogs } from "./config";
import { requestEval } from "./eval-handler";
import { HideMethod, VisibilityCheck } from "./rules";
import { getStyleElement, hideElements, isElementVisible, waitFor } from "./utils";

export function doEval(expr: string): Promise<boolean> {
  return requestEval(expr).catch((e) => {
    enableLogs && console.error('error evaluating rule', expr, e);
    return false;
  });
}

export function click(selector: string, all = false): boolean {
  const elem = document.querySelectorAll<HTMLElement>(selector);
  enableLogs && console.log("[click]", selector, all, elem);
  if (elem.length > 0) {
    if (all) {
      elem.forEach((e) => e.click());
    } else {
      elem[0].click();
    }
  }
  return elem.length > 0;
}

export function elementExists(selector: string): boolean {
  const exists = document.querySelector(selector) !== null;
  // enableLogs && console.log("[exists?]", selector, exists);
  return exists;
}

export function elementVisible(selector: string, check: VisibilityCheck): boolean {
  const elem = document.querySelectorAll<HTMLElement>(selector);
    const results = new Array(elem.length);
    elem.forEach((e, i) => {
      // check for display: none
      results[i] = isElementVisible(e);
    });
    // enableLogs && console.log("[visible?]", selector, check, elem, results);
    if (results.length === 0) {
      return false;
    } else if (check === "any") {
      return results.some(r => r);
    } else if (check === "none") {
      return results.every(r => !r);
    }
    // all
    return results.every(r => r);
}

export function waitForElement(selector: string, timeout = 10000): Promise<boolean> {
  const interval = 200;
  const times = Math.ceil((timeout) / interval);
  // enableLogs && console.log("[waitFor]", ruleStep.waitFor);
  return waitFor(
    () => document.querySelector(selector) !== null,
    times,
    interval
  );
}

export function waitForVisible(selector: string, timeout = 10000, check: VisibilityCheck = 'any'): Promise<boolean> {
  const interval = 200;
  const times = Math.ceil((timeout) / interval);
  // enableLogs && console.log("[waitForVisible]", ruleStep.waitFor);
  return waitFor(
    () => elementVisible(selector, check),
    times,
    interval
  );
}

export async function waitForThenClick(selector: string, timeout = 10000, all = false): Promise<boolean> {
  // enableLogs && console.log("[waitForThenClick]", ruleStep.waitForThenClick);
  await waitForElement(selector, timeout);
  return click(selector, all);
}

export function wait(ms: number): Promise<true> {
  // enableLogs && console.log(`waiting for ${ruleStep.wait}ms`);
  return new Promise(resolve => {
    setTimeout(() => {
      // enableLogs && console.log(`done waiting`);
      resolve(true);
    }, ms);
  });
}

export function hide(selectors: string[], method: HideMethod): boolean {
  // enableLogs && console.log("[hide]", ruleStep.hide, ruleStep.method);
  const styleEl = getStyleElement();
  return hideElements(styleEl, selectors, method);
}

export function prehide(selectors: string[]): boolean {
  const styleEl = getStyleElement('autoconsent-prehide');
  enableLogs && console.log("[prehide]", styleEl, location.href);
  return hideElements(styleEl, selectors, "opacity");
}

export function undoPrehide(): boolean {
  const existingElement = getStyleElement('autoconsent-prehide');
  enableLogs && console.log("[undoprehide]", existingElement, location.href);
  if (existingElement) {
    existingElement.remove();
  }
  return !!existingElement;
}
