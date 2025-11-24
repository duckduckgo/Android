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
     * Start of an opt-out job execution, after verifying all dependencies
     */
    START("start"),

    /**
     * Stage before querying dbp-api v0 for a generated email.
     */
    EMAIL_GENERATE("email-generate"),

    /**
     * Stage where we start the getCaptchaInfo action
     */
    CAPTCHA_PARSE("captcha-parse"),

    /**
     * Stage after captcha info is parsed and  before it's submitted to the captcha service
     */
    CAPTCHA_SEND("captcha-send"),

    /**
     * Stage during the solveCaptcha action and just before submitting the captcha to be solved
     */
    CAPTCHA_SOLVE("captcha-solve"),

    /**
     * Stage can be set in two places:
     *  (1) at the start of the expectation action
     *  (2) right before executing the emailConfirmation action (prior to email decoupling flow changes)
     */
    SUBMIT("submit"),

    /**
     * Stage after halting opt-out jobs at email confirmation step.
     */
    EMAIL_CONFIRM_HALTED("email-confirm-halted"),

    /**
     * Stage when email confirmation job resumes, after verifying all dependencies.
     */
    EMAIL_CONFIRM_DECOUPLED("email-confirm-decoupled"),

    /**
     * Stage when finalizing an opt-out job, after it has been submitted and right before submitting a dbp_optout_process_submit-success pixel.
     */
    VALIDATE("validate"),

    /**
     * Catch-all stage
     */
    OTHER("other"),

    /**
     * Stage at the start of the click and fillForm actions.
     */
    FILL_FORM("fill-form"),

    /**
     * Stage when the opt-out's condition action meets its expectation.
     */
    CONDITION_FOUND("condition-found"),

    /**
     *
     * Stage when either:
     *  (1) the opt-out's condition action completes with no follow-up actions or
     *  (2) when it doesn't meet its expectation, continuing with regular action execution.
     */
    CONDITION_NOT_FOUND("condition-not-found"),
}
