"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Multiplexer = void 0;

/**
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class Multiplexer {
  constructor(reporters) {
    this._reporters = void 0;
    this._reporters = reporters;
  }

  printsToStdio() {
    return this._reporters.some(r => r.printsToStdio ? r.printsToStdio() : true);
  }

  onBegin(config, suite) {
    for (const reporter of this._reporters) {
      var _reporter$onBegin;

      (_reporter$onBegin = reporter.onBegin) === null || _reporter$onBegin === void 0 ? void 0 : _reporter$onBegin.call(reporter, config, suite);
    }
  }

  onTestBegin(test, result) {
    for (const reporter of this._reporters) wrap(() => {
      var _reporter$onTestBegin;

      return (_reporter$onTestBegin = reporter.onTestBegin) === null || _reporter$onTestBegin === void 0 ? void 0 : _reporter$onTestBegin.call(reporter, test, result);
    });
  }

  onStdOut(chunk, test, result) {
    for (const reporter of this._reporters) wrap(() => {
      var _reporter$onStdOut;

      return (_reporter$onStdOut = reporter.onStdOut) === null || _reporter$onStdOut === void 0 ? void 0 : _reporter$onStdOut.call(reporter, chunk, test, result);
    });
  }

  onStdErr(chunk, test, result) {
    for (const reporter of this._reporters) wrap(() => {
      var _reporter$onStdErr;

      return (_reporter$onStdErr = reporter.onStdErr) === null || _reporter$onStdErr === void 0 ? void 0 : _reporter$onStdErr.call(reporter, chunk, test, result);
    });
  }

  onTestEnd(test, result) {
    for (const reporter of this._reporters) wrap(() => {
      var _reporter$onTestEnd;

      return (_reporter$onTestEnd = reporter.onTestEnd) === null || _reporter$onTestEnd === void 0 ? void 0 : _reporter$onTestEnd.call(reporter, test, result);
    });
  }

  async onEnd(result) {
    for (const reporter of this._reporters) await Promise.resolve().then(() => {
      var _reporter$onEnd;

      return (_reporter$onEnd = reporter.onEnd) === null || _reporter$onEnd === void 0 ? void 0 : _reporter$onEnd.call(reporter, result);
    }).catch(e => console.error('Error in reporter', e));
  }

  onError(error) {
    for (const reporter of this._reporters) wrap(() => {
      var _reporter$onError;

      return (_reporter$onError = reporter.onError) === null || _reporter$onError === void 0 ? void 0 : _reporter$onError.call(reporter, error);
    });
  }

  onStepBegin(test, result, step) {
    for (const reporter of this._reporters) wrap(() => {
      var _onStepBegin, _ref;

      return (_onStepBegin = (_ref = reporter).onStepBegin) === null || _onStepBegin === void 0 ? void 0 : _onStepBegin.call(_ref, test, result, step);
    });
  }

  onStepEnd(test, result, step) {
    for (const reporter of this._reporters) {
      var _onStepEnd, _ref2;

      (_onStepEnd = (_ref2 = reporter).onStepEnd) === null || _onStepEnd === void 0 ? void 0 : _onStepEnd.call(_ref2, test, result, step);
    }
  }

}

exports.Multiplexer = Multiplexer;

function wrap(callback) {
  try {
    callback();
  } catch (e) {
    console.error('Error in reporter', e);
  }
}