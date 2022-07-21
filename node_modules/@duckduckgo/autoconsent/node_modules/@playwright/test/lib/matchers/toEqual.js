"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.toEqual = toEqual;

var _util = require("../util");

var _stackTrace = require("playwright-core/lib/utils/stackTrace");

/**
 * Copyright Microsoft Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Omit colon and one or more spaces, so can call getLabelPrinter.
const EXPECTED_LABEL = 'Expected';
const RECEIVED_LABEL = 'Received'; // The optional property of matcher context is true if undefined.

const isExpand = expand => expand !== false;

async function toEqual(matcherName, receiver, receiverType, query, expected, options = {}) {
  (0, _util.expectTypes)(receiver, [receiverType], matcherName);
  const matcherOptions = {
    comment: options.contains ? '' : 'deep equality',
    isNot: this.isNot,
    promise: this.promise
  };
  const timeout = (0, _util.currentExpectTimeout)(options);
  const customStackTrace = (0, _stackTrace.captureStackTrace)();
  customStackTrace.apiName = 'expect.' + matcherName;
  const {
    matches: pass,
    received,
    log
  } = await query(this.isNot, timeout, customStackTrace);
  const message = pass ? () => this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions) + '\n\n' + `Expected: not ${this.utils.printExpected(expected)}\n` + (this.utils.stringify(expected) !== this.utils.stringify(received) ? `Received:     ${this.utils.printReceived(received)}` : '') + (0, _util.callLogText)(log) : () => this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions) + '\n\n' + this.utils.printDiffOrStringify(expected, received, EXPECTED_LABEL, RECEIVED_LABEL, isExpand(this.expand)) + (0, _util.callLogText)(log); // Passing the actual and expected objects so that a custom reporter
  // could access them, for example in order to display a custom visual diff,
  // or create a different error message

  return {
    actual: received,
    expected,
    message,
    name: matcherName,
    pass
  };
}