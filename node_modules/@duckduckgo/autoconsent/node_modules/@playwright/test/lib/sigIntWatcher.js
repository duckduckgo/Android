"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SigIntWatcher = void 0;

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
class SigIntWatcher {
  constructor() {
    this._hadSignal = false;
    this._sigintPromise = void 0;
    this._sigintHandler = void 0;
    let sigintCallback;
    this._sigintPromise = new Promise(f => sigintCallback = f);

    this._sigintHandler = () => {
      // We remove the handler so that second Ctrl+C immediately kills the runner
      // via the default sigint handler. This is handy in the case where our shutdown
      // takes a lot of time or is buggy.
      //
      // When running through NPM we might get multiple SIGINT signals
      // for a single Ctrl+C - this is an NPM bug present since at least NPM v6.
      // https://github.com/npm/cli/issues/1591
      // https://github.com/npm/cli/issues/2124
      //
      // Therefore, removing the handler too soon will just kill the process
      // with default handler without printing the results.
      // We work around this by giving NPM 1000ms to send us duplicate signals.
      // The side effect is that slow shutdown or bug in our runner will force
      // the user to hit Ctrl+C again after at least a second.
      setTimeout(() => process.off('SIGINT', this._sigintHandler), 1000);
      this._hadSignal = true;
      sigintCallback();
    };

    process.on('SIGINT', this._sigintHandler);
  }

  promise() {
    return this._sigintPromise;
  }

  hadSignal() {
    return this._hadSignal;
  }

  disarm() {
    process.off('SIGINT', this._sigintHandler);
  }

}

exports.SigIntWatcher = SigIntWatcher;