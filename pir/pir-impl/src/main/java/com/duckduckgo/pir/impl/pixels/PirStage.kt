/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.pixels

enum class PirStage(val stageName: String) {
    /**
     *
     * Stage starts: at the beginning of an opt-out job execution, after verifying all dependencies
     */
    START("start"),

    /**
     * Stage starts: before we query the dbp-api v0 for a generated email.
     */
    EMAIL_GENERATE("email-generate"),

    /**
     * Stage starts: when we receive the getCaptchaInfo action
     */
    CAPTCHA_PARSE("captcha-parse"),

    /**
     * Stage starts: after captcha info is parsed and  before it's submitted to the captcha service
     */
    CAPTCHA_SEND("captcha-send"),

    /**
     * Stage starts: once we have received the transaction id and before we start polling the captcha service for solution
     */
    CAPTCHA_SOLVE("captcha-solve"),

    /**
     * Stage starts: when we receive an expectation action
     */
    SUBMIT("submit"),

    /**
     * Stage starts: after halting opt-out jobs at email confirmation step.
     */
    EMAIL_CONFIRM_HALTED("email-confirm-halted"),

    /**
     * Stage starts: when email confirmation job resumes, after verifying all dependencies.
     */
    EMAIL_CONFIRM_DECOUPLED("email-confirm-decoupled"),

    /**
     * Stage starts: when finalizing an opt-out job, after it has been submitted and right before submitting a dbp_optout_process_submit-success pixel.
     */
    VALIDATE("validate"),

    /**
     * Stage starts: when we receive a Click or FillForm action.
     */
    FILL_FORM("fill-form"),

    /**
     * Catch-all stage
     */
    OTHER("other"),
}
