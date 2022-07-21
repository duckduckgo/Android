"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.toExpectedTextValues = toExpectedTextValues;
exports.toMatchText = toMatchText;

var _utils = require("playwright-core/lib/utils");

var _util = require("../util");

var _expect = require("../expect");

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
async function toMatchText(matcherName, receiver, receiverType, query, expected, options = {}) {
  (0, _util.expectTypes)(receiver, [receiverType], matcherName);
  const matcherOptions = {
    isNot: this.isNot,
    promise: this.promise
  };

  if (!(typeof expected === 'string') && !(expected && typeof expected.test === 'function')) {
    throw new Error(this.utils.matcherErrorMessage(this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions), `${this.utils.EXPECTED_COLOR('expected')} value must be a string or regular expression`, this.utils.printWithType('Expected', expected, this.utils.printExpected)));
  }

  const timeout = (0, _util.currentExpectTimeout)(options);
  const {
    matches: pass,
    received,
    log
  } = await query(this.isNot, timeout, (0, _util.captureStackTrace)('expect.' + matcherName));
  const stringSubstring = options.matchSubstring ? 'substring' : 'string';
  const receivedString = received || '';
  const message = pass ? () => typeof expected === 'string' ? this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions) + '\n\n' + `Expected ${stringSubstring}: not ${this.utils.printExpected(expected)}\n` + `Received string: ${(0, _expect.printReceivedStringContainExpectedSubstring)(receivedString, receivedString.indexOf(expected), expected.length)}` + (0, _util.callLogText)(log) : this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions) + '\n\n' + `Expected pattern: not ${this.utils.printExpected(expected)}\n` + `Received string: ${(0, _expect.printReceivedStringContainExpectedResult)(receivedString, typeof expected.exec === 'function' ? expected.exec(receivedString) : null)}` + (0, _util.callLogText)(log) : () => {
    const labelExpected = `Expected ${typeof expected === 'string' ? stringSubstring : 'pattern'}`;
    const labelReceived = 'Received string';
    return this.utils.matcherHint(matcherName, undefined, undefined, matcherOptions) + '\n\n' + this.utils.printDiffOrStringify(expected, receivedString, labelExpected, labelReceived, this.expand !== false) + (0, _util.callLogText)(log);
  };
  return {
    message,
    pass
  };
}

function toExpectedTextValues(items, options = {}) {
  return items.map(i => ({
    string: (0, _utils.isString)(i) ? i : undefined,
    regexSource: (0, _utils.isRegExp)(i) ? i.source : undefined,
    regexFlags: (0, _utils.isRegExp)(i) ? i.flags : undefined,
    matchSubstring: options.matchSubstring,
    ignoreCase: options.ignoreCase,
    normalizeWhiteSpace: options.normalizeWhiteSpace
  }));
}