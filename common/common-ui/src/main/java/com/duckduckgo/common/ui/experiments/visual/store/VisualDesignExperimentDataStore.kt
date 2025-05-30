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

package com.duckduckgo.common.ui.experiments.visual.store

import kotlinx.coroutines.flow.StateFlow

interface VisualDesignExperimentDataStoreInitializer {
    /**
     * Initializes the [VisualDesignExperimentDataStore].
     *
     * This is a special, idempotent function to be called while the splash screen is visible to pre-warm the store and allow for non-blocking, synchronous access afterwards.
     */
    suspend fun initialize(): VisualDesignExperimentDataStore
}

interface VisualDesignExperimentDataStore {

    /**
     * State flow which returns `true` if the feature flag for the experiment is enabled and there are no conflicting experiments detected.
     */
    val isExperimentEnabled: StateFlow<Boolean>

    /**
     * State flow which returns `true` if the feature flag for the Duck AI PoC is enabled and there are no conflicting experiments detected.
     */
    val isDuckAIPoCEnabled: StateFlow<Boolean>

    /**
     * State flow which returns `true` if there are any conflicting experiments detected.
     */
    val anyConflictingExperimentEnabled: StateFlow<Boolean>

    fun changeExperimentFlagPreference(enabled: Boolean)
    fun changeDuckAIPoCFlagPreference(enabled: Boolean)
}
