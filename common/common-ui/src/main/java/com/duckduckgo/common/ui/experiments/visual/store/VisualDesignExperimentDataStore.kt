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

interface VisualDesignExperimentDataStore {

    val experimentState: StateFlow<FeatureState>
    val navigationBarState: StateFlow<FeatureState>

    fun setExperimentStateUserPreference(enabled: Boolean)
    fun setNavigationBarStateUserPreference(enabled: Boolean)

    /**
     * @param isAvailable returns `true` if the flag for this feature is enabled in the config.
     * @param isEnabled returns `true` if both [isAvailable] is `true` and user has the feature enabled in the preferences screen.
     */
    data class FeatureState(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
    )
}
