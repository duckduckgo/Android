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

interface ModalEvaluatorCompletionStore {
    /**
     * Records completion timestamp for any evaluator
     */
    suspend fun recordCompletion()

    /**
     * Checks if evaluator is blocked by 24-hour window due to a modal being shown.
     * @return true if blocked (within 24 hours of last completion)
     */
    suspend fun isBlockedBy24HourWindow(): Boolean

    suspend fun getBackgroundedTimestamp(): Long

    suspend fun hasBackgroundTimestampRecorded(): Boolean

    suspend fun clearBackgroundTimestamp()
}
