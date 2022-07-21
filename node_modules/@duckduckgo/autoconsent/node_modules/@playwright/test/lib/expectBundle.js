"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.printReceived = exports.expect = exports.RECEIVED_COLOR = exports.INVERTED_COLOR = void 0;

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
const expect = require('./expectBundleImpl').expect;

exports.expect = expect;

const INVERTED_COLOR = require('./expectBundleImpl').INVERTED_COLOR;

exports.INVERTED_COLOR = INVERTED_COLOR;

const RECEIVED_COLOR = require('./expectBundleImpl').RECEIVED_COLOR;

exports.RECEIVED_COLOR = RECEIVED_COLOR;

const printReceived = require('./expectBundleImpl').printReceived;

exports.printReceived = printReceived;