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

package com.duckduckgo.modalcoordinator.api

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
     * Evaluates whether this modal should be shown.
     * Called by coordinator only if not blocked by 24-hour window (cooldown period).
     *
     * @return EvaluationResult indicating whether evaluation was completed and modal was shown/triggered or skipped
     */
    suspend fun evaluate(): EvaluationResult

    /**
     * Result of modal evaluation
     */
    sealed class EvaluationResult {

        /** Evaluation completed and modal was shown/triggered */
        object ModalShown : EvaluationResult()

        /** Evaluation was skipped due to internal conditions. No modal was shown/triggered */
        object Skipped : EvaluationResult()
    }
}
