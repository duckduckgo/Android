/* eslint-disable no-restricted-syntax,no-await-in-loop,no-underscore-dangle */

import { AutoCMP } from "../types";
import { AutoConsentCMPRule, AutoConsentRuleStep, RunContext } from "../rules";
import { enableLogs } from "../config";
import { click, doEval, elementExists, elementVisible, hide, wait, waitForElement, waitForThenClick, waitForVisible } from "../rule-executors";

export async function success(action: Promise<boolean>): Promise<boolean> {
  const result = await action;
  if (!result) {
    throw new Error(`Action failed: ${action} ${result}`)
  }
  return result
}

export const defaultRunContext: RunContext = {
  main: true,
  frame: false,
  url: "",
}

export default class AutoConsentCMPBase implements AutoCMP {

  name: string
  runContext: RunContext = defaultRunContext;

  constructor(name: string) {
    this.name = name;
  }

  get hasSelfTest(): boolean {
    throw new Error('Not Implemented');
  }

  get isIntermediate(): boolean {
    throw new Error('Not Implemented');
  }

  checkRunContext(): boolean {
    const runCtx: RunContext = {
      ...defaultRunContext,
      ...this.runContext,
    }

    const isTop = window.top === window;

    if (isTop && !runCtx.main) {
      return false;
    }

    if (!isTop && !runCtx.frame) {
      return false;
    }

    if (runCtx.url && !window.location.href.startsWith(runCtx.url)) {
      return false;
    }
    return true;
  }

  detectCmp(): Promise<boolean>  {
    throw new Error('Not Implemented');
  }

  async detectPopup() {
    return false;
  }

  optOut(): Promise<boolean> {
    throw new Error('Not Implemented');
  }

  optIn(): Promise<boolean> {
    throw new Error('Not Implemented');
  }

  openCmp(): Promise<boolean> {
    throw new Error('Not Implemented');
  }

  async test(): Promise<boolean> {
    // try IAB by default
    return Promise.resolve(true);
  }
}

async function evaluateRuleStep(rule: AutoConsentRuleStep) {
  const results = [];
  if (rule.exists) {
    results.push(elementExists(rule.exists));
  }
  if (rule.visible) {
    results.push(elementVisible(rule.visible, rule.check));
  }
  if (rule.eval) {
    const res = doEval(rule.eval)
    results.push(res);
  }
  if (rule.waitFor) {
    results.push(waitForElement(rule.waitFor, rule.timeout));
  }
  if (rule.waitForVisible) {
    results.push(waitForVisible(rule.waitForVisible, rule.timeout, rule.check));
  }
  if (rule.click) {
    results.push(click(rule.click, rule.all));
  }
  if (rule.waitForThenClick) {
    results.push(waitForThenClick(rule.waitForThenClick, rule.timeout, rule.all));
  }
  if (rule.wait) {
    results.push(wait(rule.wait));
  }
  if (rule.hide) {
    results.push(hide(rule.hide, rule.method));
  }

  if (results.length === 0) {
    enableLogs && console.warn('Unrecognized rule', rule);
    return false;
  }

  // boolean and of results
  const all = await Promise.all(results);
  return all.reduce((a, b) => a && b, true);
}

export class AutoConsentCMP extends AutoConsentCMPBase {

  constructor(public config: AutoConsentCMPRule) {
    super(config.name);
    this.runContext = config.runContext || defaultRunContext;
  }

  get hasSelfTest(): boolean {
    return !!this.config.test;
  }

  get isIntermediate(): boolean {
    return !!this.config.intermediate;
  }

  get prehideSelectors(): string[] {
    return this.config.prehideSelectors;
  }

  async _runRulesParallel(rules: AutoConsentRuleStep[]): Promise<boolean> {
    const results = rules.map(rule => evaluateRuleStep(rule));
    const detections = await Promise.all(results);
    return detections.every(r => !!r);
  }

  async _runRulesSequentially(rules: AutoConsentRuleStep[]): Promise<boolean> {
    for (const rule of rules) {
      enableLogs && console.log('Running rule...', rule);
      const result = await evaluateRuleStep(rule);
      enableLogs && console.log('...rule result', result);
      if (!result && !rule.optional) {
        return false;
      }
    }
    return true;
  }

  async detectCmp() {
    if (this.config.detectCmp) {
      return this._runRulesParallel(this.config.detectCmp);
    }
    return false;
  }

  async detectPopup() {
    if (this.config.detectPopup) {
      return this._runRulesParallel(this.config.detectPopup);
    }
    return false;
  }

  async optOut() {
    if (this.config.optOut) {
      enableLogs && console.log('Initiated optOut()', this.config.optOut);
      return this._runRulesSequentially(this.config.optOut);
    }
    return false;
  }

  async optIn() {
    if (this.config.optIn) {
      enableLogs && console.log('Initiated optIn()', this.config.optIn);
      return this._runRulesSequentially(this.config.optIn);
    }
    return false;
  }

  async openCmp() {
    if (this.config.openCmp) {
      return this._runRulesSequentially(this.config.openCmp);
    }
    return false;
  }

  async test() {
    if (this.hasSelfTest) {
      return this._runRulesSequentially(this.config.test);
    }
    return super.test();
  }
}
