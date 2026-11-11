/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.brokers

/**
 * Why a broker JSON was not stored. In every case the previously stored version of the broker is kept.
 */
enum class BrokerJsonUpdateFailureReason(val pixelValue: String) {
    /** The broker JSON itself could not be parsed. */
    INVALID_JSON("invalid_json"),

    /** A step has a step type this app version doesn't know. */
    UNSUPPORTED_STEP("unsupported_step"),

    /** A step contains an action type this app version doesn't know, possibly nested in another action. */
    UNSUPPORTED_ACTION("unsupported_action"),

    /** A step has only known types but doesn't match the expected step or action structure. */
    INVALID_STEP("invalid_step"),

    /** The broker JSON lacks the scan or the opt-out step. */
    MISSING_STEP("missing_step"),

    /** The broker JSON was valid but couldn't be persisted. */
    STORAGE_ERROR("storage_error"),
}
