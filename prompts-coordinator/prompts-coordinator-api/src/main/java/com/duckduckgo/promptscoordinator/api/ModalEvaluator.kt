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

package com.duckduckgo.promptscoordinator.api

/**
 * Interface for modal evaluators that can be coordinated by the ModalEvaluatorCoordinator.
 * Each evaluator should implement this interface and contribute itself via @ContributesMultibinding.
 */
interface ModalEvaluator {
    /**
     * Priority determines evaluation order. Lower numbers are evaluated first.
     * Priority 1 = highest priority (evaluated first)
     * Priority 2+ = lower priorities (evaluated in ascending order)
     */
    val priority: Int

    /**
     * Unique identifier for this evaluator, used for tracking completion timestamps.
     */
    val evaluatorId: String

    /**
     * Which lifecycle event causes the coordinator to evaluate this modal. Evaluators tied to the
     * New Tab Page should override with [ModalTrigger.NTP_RENDER].
     */
    val trigger: ModalTrigger
        get() = ModalTrigger.APP_RESUME

    /**
     * Evaluates whether this modal should be shown. Must be side-effect free with respect to
     * showing: an eligible evaluator returns [EvaluationResult.WantsToShow] and defers the actual
     * showing to the returned action, which the coordinator invokes only once the shared prompt
     * surface has been claimed. Never show a modal directly from this method.
     *
     * @return EvaluationResult indicating whether this evaluator wants to show a modal or skipped
     */
    suspend fun evaluate(): EvaluationResult

    /**
     * Result of modal evaluation
     */
    sealed class EvaluationResult {

        /**
         * Evaluation completed and this evaluator wants to show its modal.
         *
         * @param show shows the modal when invoked; returns true when the modal was actually
         * shown/triggered, false when showing fell through (e.g. no visible host to render into).
         * Invoked at most once, only after the coordinator has secured the prompt surface. State
         * that must only be persisted when the modal really shows (e.g. "prompt consumed" flags)
         * belongs inside this action, not in [evaluate].
         */
        class WantsToShow(val show: suspend () -> Boolean) : EvaluationResult()

        /** Evaluation was skipped due to internal conditions. No modal will be shown/triggered */
        object Skipped : EvaluationResult()
    }
}
